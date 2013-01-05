package dao;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import mypackage.LDA;
import mypackage.QDA;
import net.sf.javaml.classification.Classifier;
import net.sf.javaml.classification.KNearestNeighbors;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.Instance;
import net.sf.javaml.core.SparseInstance;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.samwong.hk.roomservice.api.commons.dataFormat.BssidStrength;
import com.samwong.hk.roomservice.api.commons.dataFormat.Report;
import com.samwong.hk.roomservice.api.commons.dataFormat.Response;
import com.samwong.hk.roomservice.api.commons.dataFormat.WifiInformation;
import com.samwong.hk.roomservice.api.commons.helper.InstanceFriendlyGson;
import com.samwong.hk.roomservice.api.commons.parameterEnums.Operation;
import com.samwong.hk.roomservice.api.commons.parameterEnums.ParameterKey;
import com.samwong.hk.roomservice.api.commons.parameterEnums.ReturnCode;

import customClassifier.WeightedKNearestNeighbors;

/**
 * Servlet implementation class ClassifierDAOServlet
 */
/**
 * @author wongsam
 * 
 */
@WebServlet("/ClassifierDAOServlet")
public class ClassifierDAOServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(ClassifierDAOServlet.class);
	private ClassifierDAO classifierDAO;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public ClassifierDAOServlet() {
		super();
		classifierDAO = new SQLiteBackedClassifierDAO();
		String dbUrl = "jdbc:sqlite:" + System.getProperty("catalina.base")
				+ "/test.sqlite";
		log.info(dbUrl);
		try {
			classifierDAO.setDataSource(new SingleConnectionDataSource(
					org.sqlite.JDBC.createConnection(dbUrl, new Properties()),
					true));
		} catch (SQLException e) {
			log.fatal("Failed to create connection to " + dbUrl, e);
		}

	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received GET request with params:" + parameters);
		PrintWriter out = response.getWriter();
		List<String> roomList = classifierDAO.getRoomList();
		if(parameters.containsKey(ParameterKey.LIST_OF_ROOMS.toString())){
			out.print(new Gson().toJson(roomList));
		}else{
			out.println("Hello! DB currently contains the following rooms:");
			out.println(StringUtils.join(roomList, "\n"));
		}
		out.close();
		
	}

	@Override
	protected void doDelete(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		log.info("Received DELETE request with params:"
				+ request.getParameterMap());
		super.doDelete(request, response);
		Map<String, String[]> parameters = request.getParameterMap();
		PrintWriter out = response.getWriter();

		if (!parameters.containsKey(Operation.DELETE.toString())) {
			log.warn("Erroneous DELETE request: " + parameters);
			Map<String, Object> result = new HashMap<String, Object>();
			result.put(ParameterKey.RETURN_CODE.toString(), -1);
			result.put(
					ParameterKey.ERROR_EXPLANATION.toString(),
					"You didn't explicitly say you want to perform a DELETE. To be on the safe side, this request has been ignored.");
			out.print(new Gson().toJson(result));
			out.close();
			return;
		}
		try {
			WifiInformation wifiInformation = getDejsonifiedWifiInformation(parameters);
			Instance instance = wifiInformationToInstance(wifiInformation);
			instance.setClassValue(parameters.get(ParameterKey.ROOM.toString())[0]);
			classifierDAO.deleteClassification(instance);
			out.print(new Gson().toJson(Collections.singletonMap(
					ParameterKey.RETURN_CODE.toString(), 0)));
		} catch (Throwable t) {
			log.error("Failed to perform DELETE with param map: " + parameters,
					t);
			Map<String, Object> result = new HashMap<String, Object>();
			result.put(ParameterKey.RETURN_CODE.toString(), -1);
			result.put(ParameterKey.ERROR_EXPLANATION.toString(),
					"Something went wrong while performing DELETE.");
			out.print(new Gson().toJson(result));
		}
		out.close();
	}

	private String returnJson(ReturnCode returnCode, String explanation) {
		String json = new Gson().toJson(new Response().withExplanation(explanation)
				.withReturnCode(returnCode), new TypeToken<Response>() {
		}.getType());
		log.info("json response:" + json);
		return json;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		log.info("Received POST request with params:"
				+ request.getParameterMap());
		// super.doPost(request, response);
		Map<String, String[]> parameters = request.getParameterMap();

		// Detect intent, it is either trying to save instance or is asking for location
		// Two ways, either by Report, or by WifiInformation
		PrintWriter out = response.getWriter();
		try {
			if (parameters.containsKey(Operation.SAVEINSTANCE.toString())) {
				if (parameters.containsKey(ParameterKey.REPORT.toString())) {
					String[] jsons = parameters.get(ParameterKey.REPORT.toString());
					if (jsons.length != 1)
						throw new IllegalArgumentException(
								"size of param REPORT is not 1");
					String json = jsons[0];
					Report report = InstanceFriendlyGson.gson.fromJson(json,
							new TypeToken<Report>() {
					}.getType());
					classifierDAO.saveInstance(report.getInstance(),
							report.getRoom());
					out.print(returnJson(ReturnCode.OK, "Instance has been saved"));
				} else if (parameters.containsKey(ParameterKey.WIFIINFORMATION
						.toString())) {
					String[] jsons = parameters.get(ParameterKey.WIFIINFORMATION
							.toString());
					if (jsons.length != 1)
						throw new IllegalArgumentException(
								"size of param WIFIINFORMATION is not 1");
					String json = jsons[0];
					WifiInformation wifiInformation = InstanceFriendlyGson.gson
							.fromJson(json, new TypeToken<WifiInformation>() {
							}.getType());
					classifierDAO.saveInstance(
							wifiInformationToInstance(wifiInformation),
							wifiInformation.getRoom());
					out.print(returnJson(ReturnCode.OK, "Instance has been saved"));
				} else {
					out.print(returnJson(ReturnCode.ILLEGAL_ARGUMENT,
							"Expects either WifiInformation or Report."));
				}
			}else{

				// Each column (or index) refers to one BSSID's signal strength.
				// A bit awkward but feature must be of type Double. This is the most
				// straight forward way to do this. Plus, sparse feature is supported!

				// SparseInstance does not have an API for modifying the default value.
				// Looked at the source code, decided to use this hacky way to force it.
				// wifi signal strength is between [-100,0], hence default -100
				WifiInformation observation = getDejsonifiedWifiInformation(parameters);
				log.info(observation);
				Instance instance = wifiInformationToInstance(observation);
				log.info(instance);
				// out.println("ASDFASDFASDF");
				// if(true) return;
				// convert String to enum Classifier
				com.samwong.hk.roomservice.api.commons.parameterEnums.Classifier classifier = null;
				String requestedClassifier = parameters.get(ParameterKey.CLASSIFIER
						.toString())[0];
				try {
					classifier = com.samwong.hk.roomservice.api.commons.parameterEnums.Classifier
							.valueOf(requestedClassifier);
				} catch (IllegalArgumentException e) {
					out.print(String.format("Unknown classifier: '%s'",
							requestedClassifier));
					return;
				}

				List<Report> allReports = new ArrayList<Report>();
				// Decide which classifier to use, and return results
				switch (classifier) {
				case ALL:
					allReports.add(getKnnClassification(instance));
					allReports.add(getWknnClassification(instance));
					/*
					if (classifierDAO.getAllInstances().size() > 0) {
						trainLDAandQDA(Train.BOTH);
						allReports.add(getLDAClassification(instance));
						allReports.add(getQDAClassification(instance));
					}
					*/
					out.print(new Gson().toJson(allReports));
					break;
				case KNN:
					allReports.add(getKnnClassification(instance));
					out.print(new Gson().toJson(allReports));
					break;
				case WKNN:
					allReports.add(getWknnClassification(instance));
					out.print(new Gson().toJson(allReports));
					break;
				case LDA:
					trainLDAandQDA(Train.LDA);
					allReports.add(getLDAClassification(instance));
					out.print(new Gson().toJson(allReports));
					break;
				case QDA:
					trainLDAandQDA(Train.QDA);
					allReports.add(getQDAClassification(instance));
					out.print(new Gson().toJson(allReports));
					break;
				default:
					out.print(String.format("Unimplemented classifier '%s'",
							classifier.toString()));
				}
			}
		}finally{
			out.close();
			log.info("POST done, connection closed, results returned");
		}
	}

	private Instance wifiInformationToInstance(WifiInformation observation) {
		Instance instance = new SparseInstance(-1, -100);
		for (BssidStrength bssidStrength : observation.getSignalStrengths()) {
			int index = classifierDAO.getIndexByBSSID(bssidStrength.getBSSID());
			instance.put(index, bssidStrength.getLevel() + 0.0);
		}
		return instance;
	}

	private WifiInformation getDejsonifiedWifiInformation(
			Map<String, String[]> parameters) {
		// Wrap all observations into objects
		String json = parameters.get(ParameterKey.OBSERVATION.toString())[0];
		log.info("Received json of WifiInformation:" + json);
		return new Gson().fromJson(json, new TypeToken<WifiInformation>() {
		}.getType());
	}

	/**
	 * Given a list of WifiInformation, create a sparse instance. This is done
	 * by looking up corresponding index for a given BSSID (MAC address).
	 * 
	 * @param observations
	 * @return
	 */
	private Report getKnnClassification(Instance instance) {
		// import all known instances
		Dataset dataset = new DefaultDataset();
		dataset.addAll(classifierDAO.getAllInstances());
		// train a KNN model
		Classifier knn = new KNearestNeighbors(5);
		knn.buildClassifier(dataset);

		Report report = new Report(instance);
		report.setRoom((String) knn.classify(instance));
		report.setAlgorithm("KNN");
		log.info("knn notes:" + knn.classDistribution(instance));
		StringBuilder sb = new StringBuilder();
		Set<String> otherCandidates = new HashSet<String>();
		for(Entry<Object, Double> room : knn.classDistribution(instance).entrySet()){
			if(room.getValue() > 0){
				if(!StringUtils.equals(room.getKey().toString(),report.getRoom())){
					otherCandidates.add(room.toString());
				}
				sb.append(room.getKey() + ":" + room.getValue());
			}
		}
		report.setOtherCandidates(otherCandidates);
		return report;
	}

	private Report getWknnClassification(Instance instance) {
		// import all known instances
		Dataset dataset = new DefaultDataset();
		dataset.addAll(classifierDAO.getAllInstances());
		// train a KNN model
		Classifier wknn = new WeightedKNearestNeighbors(5);
		wknn.buildClassifier(dataset);

		Report report = new Report(instance);
		report.setAlgorithm("WKNN");
		report.setRoom((String) wknn.classify(instance));
		log.info("wknn notes:" + wknn.classDistribution(instance));
		
		StringBuilder sb = new StringBuilder();
		Set<String> otherCandidates = new HashSet<String>();
		for(Entry<Object, Double> room : wknn.classDistribution(instance).entrySet()){
			if(room.getValue() > 0){
				if(!StringUtils.equals(room.getKey().toString(),report.getRoom())){
					otherCandidates.add(room.toString());
				}
				sb.append(room.getKey() + ":" + room.getValue());
			}
		}
		report.setOtherCandidates(otherCandidates);
		return report;
	}

	private Report getLDAClassification(Instance instance) {
		double[] instanceAsArray = instanceToFeatureArray(instance);
		String room = roomToIntID.inverse().get(
				LDAtrain.predict(instanceAsArray));
		return new Report(instance).withRoom(room).withNotes("LDA:");
	}

	private double[] instanceToFeatureArray(Instance instance) {
		double[] instanceAsArray = new double[maxNumberOfAttributes];
		for (int col = 0; col < maxNumberOfAttributes; col++) {
			instanceAsArray[col] = instance.get(col);
		}
		return instanceAsArray;
	}

	private Report getQDAClassification(Instance instance) {
		double[] instanceAsArray = instanceToFeatureArray(instance);
		String room = roomToIntID.inverse().get(
				QDAtrain.predict(instanceAsArray));
		return new Report(instance).withRoom(room).withNotes("QDA:");
	}

	private LDA LDAtrain;
	private QDA QDAtrain;
	private int maxNumberOfAttributes = 0;
	private BiMap<String, Integer> roomToIntID = HashBiMap.create();
	private int currentClassID = 0;

	private enum Train {
		LDA, QDA, BOTH;
	}

	/**
	 * trainLDAandQDA copied from the mypackage (Work from the other JMC who did
	 * the same project 3 years ago) Included for completeness of the
	 * refactoring effort, math to be audited.
	 */
	private void trainLDAandQDA(Train choice) {
		Set<Instance> data = classifierDAO.getAllInstances();

		// Find the max number of features, and build a room to numeric id map.
		for (Instance instance : data) {
			maxNumberOfAttributes = Math.max(maxNumberOfAttributes,
					instance.noAttributes());
			String key = instance.classValue().toString();
			if (!roomToIntID.containsKey(key)) {
				roomToIntID.put(key, currentClassID++);
			}

		}

		// each row is an instance, each col is a feature
		double[][] dataAsArray = new double[data.size()][maxNumberOfAttributes];
		int[] rowToNumericRoom = new int[data.size()];
		int row = 0;
		for (Instance instance : data) {
			for (int col = 0; col < maxNumberOfAttributes; col++) {
				dataAsArray[row][col] = instance.get(col);
			}
			String className = instance.classValue()
					.toString();
			rowToNumericRoom[row] = roomToIntID.get(className);
			row++;
		}
		switch (choice) {
		case LDA:
			LDAtrain = new LDA(dataAsArray, rowToNumericRoom, true);
			return;
		case QDA:
			QDAtrain = new QDA(dataAsArray, rowToNumericRoom, true);
			return;
		case BOTH:
			LDAtrain = new LDA(dataAsArray, rowToNumericRoom, true);
			QDAtrain = new QDA(dataAsArray, rowToNumericRoom, true);
			return;
		}

	}
}
