package classifier;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import net.sf.javaml.core.Instance;

import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.Report;
import hk.samwong.roomservice.commons.parameterEnums.Classifier;

public interface Oracle {
	List<Report> classify(Instance instance, Classifier classifier, AuthenticationDetails auenticationDetails, Map<String, String> specialRequests) throws NoSuchAlgorithmException;
}
