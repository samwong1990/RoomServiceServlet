package classifier;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import net.sf.javaml.core.Instance;

import com.samwong.hk.roomservice.api.commons.dataFormat.AuthenticationDetails;
import com.samwong.hk.roomservice.api.commons.dataFormat.Report;
import com.samwong.hk.roomservice.api.commons.parameterEnums.Classifier;

public interface Oracle {
	List<Report> classify(Instance instance, Classifier classifier, AuthenticationDetails auenticationDetails, Map<String, String> specialRequests) throws NoSuchAlgorithmException;
}
