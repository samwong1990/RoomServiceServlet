package hk.samwong.roomservice.servlet.dao;


import hk.samwong.roomservice.commons.dataFormat.AuthenticationDetails;
import hk.samwong.roomservice.commons.dataFormat.Report;
import hk.samwong.roomservice.commons.dataFormat.Response;
import hk.samwong.roomservice.commons.dataFormat.ResponseWithListOfRooms;
import hk.samwong.roomservice.commons.dataFormat.ResponseWithReports;
import hk.samwong.roomservice.commons.dataFormat.TrainingData;
import hk.samwong.roomservice.commons.dataFormat.WifiInformation;
import hk.samwong.roomservice.commons.helper.InstanceFriendlyGson;
import hk.samwong.roomservice.commons.parameterEnums.Classifier;
import hk.samwong.roomservice.commons.parameterEnums.Operation;
import hk.samwong.roomservice.commons.parameterEnums.ParameterKey;
import hk.samwong.roomservice.commons.parameterEnums.ReturnCode;
import hk.samwong.roomservice.servlet.classifier.Oracle;
import hk.samwong.roomservice.servlet.classifier.SimpleOracle;
import hk.samwong.roomservice.servlet.helper.Deserializer;
import hk.samwong.roomservice.servlet.helper.MapUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.javaml.core.Instance;

import org.apache.log4j.Logger;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Servlet that deals with all the fingerprint queries.
 * doDelete: 	for undoing client's reinforcement feedback.
 * doGet:		returns the list of known rooms in the database. TODO. only return rooms around a geolocation
 * doPost:		for uploading training data.
 * doPut		for client's reinforcement feedback. ie "Yes I am indeed in roomX". Also a temporary solution for receiving validation data 
 * @author wongsam
 * 
 */

