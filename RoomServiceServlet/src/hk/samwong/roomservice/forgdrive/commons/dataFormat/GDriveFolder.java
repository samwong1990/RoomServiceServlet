package hk.samwong.roomservice.forgdrive.commons.dataFormat;

import hk.samwong.roomservice.forgdrive.commons.enums.RoomServicePoweredFeatures;

import java.util.List;

public class GDriveFolder {
	private boolean starred;
	private String name;
	private String room;
	private int brains;
	private String owner;
	private String url;
	private boolean inviteOnly;
	private List<RoomServicePoweredFeatures> features;

	public boolean isStarred() {
		return starred;
	}

	public GDriveFolder withStarred(boolean starred) {
		this.starred = starred;
		return this;
	}

	public String getName() {
		return name;
	}

	public GDriveFolder withName(String name) {
		this.name = name;
		return this;
	}

	public String getRoom() {
		return room;
	}

	public GDriveFolder withRoom(String room) {
		this.room = room;
		return this;
	}

	public int getBrains() {
		return brains;
	}

	public GDriveFolder withBrains(int brains) {
		this.brains = brains;
		return this;
	}

	public String getOwner() {
		return owner;
	}

	public GDriveFolder withOwner(String owner) {
		this.owner = owner;
		return this;
	}

	public String getUrl() {
		return url;
	}

	public GDriveFolder withUrl(String url) {
		this.url = url;
		return this;
	}

	public boolean isInviteOnly() {
		return inviteOnly;
	}

	public GDriveFolder withInviteOnly(boolean inviteOnly) {
		this.inviteOnly = inviteOnly;
		return this;
	}

	public List<RoomServicePoweredFeatures> getFeatures() {
		return features;
	}

	public GDriveFolder withFeatures(List<RoomServicePoweredFeatures> features) {
		this.features = features;
		return this;
	}

	@Override
	public String toString() {
		return String.format("GDriveFolder [starred=%s, name=%s, room=%s, brains=%s, owner=%s, url=%s, inviteOnly=%s, features=%s]", starred, name,
				room, brains, owner, url, inviteOnly, features);
	}

}
