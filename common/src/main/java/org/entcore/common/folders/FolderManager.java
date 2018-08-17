package org.entcore.common.folders;

import org.entcore.common.user.UserInfos;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface FolderManager {
	static final int FOLDER_TYPE = 0;
	static final int FILE_TYPE = 1;

	/**
	 * Create a folder as root folder
	 * 
	 * @param folder  informations
	 * @param user    owner of the folder
	 * @param handler emit the folder informations with the DB id created
	 */
	void createFolder(JsonObject folder, UserInfos user, final Handler<AsyncResult<JsonObject>> handler);

	/**
	 * Create a folder under destination folder
	 * 
	 * @param destinationFolderId
	 * @param user                owner of the folder
	 * @param folder              informations
	 * @param handler             emit the folder informations with the DB id
	 *                            created
	 */
	void createFolder(String destinationFolderId, UserInfos user, JsonObject folder,
			final Handler<AsyncResult<JsonObject>> handler);

	/**
	 * get infos related to a folder or a file
	 * 
	 * @param id      of the file or the folder
	 * @param user    reading infos
	 * @param handler emit info related to the folder or the file
	 */
	void info(String id, UserInfos user, final Handler<AsyncResult<JsonObject>> handler);

	/**
	 * list children belonging to a folder (files or folders)
	 * 
	 * @param idFolder
	 * @param handler  emit the list of files/folders belonging to the folder
	 */
	void list(String idFolder, final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * 
	 * List all folders that belongs to or are shared with the user. Starting from
	 * idFolder
	 * 
	 * @param idFolder id of the folder from which we search
	 * @param user
	 * @param handler  emit the list of files/folders under the root folder
	 *                 (recursively)
	 */
	void listFoldersRecursivelyFromFolder(String idFolder, UserInfos user,
			final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * List all folders that belongs to or are shared with the user
	 * 
	 * @param user
	 * @param handler emit the list of files/folders under the root folder
	 *                (recursively)
	 */
	void listFoldersRecursively(UserInfos user, final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * 
	 * @param id      of the root folder or of the file
	 * @param user    the user to check wich files he is able to see
	 * @param request
	 */
	void downloadFile(String id, UserInfos user, HttpServerRequest request);

	/**
	 * 
	 * @param id      of the file or the folder
	 * @param user    sharing the file or the folder
	 * @param handler emit success if inheritedShared has been computed successfully
	 */
	void updateShared(String id, UserInfos user, final Handler<AsyncResult<Void>> handler);

	/**
	 * 
	 * @param id      of the file or the folder
	 * @param newName the new name of the file or folder
	 * @param handler emit an error if rename failed
	 */
	void rename(String id, String newName, final Handler<AsyncResult<Void>> handler);

	/**
	 * 
	 * @param sourceId            of the file or the folder
	 * @param destinationFolderId
	 * @param user                moving the folder
	 * @param handler             emit moved file an error if destination does not
	 *                            exists or an error occurs
	 */
	void move(String sourceId, String destinationFolderId, UserInfos user,
			final Handler<AsyncResult<JsonObject>> handler);

	/**
	 * 
	 * @param sourceId            of the file or the folder
	 * @param destinationFolderId
	 * @param user                the user doing the copy
	 * @param handler             emit the folder info created or an error if the
	 *                            destination or the source does not exists or an
	 *                            error occurs
	 */
	void copy(String sourceId, String destinationFolderId, UserInfos user,
			final Handler<AsyncResult<JsonObject>> handler);

	/**
	 * trash only make the file or the folder not visible but it is still saved in
	 * hard disk. If you want to delete it definitely use delete
	 * 
	 * @param id      of the file or directory. In case of directory, the method
	 *                trash all files and folders recursively
	 * @param user    the user trashing the folder or the file
	 * @param handler emit a list of the files/folders ID trashed
	 */
	void trash(String id, UserInfos user, final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * restore a trashed file or folder
	 * 
	 * @param id      of the file or directory. In case of directory, the method
	 *                restore all files and folders recursively
	 * @param user    the user restoring the folder or the file
	 * @param handler emit a list of the files/folders ID restored
	 */
	void restore(String id, UserInfos user, final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * delete a file or a folder definitly
	 * 
	 * @param id      of the file or directory. In case of directory, the method
	 *                delete all files and folders recursively
	 * @param user    the user deleting the folder or the file
	 * @param handler emit a list of the files/folders ID deleted
	 */
	void delete(String id, UserInfos user, final Handler<AsyncResult<JsonArray>> handler);
}
