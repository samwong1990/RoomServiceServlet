package dao;

import helper.Deserializer;

import java.sql.ResultSet;
import java.sql.SQLException;
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
import com.samwong.hk.roomservice.api.commons.dataFormat.AuthenticationDetails;
import com.samwong.hk.roomservice.api.commons.dataFormat.TrainingData;
import com.samwong.hk.roomservice.api.commons.dataFormat.WifiInformation;

public class SQLiteBackedClassifierDAO implements ClassifierDAO {
	private static Logger log = Logger
			.getLogger(SQLiteBackedClassifierDAO.class);

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
	public void saveInstances(TrainingData trainingData, AuthenticationDetails authenticationDetails) {
		// TODO authenticationDetails not used
		for (WifiInformation wifiInformation : trainingData.getDatapoints()) {
			saveInstance(Deserializer.wifiInformationToInstance(
					wifiInformation, this), trainingData.getRoom(), authenticationDetails);
		}
	}

	@Override
	public void saveInstance(Instance instance, String room, AuthenticationDetails authenticationDetails) {
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

}
