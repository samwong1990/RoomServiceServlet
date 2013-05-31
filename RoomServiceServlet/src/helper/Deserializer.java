package helper;

import java.util.Map;
import java.util.TreeMap;

import net.sf.javaml.core.Instance;
import net.sf.javaml.core.SparseInstance;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.BssidStrength;
import hk.samwong.roomservice.commons.dataFormat.WifiInformation;
import hk.samwong.roomservice.commons.parameterEnums.ParameterKey;

import dao.ClassifierDAO;

/**
 * An helper to deserialize objects from json. Assumes parameter map uses the
 * vocabulary defined in RoomServiceCommons
 * 
 * @author wongsam
 * 
 */
public class Deserializer {
	private static Logger log = Logger.getLogger(Deserializer.class);

	/**
	 * Given a list of WifiInformation, create a sparse instance. This is done
	 * by looking up corresponding index for a given BSSID (MAC address).
	 * 
	 * @param observations
	 * @param classifierDAO
	 * @return
	 */
	public static Instance wifiInformationToInstance(
			WifiInformation observation, ClassifierDAO classifierDAO) {

		// Each column (or index) refers to one BSSID's signal strength.
		// A bit awkward but feature must be of type Double. This is the most
		// straight forward way to do this. Plus, sparse feature is supported!

		// SparseInstance does not have an API for modifying the default value.
		// Looked at the source code, decided to use this hacky way to force it.
		// wifi signal strength is between [-100,0], hence default -100

		Instance instance = new SparseInstance(-1, -100);
		for (BssidStrength bssidStrength : observation.getSignalStrengths()) {
			int index = classifierDAO.getIndexByBSSID(bssidStrength.getBSSID());
			instance.put(index, bssidStrength.getLevel() + 0.0);
		}
		return instance;
	}

	public static WifiInformation getDejsonifiedWifiInformation(
			Map<String, String[]> parameters) {
		// Wrap all observations into objects
		String json = getSingleParameter(parameters, ParameterKey.OBSERVATION);
		log.info("Received json of WifiInformation:" + json);
		return new Gson().fromJson(json, new TypeToken<WifiInformation>() {
		}.getType());
	}

	public static String getSingleParameter(Map<String, String[]> parameters,
			ParameterKey key) {
		String[] values = parameters.get(key.toString());
		if (values == null || values.length == 0) {
			throw new IllegalArgumentException("No value for " + key.toString()
					+ ".");
		} else if (values.length > 1) {
			throw new IllegalArgumentException("Received " + values.length
					+ " values for " + key.toString() + ". Expects only one.");
		}
		return values[0];
	}

	public static AuthenticationDetails getAuenticationDetails(
			Map<String, String[]> parameters) {
		String json = getSingleParameter(parameters,
				ParameterKey.AUENTICATION_DETAILS);
		log.info("Received json of AuenticationDetails:" + json);
		return new Gson().fromJson(json, new TypeToken<AuthenticationDetails>() {
		}.getType());
	}

	public static Map<String, String> getSpecialRequestsForClassifier(
			Map<String, String[]> parameters) {
		String json;
		try{
			json = getSingleParameter(parameters,
					ParameterKey.SPECIAL_REQUESTS_FOR_SPECFIC_CLASSIFIER);
		}catch(IllegalArgumentException e){
			// None specified, return an empty map
			return new TreeMap<String, String>();
		}
		log.info("Received json of AuenticationDetails:" + json);
		return new Gson().fromJson(json, new TypeToken<Map<String,String>>() {
		}.getType());
	}

}
