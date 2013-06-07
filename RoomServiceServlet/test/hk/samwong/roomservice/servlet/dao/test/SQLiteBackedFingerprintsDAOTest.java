package hk.samwong.roomservice.servlet.dao.test;

import static org.junit.Assert.*;
import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.servlet.dao.SQLiteBackedFingerprintsDAO;

import java.sql.SQLException;
import java.util.Collections;
import java.util.MissingResourceException;
import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.security.Credential.MD5;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class SQLiteBackedFingerprintsDAOTest {

	SQLiteBackedFingerprintsDAO fingerprintsDAO;
	String dbPath;

	@Before
	public void setUp() throws Exception {
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.removeAllAppenders();
		rootLogger.setLevel(Level.DEBUG);
		rootLogger.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN)));

		fingerprintsDAO = new SQLiteBackedFingerprintsDAO();
		dbPath = "jdbc:sqlite:/tmp/SQLiteDAOtest" + MD5.digest(System.currentTimeMillis() + "").substring(4) + ".sqlite";
		try {
			fingerprintsDAO.setDataSource(new SingleConnectionDataSource(org.sqlite.JDBC.createConnection(dbPath, new Properties()), true));
		} catch (SQLException e) {
			throw new MissingResourceException("JDBC createConnection failed.", fingerprintsDAO.toString(), dbPath);
		}
	}

	@After
	public void tearDown() throws Exception {

	}

	/*
	 * @Test public void testGetIndexByBSSID() { fail("Not yet implemented"); }
	 * 
	 * @Test public void testGetAllInstances() { fail("Not yet implemented"); }
	 * 
	 * @Test public void testGetInstancesAroundDevice() {
	 * fail("Not yet implemented"); }
	 */
	@Test
	public void testGetRoomList() {
		assertEquals(Collections.emptyList(),
				fingerprintsDAO.getRoomList(new AuthenticationDetails().withLocationAccuracy(12345).withDeviceLatitude(0).withDeviceLongitude(0)));

	}

	/*
	 * @Test public void testSaveInstance() { fail("Not yet implemented"); }
	 * 
	 * @Test public void testDeleteClassification() {
	 * fail("Not yet implemented"); }
	 * 
	 * @Test public void testSaveStatistics() { fail("Not yet implemented"); }
	 * 
	 * @Test public void testSerializedSparseInstance() {
	 * fail("Not yet implemented"); }
	 */
}
