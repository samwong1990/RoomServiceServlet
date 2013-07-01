package hk.samwong.roomservice.servlet.dao;

import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.Label;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

public class SQLiteBackedFingerprintsDAO extends FingerprintsDAO {
	private static Logger log = Logger
			.getLogger(SQLiteBackedFingerprintsDAO.class);

	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public void setDataSource(DataSource dataSource) {
		namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
		/*
		 * Old, flat, label structure namedParameterJdbcTemplate
		 * .getJdbcOperations() .execute(
		 * "CREATE TABLE IF NOT EXISTS bssid_index (BSSID TEXT PRIMARY KEY ON CONFLICT IGNORE);"
		 * ); namedParameterJdbcTemplate .getJdbcOperations() .execute(
		 * "CREATE TABLE IF NOT EXISTS instances (timestamp DATETIME DEFAULT (strftime('%Y-%m-%dT%H:%M:%f','now')), room TEXT, latitude REAL, longitude REAL, twoSigmaConfidence REAL, features TEXT, wifimac TEXT, installID TEXT);"
		 * ); namedParameterJdbcTemplate .getJdbcOperations() .execute(
		 * "CREATE TABLE IF NOT EXISTS statistics (room TEXT, algorithm TEXT, hits INTEGER, num_of_trials INTEGER);"
		 * ); namedParameterJdbcTemplate .getJdbcOperations() .execute(
		 * "CREATE TABLE IF NOT EXISTS statistics_details (trial_id INTEGER NOT NULL, hitted_room TEXT, hits INTEGER, FOREIGN KEY(trial_id) REFERENCES statistics_ID(_ROWID_));"
		 * );
		 */

		// Use sqlite's ROWID as UUID.
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS roomgraph (label TEXT, parent TEXT, FOREIGN KEY (parent) REFERENCES roomgraph(ROWID));");
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS children (UUID TEXT, childUUID TEXT, FOREIGN KEY (UUID) REFERENCES roomgraph(ROWID), FOREIGN KEY (childUUID) REFERENCES roomgraph(ROWID));");
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS instances (UUID TEXT, timestamp DATETIME DEFAULT (strftime('%Y-%m-%dT%H:%M:%f','now')),  latitude REAL, longitude REAL, twoSigmaConfidence REAL, features TEXT, wifimac TEXT, installID TEXT, FOREIGN KEY (UUID) REFERENCES roomgraph(ROWID));");
		namedParameterJdbcTemplate
				.getJdbcOperations()
				.execute(
						"CREATE TABLE IF NOT EXISTS bssid_index (BSSID TEXT PRIMARY KEY ON CONFLICT IGNORE);");
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
	public synchronized String insertNewRoom(String label) throws SQLException {
		SqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("label", label);

		RowMapper<String> rowMapper = new RowMapper<String>() {
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString("ROWID");
			}
		};

		List<String> previouslyAdded = this.namedParameterJdbcTemplate
				.query("SELECT ROWID FROM roomgraph WHERE label=:label;",
						queryParameters, rowMapper);

		
		String insertSql = "INSERT INTO roomgraph (label) values (:label);";
		namedParameterJdbcTemplate.update(insertSql, queryParameters);
		
		List<String> updated = this.namedParameterJdbcTemplate
				.query("SELECT ROWID FROM roomgraph WHERE label=:label;",
						queryParameters, rowMapper);

		// TODO deal with the case where two identical labels are added at the same time.
		updated.removeAll(previouslyAdded);
		
		if(updated.size() != 1){
			throw new SQLException("Concurrent write of two rows with identical label");
		}
		return updated.get(0);
	}

	@Override
	public Set<Instance> getAllInstances() {
		RowMapper<Instance> rowMapper = new RowMapper<Instance>() {
			public Instance mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				Instance instance = new SparseInstance(-1, -100);
				for (String feature : rs.getString("features").split(",")) {
					String[] split = StringUtils.split(feature, "=");
					int index = Integer.parseInt(split[0]);
					Double strength = Double.parseDouble(split[1]);
					instance.put(index, strength);
				}
				instance.setClassValue(rs.getString("UUID"));
				return instance;
			}
		};