@WebServlet("/api")
public class RoomServiceServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(RoomServiceServlet.class);
	private FingerprintsDAO fingerprintsDAO;
	private Oracle oracle;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public RoomServiceServlet() {
		super();
		fingerprintsDAO = new SQLiteBackedFingerprintsDAO();
		String dbUrl = "jdbc:sqlite:" + System.getProperty("catalina.base")
				+ "/test.sqlite";
		log.info(dbUrl);
		try {
			fingerprintsDAO.setDataSource(new SingleConnectionDataSource(
					org.sqlite.JDBC.createConnection(dbUrl, new Properties()),
					true));
			oracle = new SimpleOracle(fingerprintsDAO);
		} catch (SQLException e) {
			log.fatal("Failed to create connection to " + dbUrl, e);
			throw new MissingResourceException("JDBC createConnection failed.", fingerprintsDAO.toString(), dbUrl);
		}
	}

	@Override
	protected void doDelete(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received DELETE request with params:" + MapUtils.printParameterMap(parameters));
		String operation = Deserializer.getSingleParameter(parameters,
				ParameterKey.OPERATION);
		PrintWriter out = response.getWriter();
		try {
			/* Return if there is no DELETE parameter */
			if (!operation.equals(Operation.DELETE.toString())) {
				log.warn("Erroneous DELETE request: " + parameters);
				out.print(jsonFeedback(
						ReturnCode.NO_RESPONSE,
						"You didn't explicitly say you want to perform a DELETE. To be on the safe side, this request has been ignored."));
				return;
			}
			try {
				WifiInformation wifiInformation = Deserializer
						.getDejsonifiedWifiInformation(parameters);
				Instance instance = Deserializer.wifiInformationToInstance(
						wifiInformation, fingerprintsDAO);
				instance.setClassValue(parameters.get(ParameterKey.ROOM
						.toString())[0]);
				AuthenticationDetails auenticationDetails = Deserializer
						.getAuenticationDetails(parameters);
				fingerprintsDAO.deleteClassification(auenticationDetails, instance);
				out.print(jsonFeedback(ReturnCode.OK, "DELETE has been completed"));
			} catch (Exception e) {
				log.error("Failed to perform DELETE with param map: "
						+ parameters, e);
				out.print(jsonFeedback(ReturnCode.UNRECOVERABLE_EXCEPTION,
						"Something went wrong while performing DELETE. " + e));
			}
		} finally {
			out.close();
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received GET request with params:" + MapUtils.printParameterMap(parameters));

		PrintWriter out = response.getWriter();
		try {
			String operation = Deserializer.getSingleParameter(parameters,
					ParameterKey.OPERATION);
			AuthenticationDetails auenticationDetails = Deserializer
					.getAuenticationDetails(parameters);
			log.info(auenticationDetails);

			if (operation.equals(Operation.GET_LIST_OF_ROOMS.toString())) {
				List<String> uuidList = fingerprintsDAO
						.getRoomList(auenticationDetails);
				List<String> aliasList = new ArrayList<String>();
				for(String room : uuidList){
					aliasList.add(fingerprintsDAO.getLabelByUUID(room).getAlias());
				}
				ResponseWithListOfRooms result = new ResponseWithListOfRooms().withRoomList(aliasList);
				out.print(new Gson().toJson(result));
				return;
			}
		} catch (Exception e) {
			Response errorResponse = new Response().setReturnCode(
					ReturnCode.ILLEGAL_ARGUMENT).setExplanation(e.toString());
			out.print(new Gson().toJson(errorResponse));
			return;
		} finally {
			out.close();
		}

	}

	/* Shoved upload batch training from PUT to POST because there is a limit on url length.
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received POST request with params:" + MapUtils.printParameterMap(parameters));

		PrintWriter out = response.getWriter();
		try {
			String operation = Deserializer.getSingleParameter(parameters,
					ParameterKey.OPERATION);
			AuthenticationDetails authenticationDetails = Deserializer
					.getAuenticationDetails(parameters);
			log.info(authenticationDetails);

			if (operation.equals(Operation.CLASSIFY.toString())) {
				ResponseWithReports responseWithReports = new ResponseWithReports();

				WifiInformation observation = Deserializer
						.getDejsonifiedWifiInformation(parameters);
				log.info(observation);

				Instance instance = Deserializer.wifiInformationToInstance(
						observation, fingerprintsDAO);
				log.info(instance);

				Map<String, String> specialRequests = Deserializer
						.getSpecialRequestsForClassifier(parameters);
				log.info(specialRequests);

				try {
					// convert String to enum Classifier
					String classifierAsString = parameters
							.get(ParameterKey.CLASSIFIER.toString())[0];
					Classifier classifier = null;
					classifier = Classifier.valueOf(classifierAsString);

					List<Report> result = new ArrayList<Report>();
					result.addAll(oracle.classify(instance, classifier,
							authenticationDetails, specialRequests));
					responseWithReports.withReports(result);

					out.print(new Gson().toJson(responseWithReports));
					return;
				} catch (NoSuchAlgorithmException e) {
					responseWithReports
							.setReturnCode(ReturnCode.NO_SUCH_ALGORITHM);
					responseWithReports.setExplanation(e.toString());
					out.print(new Gson().toJson(responseWithReports));
					return;
				}
			} else if (operation.equals(Operation.UPLOAD_TRAINING_DATA
					.toString())) {
				// Process the batch of training data
				String json = Deserializer.getSingleParameter(parameters,
						ParameterKey.BATCH_TRAINING_DATA);
				TrainingData trainingData = InstanceFriendlyGson.gson.fromJson(
						json, new TypeToken<TrainingData>() {
						}.getType());
				
				String UUID = fingerprintsDAO.insertNewRoom(trainingData.getRoom());
				for (WifiInformation wifiInformation : trainingData.getDatapoints()) {
					fingerprintsDAO.saveInstance(authenticationDetails,
							Deserializer.wifiInformationToInstance(
							wifiInformation, fingerprintsDAO), UUID
							);
				}
				out.print(jsonFeedback(ReturnCode.OK,
						"Training data has been saved"));
				return;
			}
		} catch (Exception e) {
			Response errorResponse = new ResponseWithReports()
			.setReturnCode(ReturnCode.ILLEGAL_ARGUMENT).setExplanation(e.toString());
			out.print(new Gson().toJson(errorResponse));
			return;
		} finally {
			out.close();
		}
	}
	
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doPut(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		// Detect intent, it is either reporting a correct classification or
		// trying to contribute training data
		// Two ways, either by Report, or by WifiInformation
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received PUT request with params:" + MapUtils.printParameterMap(parameters));

		PrintWriter out = response.getWriter();
		try {
			String operation = Deserializer.getSingleParameter(parameters,
					ParameterKey.OPERATION);
			AuthenticationDetails authenticationDetails = Deserializer
					.getAuenticationDetails(parameters);
			if (operation.equals(Operation.CONFIRM_VALID_CLASSIFICATION
					.toString())) {
				// Just a single report, ie client validates the classification
				// is correct.
				// So add this new instance to db.
				String room = Deserializer.getSingleParameter(parameters,
						ParameterKey.ROOM);
				String instanceAsJson = Deserializer.getSingleParameter(
						parameters, ParameterKey.INSTANCE);
				Instance instance = InstanceFriendlyGson.gson.fromJson(
						instanceAsJson, new TypeToken<Instance>() {
						}.getType());
				fingerprintsDAO.saveInstance(authenticationDetails, instance, room);
				out.print(jsonFeedback(ReturnCode.OK, "Instance has been saved"));
				return;
//			} else if(operation.equals(Operation.UPLOAD_STATISTICS.toString())){
//				log.info("Saving statistics");
//				String listOfStatJson = Deserializer.getSingleParameter(parameters,
//						ParameterKey.VALIDATION_STATISTICS);
//				List<RoomStatistic> stats = new Gson().fromJson(listOfStatJson, new TypeToken<List<RoomStatistic>>() {
//						}.getType());
//				((SQLiteBackedFingerprintsDAO) fingerprintsDAO).saveStatistics(stats);
//				out.print(jsonFeedback(ReturnCode.OK, "Saved statistics"));
			} else {
				out.print(jsonFeedback(ReturnCode.NO_RESPONSE,
						"Supported Operations: UPLOAD_TRAINING_DATA & CONFIRM_VALID_CLASSIFICATION."));
				return;
			}
		} catch (IllegalArgumentException e) {
			out.print(jsonFeedback(ReturnCode.ILLEGAL_ARGUMENT, e.getMessage()));
			return;
		} finally {
			out.close();
			log.info("put done, connection closed, results returned");
		}

	}

	private String jsonFeedback(ReturnCode returnCode, String explanation) {
		String json = new Gson().toJson(
				new Response().setExplanation(explanation).setReturnCode(
						returnCode), new TypeToken<Response>() {
				}.getType());
		log.info("json response:" + json);
		return json;
	}

}
