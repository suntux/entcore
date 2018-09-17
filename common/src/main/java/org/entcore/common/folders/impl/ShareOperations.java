package org.entcore.common.folders.impl;

import java.util.List;

import org.entcore.common.user.UserInfos;

public class ShareOperations {
	enum ShareOperationKind {
		USER_SHARE, USER_SHARE_REMOVE, GROUP_SHARE, GROUP_SHARE_REMOVE
	}

	ShareOperationKind kind;
	UserInfos user;
	String userId;
	String groupId;
	List<String> actions;

	public static ShareOperations addShareUser(UserInfos user, String userId, List<String> actions) {
		ShareOperations share = new ShareOperations();
		share.kind = ShareOperationKind.USER_SHARE;
		share.user = user;
		share.userId = userId;
		share.actions = actions;
		return share;
	}

	public static ShareOperations removeShareUser(UserInfos user, String userId, List<String> actions) {
		ShareOperations share = new ShareOperations();
		share.kind = ShareOperationKind.USER_SHARE_REMOVE;
		share.user = user;
		share.userId = userId;
		share.actions = actions;
		return share;
	}

	public static ShareOperations addShareGroup(UserInfos user, String groupId, List<String> actions) {
		ShareOperations share = new ShareOperations();
		share.kind = ShareOperationKind.GROUP_SHARE;
		share.user = user;
		share.groupId = groupId;
		share.actions = actions;
		return share;
	}

	public static ShareOperations removeShareGroup(UserInfos user, String groupId, List<String> actions) {
		ShareOperations share = new ShareOperations();
		share.kind = ShareOperationKind.GROUP_SHARE_REMOVE;
		share.user = user;
		share.groupId = groupId;
		share.actions = actions;
		return share;
	}
}