		List<Instance> instances = this.namedParameterJdbcTemplate
				.getJdbcOperations().query(
						"SELECT UUID, features FROM instances;", rowMapper);
		return Sets.newHashSet(instances);

	}

	@Override
	public Set<Instance> getInstancesAroundDevice(
			AuthenticationDetails authenticationDetails) {
		SqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("latitude", authenticationDetails.getDeviceLatitude())
				.addValue("longitude",
						authenticationDetails.getDeviceLongitude())
				// Uses two sigmas to get 95% confidence.
				.addValue("twoSigmaConfidence",
						authenticationDetails.getLocationAccuracy() * 2);

		RowMapper<Instance> rowMapper = new RowMapper<Instance>() {
			public Instance mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				Instance instance = new SparseInstance(-1, -100);
				for (String feature : rs.getString("features").split(",")) {
					String[] split = StringUtils.split(feature, "=");
					int index = Integer.parseInt(split[0]);
					Double strength = Double.parseDouble(split[1]);
					instance.put(index, strength);
				}
				instance.setClassValue(rs.getString("UUID"));
				return instance;
			}
		};

		List<Instance> instances = this.namedParameterJdbcTemplate
				.query("SELECT UUID, features FROM instances WHERE (latitude - (:latitude)) < (:twoSigmaConfidence) AND (longitude - (:longitude)) < (:twoSigmaConfidence);",
						queryParameters, rowMapper);
		return Sets.newHashSet(instances);
	}

	@Override
	public void deleteClassification(
			AuthenticationDetails authenticationDetails, Instance instance) {
		String deleteSql = "DELETE FROM instances WHERE deviceWifiMacAddress=:deviceWifiMacAddress AND deviceInstallID:=deviceInstallID AND UUID=:UUID AND features=:serializedFeatures;";
		SqlParameterSource namedParameters = new MapSqlParameterSource()
				.addValue("deviceWifiMacAddress",
						authenticationDetails.getDeviceWifiMacAddress())
				.addValue("deviceInstallID",
						authenticationDetails.getDeviceInstallID())
				.addValue("UUID", instance.getClass().toString())
				.addValue("serializedFeatures",
						serializedSparseInstance(instance));
		this.namedParameterJdbcTemplate.update(deleteSql, namedParameters);
	}

	/*
	 * public void saveInstances(TrainingData trainingData,
	 * AuthenticationDetails authenticationDetails) { // TODO
	 * authenticationDetails not used for (WifiInformation wifiInformation :
	 * trainingData.getDatapoints()) {
	 * saveInstance(Deserializer.wifiInformationToInstance( wifiInformation,
	 * this), trainingData.getRoom(), authenticationDetails); } }
	 */

	@Override
	public void saveInstance(AuthenticationDetails authenticationDetails,
			Instance instance, String UUID) {
		String insertSql = "INSERT INTO instances (wifimac, installID, latitude, longitude, twoSigmaConfidence, UUID, features) VALUES (:wifimac, :installID, :lat, :long, :twoSigma, :UUID, :serializedFeatures);";
		SqlParameterSource namedParameters = new MapSqlParameterSource()
				.addValue("wifimac",
						authenticationDetails.getDeviceWifiMacAddress())
				.addValue("installID",
						authenticationDetails.getDeviceInstallID())
				.addValue("lat", authenticationDetails.getDeviceLatitude())
				.addValue("long", authenticationDetails.getDeviceLongitude())
				.addValue("twoSigma",
						authenticationDetails.getLocationAccuracy() * 2)
				.addValue("UUID", UUID)
				.addValue("serializedFeatures",
						serializedSparseInstance(instance));
		this.namedParameterJdbcTemplate.update(insertSql, namedParameters);
	}

	@Override
	public List<String> getRoomList(AuthenticationDetails authenticationDetails) {
		SqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("latitude", authenticationDetails.getDeviceLatitude())
				.addValue("longitude",
						authenticationDetails.getDeviceLongitude())
				// Uses two sigmas to get 95% confidence.
				.addValue("twoSigmaConfidence",
						authenticationDetails.getLocationAccuracy() * 2);

		RowMapper<String> rowMapper = new RowMapper<String>() {
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString("UUID");
			}
		};

		List<String> roomNames = this.namedParameterJdbcTemplate
				.query("SELECT DISTINCT UUID FROM instances WHERE (latitude - (:latitude)) < (:twoSigmaConfidence) AND (longitude - (:longitude)) < (:twoSigmaConfidence);",
						queryParameters, rowMapper);

		return roomNames;
	}

	/*
	 * synchronized to ensure we get the rowid TODO somehow generate an id from
	 * the RoomStatistic to allow concurrent PUT.
	 */
	// public synchronized void saveStatistics(List<RoomStatistic> stats) {
	// for (RoomStatistic stat : stats) {
	// // first save it into a row to get the a id for this trial.
	// String insertSql =
	// "INSERT INTO statistics (UUID, algorithm, hits, num_of_trials) VALUES (:UUID, :algorithm, :hits, :num_of_trials);";
	// SqlParameterSource namedParameters = new MapSqlParameterSource(
	// "UUID", stat.getRoomName())
	// .addValue("algorithm", stat.getAlgorithmName())
	// .addValue("hits", stat.getHits())
	// .addValue("num_of_trials", stat.getNumOfTrials());
	// this.namedParameterJdbcTemplate.update(insertSql, namedParameters);
	//
	// // now get the _ROWID_
	// String selectSql =
	// "SELECT MAX(ROWID) from statistics WHERE UUID=:UUID AND algorithm=:algorithm;";
	// SqlParameterSource parametersForRowid = new MapSqlParameterSource(
	// "UUID", stat.getRoomName()).addValue("algorithm",
	// stat.getAlgorithmName());
	// int rowID = 0;
	// try {
	// rowID = namedParameterJdbcTemplate.queryForInt(selectSql,
	// parametersForRowid);
	// } catch (EmptyResultDataAccessException e) {
	// log.info("Something went wrong, can't find the rowid for the current trial. Abort");
	// return;
	// }
	//
	// // now insert all the details into another table.
	// for (Entry<String, AtomicInteger> entry : stat.getRoomToHitMap()
	// .entrySet()) {
	// String insertDetailsSql =
	// "INSERT INTO statistics_details (trial_id, hitted_room, hits) VALUES (:trial_id, :hitted_room, :hits);";
	// SqlParameterSource detailsParameters = new MapSqlParameterSource(
	// "trial_id", rowID).addValue("hitted_room",
	// entry.getKey()).addValue("hits",
	// entry.getValue().get());
	// this.namedParameterJdbcTemplate.update(insertDetailsSql,
	// detailsParameters);
	// }
	// log.info("Processed one stat");
	// }
	// log.info("All statistics saved.");
	// }

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
	public Label getLabelByUUID(final String UUID) {
		SqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("uuid", UUID);

		RowMapper<Label> rowMapper = new RowMapper<Label>() {
			public Label mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new Label().setUUID(UUID)
						.setAlias(rs.getString("label"))
						.setParent(rs.getString("parent"));
			}
		};

		List<Label> labels = this.namedParameterJdbcTemplate
				.query("SELECT label , parent FROM roomgraph WHERE ROWID=:uuid;",
						queryParameters, rowMapper);
		if (labels.size() != 1) {
			log.error(String.format("Received %d labels by UUID", labels.size()));
			return null;
		}

		// now get its children
		RowMapper<String> childRowMapper = new RowMapper<String>() {
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString("childUUID");
			}
		};

		List<String> children = this.namedParameterJdbcTemplate
				.query(
						"SELECT childUUID FROM children WHERE ROWID=:uuid;",
						queryParameters, childRowMapper);

		return labels.get(0).setChildren(new HashSet<String>(children));
	}
}
