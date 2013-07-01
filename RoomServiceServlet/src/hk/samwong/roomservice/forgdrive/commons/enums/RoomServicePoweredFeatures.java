package hk.samwong.roomservice.forgdrive.commons.enums;

public enum RoomServicePoweredFeatures {
	MUST_BE_PRESENT_TO_JOIN, KICK_ABSENTEES, AUTO_INVITE_ROOMMATES;

	private int intParameter = 0;

	public int getInt() {
		return intParameter;
	}

	public void setInt(int n) {
		intParameter = n;
	}
}
