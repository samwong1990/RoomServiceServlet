package hk.samwong.roomservice.servlet.customClassifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.Instance;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;


import hk.samwong.roomservice.commons.dataFormat.Report;
import hk.samwong.roomservice.servlet.classifierImplementation.WeightedKNearestNeighbors;
import hk.samwong.roomservice.servlet.dao.FingerprintsDAO;


public class WKNN implements CustomClassifier {
	private static Logger log = Logger.getLogger(WKNN.class);

	private FingerprintsDAO fingerprintsDAO;
	
	public WKNN(FingerprintsDAO fingerprintsDAO){
		this.fingerprintsDAO = fingerprintsDAO;
	}
	
	public Report getClassification(Instance instance, Map<String,String> specialRequest) {
		// import all known instances
		Dataset dataset = new DefaultDataset();
		dataset.addAll(fingerprintsDAO.getAllInstances());
		// train a KNN model
		net.sf.javaml.classification.Classifier wknn = new WeightedKNearestNeighbors(
				5);
		wknn.buildClassifier(dataset);
		Report report = new Report(instance).withAlgorithm("WKNN");
		report.setRoom((String) wknn.classify(instance));
		Map<Object, Double> classDistribution = wknn.classDistribution(instance);
		log.info("wknn notes:" + classDistribution);
		
		// go through the list to see if there is any other suggestion
		// sb is building a custom message to give a report summary
		
		Set<String> otherCandidates = new HashSet<String>();
		StringBuilder sb = new StringBuilder();
		for (Entry<Object, Double> room : classDistribution
				.entrySet()) {
			if (room.getValue() > 0) {
				if (!StringUtils.equals(room.getKey().toString(),
						report.getRoom())) {
					otherCandidates.add(room.toString());
				}
				sb.append(room.getKey() + ":" + room.getValue());
			}
		}
		report.setOtherCandidates(otherCandidates);
		return report;
	}
}
