package hk.samwong.roomservice.forgdrive.commons.enums;

public enum GDriveParameterKey {
	// Client to server:
	OPERATION,		// specifies intent: 
	OBSERVATION, 	// stores the wifi fingerprint
	ROOMS,			// list of room names, of course
	BATCH_TRAINING_DATA,	// self explanatory
	INSTANCE,		// when validating classification, this is the corresponding instance
	AUENTICATION_DETAILS,	// self explanatory
	VALIDATION_STATISTICS,		// For Statistics
	// Server to client:
	RETURN_CODE, 	// See Enum ReturnCode
	ERROR_EXPLANATION, // String with error details
	
	
	
	// save folder
	ACCOUNT_NAME,
	ROOM_NAME,
	FOLDER_NAME,
	ALTERNATE_LINK;
	
}