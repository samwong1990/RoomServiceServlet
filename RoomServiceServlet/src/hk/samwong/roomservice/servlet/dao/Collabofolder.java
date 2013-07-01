package hk.samwong.roomservice.servlet.dao;

import hk.samwong.roomservice.commons.dataFormat.Response;
import hk.samwong.roomservice.commons.parameterEnums.ReturnCode;
import hk.samwong.roomservice.forgdrive.commons.dataFormat.GDriveFolder;
import hk.samwong.roomservice.forgdrive.commons.dataFormat.ResponseWithGDriveFolders;
import hk.samwong.roomservice.forgdrive.commons.enums.GDriveOperation;
import hk.samwong.roomservice.forgdrive.commons.enums.GDriveParameterKey;
import hk.samwong.roomservice.servlet.helper.MapUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Servlet implementation class Collabofolder
 */
@WebServlet("/Collabofolder")
public class Collabofolder extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(Collabofolder.class);
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public Collabofolder() {
		super();
		String dbUrl = "jdbc:sqlite:" + System.getProperty("catalina.base")
				+ "/collabofolder.sqlite";
		log.info(dbUrl);
		try {
			namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(
					new SingleConnectionDataSource(
							org.sqlite.JDBC.createConnection(dbUrl,
									new Properties()), true));
			namedParameterJdbcTemplate
					.getJdbcOperations()
					.execute(
							"CREATE TABLE IF NOT EXISTS rooms (folder TEXT, room TEXT, link TEXT, owner TEXT);");

		} catch (SQLException e) {
			log.fatal("Failed to create connection to " + dbUrl, e);
			throw new MissingResourceException("JDBC createConnection failed.",
					"sqlite", dbUrl);
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received GET request with params:"
				+ MapUtils.printParameterMap(parameters));
		
		PrintWriter out = response.getWriter();
		try {
			String[] ops = parameters.get(GDriveParameterKey.OPERATION
			.toString());
			
			String operation;
			if (ops.length > 0) {
				operation = ops[0];
			} else {
				ResponseWithGDriveFolders badOps = (ResponseWithGDriveFolders) new ResponseWithGDriveFolders()
						.setReturnCode(ReturnCode.ILLEGAL_ARGUMENT)
						.setExplanation("please specify OPERATION");
				out.print(new Gson().toJson(badOps));
				return;
			}

			if (operation.equals(GDriveOperation.GetSurroundingFolders
					.toString())) {
			
				String[] roomLists = parameters.get(GDriveParameterKey.ROOMS
						.toString());
				String roomListJson;
				if (roomLists.length > 0) {
					roomListJson = roomLists[0];
				} else {
					ResponseWithGDriveFolders badOps = (ResponseWithGDriveFolders) new ResponseWithGDriveFolders()
							.setReturnCode(ReturnCode.ILLEGAL_ARGUMENT)
							.setExplanation("please specify ROOMS");
					out.print(new Gson().toJson(badOps));
					return;
				}

				List<String> roomList = new Gson().fromJson(roomListJson,
						new TypeToken<List<String>>() {
						}.getType());

				RowMapper<GDriveFolder> rowMapper = new RowMapper<GDriveFolder>() {
					public GDriveFolder mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new GDriveFolder()
								.withName(rs.getString("folder"))
								.withRoom(rs.getString("room"))
								.withUrl(rs.getString("link"))
								.withOwner(rs.getString("owner"));
					}
				};
 
				List<GDriveFolder> gDriveFolders = this.namedParameterJdbcTemplate
						.getJdbcOperations()
						.query("SELECT folder , room , link , owner FROM rooms;",
								rowMapper);
				// Filter out the irrelevant room. TODO do this in db
				List<GDriveFolder> gDriveFoldersFiltered = new LinkedList<GDriveFolder>();
				for (GDriveFolder drive : gDriveFolders) {
					if (roomList.contains(drive.getRoom())) {
						gDriveFoldersFiltered.add(drive);
					}
				}

				ResponseWithGDriveFolders responseWithGDriveFolders = new ResponseWithGDriveFolders()
						.withGDriveFolders(gDriveFoldersFiltered);
				out.print(new Gson().toJson(responseWithGDriveFolders));
			}
		} catch (Throwable e) {
			Response errorResponse = new Response().setReturnCode( 
					ReturnCode.ILLEGAL_ARGUMENT).setExplanation(e.toString());
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
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Map<String, String[]> parameters = request.getParameterMap();
		log.info("Received PUT request with params:" + MapUtils.printParameterMap(parameters));

		PrintWriter out = response.getWriter();
		try {
			String[] ops = parameters.get(GDriveParameterKey.OPERATION.toString());
			String operation;
			if(ops.length > 0){
				operation = ops[0];
			}else{
				ResponseWithGDriveFolders badOps = (ResponseWithGDriveFolders) new ResponseWithGDriveFolders().setReturnCode(ReturnCode.ILLEGAL_ARGUMENT).setExplanation("please specify OPERATION");
				out.print(new Gson().toJson(badOps));
				return;
			}
			
			if (operation.equals(GDriveOperation.PutFolder.toString())) {
				String insertSql = "INSERT INTO rooms (folder, room, link, owner) VALUES (:folder, :room, :link, :owner);";
				SqlParameterSource namedParameters = new MapSqlParameterSource()
								.addValue("folder",
								parameters.get(GDriveParameterKey.FOLDER_NAME.toString())[0])
								.addValue("room",
								parameters.get(GDriveParameterKey.ROOM_NAME.toString())[0])
								.addValue("link",
								parameters.get(GDriveParameterKey.ALTERNATE_LINK.toString())[0])
								.addValue("owner",
								parameters.get(GDriveParameterKey.ACCOUNT_NAME.toString())[0]);
				this.namedParameterJdbcTemplate.update(insertSql, namedParameters);
				out.print(new Gson().toJson(new Response().setReturnCode(ReturnCode.OK)));
			}
		} catch (IllegalArgumentException e) {
			Response errorResponse = new Response().setReturnCode(
					ReturnCode.ILLEGAL_ARGUMENT).setExplanation(e.toString());
			out.print(new Gson().toJson(errorResponse));
			return;
		} catch (ArrayIndexOutOfBoundsException e){
			Response errorResponse = new Response().setReturnCode(
					ReturnCode.ILLEGAL_ARGUMENT).setExplanation(e.toString());
			out.print(new Gson().toJson(errorResponse));
			return;
		} finally {
			out.close();
			log.info("put done, connection closed, results returned");
		}

	}
}
