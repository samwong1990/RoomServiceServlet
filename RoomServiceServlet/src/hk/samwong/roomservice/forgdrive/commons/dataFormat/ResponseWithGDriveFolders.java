package hk.samwong.roomservice.forgdrive.commons.dataFormat;

import java.util.List;

import hk.samwong.roomservice.commons.dataFormat.Response;

public class ResponseWithGDriveFolders extends Response {
	private List<GDriveFolder> gDriveFolders;

	public List<GDriveFolder> getGDriveFolders() {
		return gDriveFolders;
	}

	public ResponseWithGDriveFolders withGDriveFolders(List<GDriveFolder> gDriveFolders) {
		this.gDriveFolders = gDriveFolders;
		return this;
	}

	@Override
	public String toString() {
		return String.format("ResponseWithGDriveFolder [gDriveFolders=%s]", gDriveFolders);
	}
}
