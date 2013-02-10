package customClassifier;

import java.util.Map;

import com.samwong.hk.roomservice.api.commons.dataFormat.Report;

import net.sf.javaml.core.Instance;

public interface CustomClassifier {
	Report getClassification(Instance instance, Map<String, String> specialRequests);
}
