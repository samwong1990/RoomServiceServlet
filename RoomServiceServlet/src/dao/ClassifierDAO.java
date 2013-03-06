package dao;


import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import com.samwong.hk.roomservice.api.commons.dataFormat.AuthenticationDetails;
import com.samwong.hk.roomservice.api.commons.dataFormat.RoomStatistic;
import com.samwong.hk.roomservice.api.commons.dataFormat.TrainingData;

import net.sf.javaml.core.Instance;

public interface ClassifierDAO {

	void setDataSource(DataSource singleConnectionDataSource);
	
	int getIndexByBSSID(String bssid);
	
	Set<Instance> getAllInstances();
	
	void saveInstance(Instance instance, String room, AuthenticationDetails authenticationDetails);
	
	void deleteClassification(Instance wifiInformationToInstance, AuthenticationDetails auenticationDetails);
	
	public String serializedSparseInstance(Instance instance);

	List<String> getRoomList(AuthenticationDetails auenticationDetails);

	void saveInstances(TrainingData trainingData, AuthenticationDetails authenticationDetails);

	void saveStatistics(List<RoomStatistic> stats);
}
