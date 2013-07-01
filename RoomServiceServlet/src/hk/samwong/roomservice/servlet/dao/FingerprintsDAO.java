package hk.samwong.roomservice.servlet.dao;


import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.Label;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import net.sf.javaml.core.Instance;

public abstract class FingerprintsDAO {

	/* Initialize database connection */
	public abstract void setDataSource(DataSource singleConnectionDataSource);
	
	/* Data access for classifiers */
	/**
	 * @param bssid
	 * @return column index of the BSSID.
	 */
	public abstract int getIndexByBSSID(String bssid);
	
	/**
	 * @return Instances that are close to the device
	 */
	public abstract Set<Instance> getAllInstances();
	
	/**
	 * @return Instances that are close to the device
	 */
	public abstract Set<Instance> getInstancesAroundDevice(AuthenticationDetails authenticationDetails);
	
	/**
	 * Returns a list of rooms around the device.
	 * @param query
	 * @return
	 */
	public abstract List<String> getRoomList(AuthenticationDetails authenticationDetails);
	
	/* Modify data in database */
	/**
	 * DB should ensure training data contain a timestamp and source for auditing purpose.
	 * @param query
	 * @param instance
	 * @param room
	 */
	public abstract void saveInstance(AuthenticationDetails authenticationDetails, Instance instance, String uuid);
	
	/**
	 * Can only delete instances that are created by the same user.
	 * @param query
	 * @param wifiInformationToInstance
	 * @throws IllegalAccessException
	 */
	public abstract void deleteClassification(AuthenticationDetails authenticationDetails, Instance instance) throws IllegalAccessException;
	
	public abstract Label getLabelByUUID(String UUID);

	public abstract String insertNewRoom(String label) throws SQLException;
		
}
