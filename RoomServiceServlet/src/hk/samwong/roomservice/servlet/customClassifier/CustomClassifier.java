package hk.samwong.roomservice.servlet.customClassifier;

import java.util.Map;

import hk.samwong.roomservice.commons.dataFormat.Report;

import net.sf.javaml.core.Instance;

public interface CustomClassifier {
	Report getClassification(Instance instance, Map<String, String> specialRequests);
}
