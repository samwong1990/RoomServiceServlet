package hk.samwong.roomservice.servlet.customClassifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.javaml.classification.KNearestNeighbors;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.Instance;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import hk.samwong.roomservice.commons.dataFormat.Label;
import hk.samwong.roomservice.commons.dataFormat.Report;
import hk.samwong.roomservice.servlet.dao.FingerprintsDAO;


public class KNN implements CustomClassifier {
	private static Logger log = Logger.getLogger(KNN.class);

	private FingerprintsDAO fingerprintsDAO;

	public KNN(FingerprintsDAO fingerprintsDAO){
		this.fingerprintsDAO = fingerprintsDAO;
	}

	@Override
	public Report getClassification(Instance instance, Map<String,String> specialRequest) {
		// import all known instances
		Dataset dataset = new DefaultDataset();
		dataset.addAll(fingerprintsDAO.getAllInstances());
		// train a KNN model
		net.sf.javaml.classification.Classifier knn = new KNearestNeighbors(5);
		knn.buildClassifier(dataset);

		String bestMatchUUID = (String) knn.classify(instance);
		Label bestMatchLabel = fingerprintsDAO.getLabelByUUID(bestMatchUUID);
		log.info("knn notes:" + knn.classDistribution(instance));
		
		StringBuilder sb = new StringBuilder();
		Set<Label> otherCandidates = new HashSet<Label>();
		for (Entry<Object, Double> uuidEntry : knn.classDistribution(instance)
				.entrySet()) {
			if (uuidEntry.getValue() > 0) {
				if (!StringUtils.equals(uuidEntry.getKey().toString(),
						bestMatchUUID)) {
					Label nextMatch = fingerprintsDAO.getLabelByUUID(uuidEntry.getKey().toString());
					otherCandidates.add(nextMatch);
					sb.append(nextMatch.getAlias() + ":" + String.format("%5f",uuidEntry.getValue()));
				}else{
					sb.append(bestMatchLabel.getAlias() + ":" + String.format("%5f",uuidEntry.getValue()));
				}
				sb.append("|");
			}
		}
		Report report = new Report(instance).setBestMatch(bestMatchLabel).setAlgorithm("KNN").setOtherCandidates(otherCandidates).setNotes(sb.toString());
		return report;
	}

}
