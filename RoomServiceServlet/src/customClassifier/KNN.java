package customClassifier;

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

import hk.samwong.roomservice.commons.dataFormat.Report;

import dao.ClassifierDAO;

public class KNN implements CustomClassifier {
	private static Logger log = Logger.getLogger(KNN.class);

	private ClassifierDAO classifierDAO;

	public KNN(ClassifierDAO classifierDAO){
		this.classifierDAO = classifierDAO;
	}

	@Override
	public Report getClassification(Instance instance, Map<String,String> specialRequest) {
		// import all known instances
		Dataset dataset = new DefaultDataset();
		dataset.addAll(classifierDAO.getAllInstances());
		// train a KNN model
		net.sf.javaml.classification.Classifier knn = new KNearestNeighbors(5);
		knn.buildClassifier(dataset);

		Report report = new Report(instance);
		report.setRoom((String) knn.classify(instance));
		report.setAlgorithm("KNN");
		log.info("knn notes:" + knn.classDistribution(instance));
		StringBuilder sb = new StringBuilder();
		Set<String> otherCandidates = new HashSet<String>();
		for (Entry<Object, Double> room : knn.classDistribution(instance)
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
