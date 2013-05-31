package classifier;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.sf.javaml.core.Instance;

import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.Report;
import hk.samwong.roomservice.commons.parameterEnums.Classifier;

import customClassifier.CustomClassifier;
import customClassifier.KNN;
import customClassifier.WKNN;
import dao.FingerprintsDAO;

public class SimpleOracle implements Oracle {

	private FingerprintsDAO fingerprintsDAO;
	private Map<Classifier, CustomClassifier> classifierMap;
	
	public SimpleOracle(FingerprintsDAO server) {
		this.fingerprintsDAO = server;
		classifierMap = new HashMap<Classifier, CustomClassifier>();
		classifierMap.put(Classifier.WKNN, new WKNN(fingerprintsDAO));
		classifierMap.put(Classifier.KNN, new KNN(fingerprintsDAO));
	}

	@Override
	public List<Report> classify(Instance instance, Classifier classifier, AuthenticationDetails aueAuthenticationDetails, Map<String, String> specialRequests) throws NoSuchAlgorithmException {
		List<Report> reports = new LinkedList<Report>();
		if(classifier.equals(Classifier.ALL)){
			for(CustomClassifier algorithm : classifierMap.values()){
				reports.add(algorithm.getClassification(instance, null));
			}
		}else{
			CustomClassifier customClassifier = classifierMap.get(classifier);
			if(customClassifier == null){
				throw new NoSuchAlgorithmException("Can't find classifier: " + classifier.toString());
			}
			reports.add(customClassifier.getClassification(instance, specialRequests));
		}
		return reports;
	}
}
