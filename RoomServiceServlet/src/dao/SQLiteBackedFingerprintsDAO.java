package dao;

import helper.Deserializer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import net.sf.javaml.core.Instance;
import net.sf.javaml.core.SparseInstance;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import com.google.common.collect.Sets;
import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.RoomStatistic;
import hk.samwong.roomservice.commons.dataFormat.TrainingData;
import hk.samwong.roomservice.commons.dataFormat.WifiInformation;

public class SQLiteBackedFingerprintsDAO implements FingerprintsDAO {
	private static Logger log = Logger
			.getLogger(SQLiteBackedFingerprintsDAO.class);

	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public void setDataSource(DataSource dataSource) {
		namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS bssid_index (BSSID TEXT PRIMARY KEY ON CONFLICT IGNORE);");
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS instances (room TEXT, features TEXT);");
		
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS statistics (room TEXT, algorithm TEXT, hits INTEGER, num_of_trials INTEGER);");
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS statistics_details (trial_id INTEGER NOT NULL, hitted_room TEXT, hits INTEGER, FOREIGN KEY(trial_id) REFERENCES statistics_ID(_ROWID_));");
		
	}

	@Override
	public int getIndexByBSSID(String bssid) {
		// Subtract 1 from index because ROWID starts from 1.
		String selectSql = "SELECT ROWID-1 from bssid_index WHERE BSSID=:bssid;";
		SqlParameterSource namedParameters = new MapSqlParameterSource("bssid",
				bssid);
		try {
			return namedParameterJdbcTemplate.queryForInt(selectSql,
					namedParameters);
		} catch (EmptyResultDataAccessException e) {
			log.info("Unseen bssid: " + bssid);
			String insertSql = "INSERT INTO bssid_index (BSSID) values (:bssid);";
			SqlParameterSource insertSqlParameterSource = new MapSqlParameterSource(
					"bssid", bssid);
			namedParameterJdbcTemplate.update(insertSql,
					insertSqlParameterSource);
			return namedParameterJdbcTemplate.queryForInt(selectSql,
					namedParameters);
		}
	}

	@Override
	public Set<Instance> getAllInstances() {
		List<Instance> instances = this.namedParameterJdbcTemplate
				.getJdbcOperations().query(
						"SELECT room, features FROM instances;",
						new RowMapper<Instance>() {
							public Instance mapRow(ResultSet rs, int rowNum)
									throws SQLException {
								Instance instance = new SparseInstance(-1, -100);
								for (String feature : rs.getString("features")
										.split(",")) {
									String[] split = StringUtils.split(feature,
											"=");
									int index = Integer.parseInt(split[0]);
									Double strength = Double
											.parseDouble(split[1]);
									instance.put(index, strength);
								}
								instance.setClassValue(rs.getString("room"));
								return instance;
							}
						});
		return Sets.newHashSet(instances);
	}

	public void deleteClassification(Instance instance,
			AuthenticationDetails auenticationDetails) {
		// TODO authenticationDetails not used
		String deleteSql = "DELETE FROM instances WHERE deviceWifiMacAddress=:deviceWifiMacAddress AND deviceInstallID:=deviceInstallID AND room=:room AND features=:serializedFeatures;";
		SqlParameterSource namedParameters = new MapSqlParameterSource()
				.addValue("deviceWifiMacAddress",
						auenticationDetails.getDeviceWifiMacAddress())
				.addValue("deviceInstallID",
						auenticationDetails.getDeviceInstallID())
				.addValue("room", instance.getClass().toString())
				.addValue("serializedFeatures",
						serializedSparseInstance(instance));
		this.namedParameterJdbcTemplate.update(deleteSql, namedParameters);
	}

	@Override
	public void saveInstances(TrainingData trainingData,
			AuthenticationDetails authenticationDetails) {
		// TODO authenticationDetails not used
		for (WifiInformation wifiInformation : trainingData.getDatapoints()) {
			saveInstance(Deserializer.wifiInformationToInstance(
					wifiInformation, this), trainingData.getRoom(),
					authenticationDetails);
		}
	}

	@Override
	public void saveInstance(Instance instance, String room,
			AuthenticationDetails authenticationDetails) {
		// TODO authenticationDetails not used
		String insertSql = "INSERT INTO instances (room, features) VALUES (:room, :serializedFeatures);";
		SqlParameterSource namedParameters = new MapSqlParameterSource("room",
				room).addValue("serializedFeatures",
				serializedSparseInstance(instance));
		this.namedParameterJdbcTemplate.update(insertSql, namedParameters);
	}

	public String serializedSparseInstance(Instance instance) {
		StringBuilder sb = new StringBuilder();
		for (int key : instance.keySet()) {
			sb.append(key);
			sb.append("=");
			sb.append(instance.get(key));
			sb.append(",");
		}
		return sb.toString();
	}

	@Override
	public List<String> getRoomList(AuthenticationDetails authenticationDetails) {
		// TODO authenticationDetails not used
		List<String> roomList = this.namedParameterJdbcTemplate
				.getJdbcOperations().query(
						"SELECT DISTINCT room FROM instances;",
						new RowMapper<String>() {
							public String mapRow(ResultSet rs, int rowNum)
									throws SQLException {
								return rs.getString("room");
							}
						});
		return roomList;
	}

	/*
	 * synchronized to ensure we get the rowid TODO somehow generate an id from
	 * the RoomStatistic to allow concurrent PUT.
	 */
	@Override
	public synchronized void saveStatistics(List<RoomStatistic> stats) {
		
		// TODO authenticationDetails not used
		for (RoomStatistic stat : stats) {
			// first save it into a row to get the a id for this trial.
			String insertSql = "INSERT INTO statistics (room, algorithm, hits, num_of_trials) VALUES (:room, :algorithm, :hits, :num_of_trials);";
			SqlParameterSource namedParameters = new MapSqlParameterSource(
					"room", stat.getRoomName())
					.addValue("algorithm", stat.getAlgorithmName())
					.addValue("hits", stat.getHits())
					.addValue("num_of_trials", stat.getNumOfTrials());
			this.namedParameterJdbcTemplate.update(insertSql, namedParameters);

			// now get the _ROWID_
			String selectSql = "SELECT MAX(ROWID) from statistics WHERE room=:room AND algorithm=:algorithm;";
			SqlParameterSource parametersForRowid = new MapSqlParameterSource(
					"room", stat.getRoomName()).addValue("algorithm", stat.getAlgorithmName());
			int rowID = 0;
			try {
				rowID = namedParameterJdbcTemplate.queryForInt(selectSql,
						parametersForRowid);
			} catch (EmptyResultDataAccessException e) {
				log.info("Something went wrong, can't find the rowid for the current trial. Abort");
				return;
			}

			// now insert all the details into another table.
			for (Entry<String, AtomicInteger> entry : stat.getRoomToHitMap()
					.entrySet()) {
				String insertDetailsSql = "INSERT INTO statistics_details (trial_id, hitted_room, hits) VALUES (:trial_id, :hitted_room, :hits);";
				SqlParameterSource detailsParameters = new MapSqlParameterSource(
						"trial_id", rowID).addValue("hitted_room",
						entry.getKey()).addValue("hits",
						entry.getValue().get());
				this.namedParameterJdbcTemplate.update(insertDetailsSql,
						detailsParameters);
			}
			log.info("Processed one stat");
		}
		log.info("All statistics saved.");
	}
}
