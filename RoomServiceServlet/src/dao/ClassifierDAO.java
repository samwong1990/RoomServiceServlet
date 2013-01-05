package dao;


import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import net.sf.javaml.core.Instance;

public interface ClassifierDAO {

	void setDataSource(DataSource singleConnectionDataSource);
	
	int getIndexByBSSID(String bssid);
	
	Set<Instance> getAllInstances();
	
	void saveInstance(Instance instance, String roomArray);
	
	void deleteClassification(Instance wifiInformationToInstance);
	
	public String serializedSparseInstance(Instance instance);

	List<String> getRoomList();
}
