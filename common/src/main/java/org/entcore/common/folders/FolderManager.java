package org.entcore.common.folders;

import java.util.Collection;
import java.util.Optional;

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
	 * 
	 * @param query   multi criteria query
	 * @param user    user executing the query
	 * @param handler emitting all files matching the query
	 */
	void findByQuery(ElementQuery query, UserInfos user, Handler<AsyncResult<JsonArray>> handler);

	/**
	 * 
	 * @param parentId the folder containing the file
	 * @param doc      the file object
	 * @param user     the user saving the file
	 * @param handler  the handler that emit the file saved
	 */
	void addFile(Optional<String> parentId, JsonObject doc, UserInfos user, Handler<AsyncResult<JsonObject>> handler);

	/**
	 * 
	 * @param id       of the file to update
	 * @param parentId folder containing the file
	 * @param doc      the content of the file object
	 * @param user     the user updating the file
	 * @param handler  the handler that emit the file object save or an error if any
	 */
	void updateFile(String id, Optional<String> parentId, JsonObject doc, UserInfos user,
			Handler<AsyncResult<JsonObject>> handler);

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
	 * @param user
	 * @param handler  emit the list of files/folders belonging to the folder
	 */
	void list(String idFolder, UserInfos user, final Handler<AsyncResult<JsonArray>> handler);

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
	 * @param ids     id's list of files and folders
	 * @param user    the user to check wich files he is able to see
	 * @param request
	 */
	void downloadFiles(Collection<String> ids, UserInfos user, HttpServerRequest request);

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
	 * @param user    user renaming file
	 * @param handler emit an error if rename failed
	 */
	void rename(String id, String newName, UserInfos user, final Handler<AsyncResult<Void>> handler);

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
	 * @param sourceId            collection of id's file or the folder
	 * @param destinationFolderId
	 * @param user                moving the folder
	 * @param handler             emit moved files an error if destination does not
	 *                            exists or an error occurs
	 */
	void moveAll(Collection<String> sourceId, String destinationFolderId, UserInfos user,
			final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * 
	 * @param sourceId            of the file or the folder
	 * @param destinationFolderId the id of the destination folder or empty if the
	 *                            destination is root
	 * @param user                the user doing the copy
	 * @param handler             emit the list of copied files/folders or an error
	 *                            if the destination or the source does not exists
	 *                            or an error occurs
	 */
	void copy(String sourceId, Optional<String> destinationFolderId, UserInfos user,
			final Handler<AsyncResult<JsonArray>> handler);

	/**
	 * 
	 * @param sourceIds           collection of id's file or the folder
	 * @param destinationFolderId the id of the destination folder or empty if the
	 *                            destination is root
	 * @param user                the user doing the copy
	 * @param handler             emit the list of copied files/folders or an error
	 *                            if the destination or the source does not exists
	 *                            or an error occurs
	 */
	void copyAll(Collection<String> sourceIds, Optional<String> destinationFolderId, UserInfos user,
			final Handler<AsyncResult<JsonArray>> handler);

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

	/**
	 * 
	 * @param id              of the file or the folder to share
	 * @param shareOperations defining what kind of share operation to do
	 * @param h               handler that emit the list of userid who can see files or folders
	 */
	public void share(String id, ElementShareOperations shareOperations, Handler<AsyncResult<Collection<String>	>> h);

	/**
	 * 
	 * @param ids             collection of the file or the folder IDS to share
	 * @param shareOperations defining what kind of share operation to do
	 * @param h               handler that emit the list of userid who can see files or folders
	 */
	public void shareAll(Collection<String> ids, ElementShareOperations shareOperations,
			Handler<AsyncResult<Collection<String>>> h);
	/**
	 * 
	 * @param ids
	 * @param h
	 */
	void markAsFavorites(Collection<String>ids,Handler<AsyncResult<Collection<JsonObject>>> h);
}
