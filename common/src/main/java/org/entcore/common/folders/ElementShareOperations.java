package org.entcore.common.folders;

import java.util.List;

import org.entcore.common.user.UserInfos;

public class ElementShareOperations {
	public enum ShareOperationKind {
		USER_SHARE, USER_SHARE_REMOVE, GROUP_SHARE, GROUP_SHARE_REMOVE
	}

	ShareOperationKind kind;
	UserInfos user;
	String userId;
	String groupId;
	List<String> actions;

	public static ElementShareOperations addShareUser(UserInfos user, String userId, List<String> actions) {
		ElementShareOperations share = new ElementShareOperations();
		share.kind = ShareOperationKind.USER_SHARE;
		share.user = user;
		share.userId = userId;
		share.actions = actions;
		return share;
	}

	public static ElementShareOperations removeShareUser(UserInfos user, String userId, List<String> actions) {
		ElementShareOperations share = new ElementShareOperations();
		share.kind = ShareOperationKind.USER_SHARE_REMOVE;
		share.user = user;
		share.userId = userId;
		share.actions = actions;
		return share;
	}

	public static ElementShareOperations addShareGroup(UserInfos user, String groupId, List<String> actions) {
		ElementShareOperations share = new ElementShareOperations();
		share.kind = ShareOperationKind.GROUP_SHARE;
		share.user = user;
		share.groupId = groupId;
		share.actions = actions;
		return share;
	}

	public static ElementShareOperations removeShareGroup(UserInfos user, String groupId, List<String> actions) {
		ElementShareOperations share = new ElementShareOperations();
		share.kind = ShareOperationKind.GROUP_SHARE_REMOVE;
		share.user = user;
		share.groupId = groupId;
		share.actions = actions;
		return share;
	}

	public ShareOperationKind getKind() {
		return kind;
	}

	public void setKind(ShareOperationKind kind) {
		this.kind = kind;
	}

	public UserInfos getUser() {
		return user;
	}

	public void setUser(UserInfos user) {
		this.user = user;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public List<String> getActions() {
		return actions;
	}

	public void setActions(List<String> actions) {
		this.actions = actions;
	}

}
