package org.entcore.common.folders.impl;

import java.util.Collection;
import java.util.Optional;

import org.entcore.common.folders.ElementQuery;
import org.entcore.common.folders.ElementShareOperations;
import org.entcore.common.folders.FolderManager;
import org.entcore.common.folders.QuotaService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FolderManagerWithQuota implements FolderManager {
	protected final QuotaService quotaService;
	protected final FolderManager folderManager;
	protected final EventBus eventBus;
	protected final QueryHelper queryHelper;
	protected final int quotaThreshold;

	public FolderManagerWithQuota(String collection, int quotaTreshold, QuotaService quotaService,
			FolderManager folderManager, EventBus bus) {
		super();
		this.eventBus = bus;
		this.quotaThreshold = quotaTreshold;
		this.quotaService = quotaService;
		this.folderManager = folderManager;
		this.queryHelper = new QueryHelper(collection);
	}

	public Future<Long> computFreeSpace(final UserInfos userInfos) {
		Future<Long> future = Future.future();
		try {
			long quota = Long.valueOf(userInfos.getAttribute("quota").toString());
			long storage = Long.valueOf(userInfos.getAttribute("storage").toString());
			future.complete(quota - storage);
		} catch (Exception e) {
			quotaService.quotaAndUsage(userInfos.getUserId(), new Handler<Either<String, JsonObject>>() {
				@Override
				public void handle(Either<String, JsonObject> r) {
					if (r.isRight()) {
						JsonObject j = r.right().getValue();
						if (j != null) {
							long quota = j.getLong("quota", 0l);
							long storage = j.getLong("storage", 0l);
							for (String attr : j.fieldNames()) {
								UserUtils.addSessionAttribute(eventBus, userInfos.getUserId(), attr, j.getLong(attr),
										null);
							}
							future.complete(quota - storage);
						} else {
							future.fail("not.found");
						}
					} else {
						future.fail(r.left().getValue());
					}
				}
			});
		}
		return future;
	}

	@Override
	public void createFolder(JsonObject folder, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		// dont need to check quota
		this.folderManager.createFolder(folder, user, handler);
	}

	@Override
	public void createFolder(String destinationFolderId, UserInfos user, JsonObject folder,
			Handler<AsyncResult<JsonObject>> handler) {
		// dont need to check quota
		this.folderManager.createFolder(destinationFolderId, user, folder, handler);
	}

	@Override
	public void info(String id, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		// dont need to check
		this.folderManager.info(id, user, handler);
	}

	@Override
	public void list(String idFolder, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		// dont need to check
		this.folderManager.list(idFolder, user, handler);
	}

	@Override
	public void listFoldersRecursivelyFromFolder(String idFolder, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		// dont need to check
		this.folderManager.listFoldersRecursivelyFromFolder(idFolder, user, handler);
	}

	@Override
	public void listFoldersRecursively(UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		// dont need to check
		this.folderManager.listFoldersRecursively(user, handler);
	}

	@Override
	public void downloadFile(String id, UserInfos user, HttpServerRequest request) {
		// dont need to check
		this.folderManager.downloadFile(id, user, request);
	}

	@Override
	public void downloadFiles(Collection<String> ids, UserInfos user, HttpServerRequest request) {
		// dont need to check
		this.folderManager.downloadFiles(ids, user, request);
	}

	@Override
	public void updateShared(String id, UserInfos user, Handler<AsyncResult<Void>> handler) {
		// do not need to check
		this.folderManager.updateShared(id, user, handler);
	}

	@Override
	public void rename(String id, String newName, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		// Dont need to check
		this.folderManager.rename(id, newName, user, handler);
	}

	@Override
	public void moveAll(Collection<String> sourceId, String destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		this.folderManager.moveAll(sourceId, destinationFolderId, user, handler);
	}

	@Override
	public void move(String sourceId, String destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonObject>> handler) {
		// dont need to check
		this.folderManager.move(sourceId, destinationFolderId, user, handler);
	}

	private Future<Void> incrementFreeSpace(UserInfos user, final long amount) {
		if (amount == 0) {
			return Future.succeededFuture();
		}
		Future<Void> future = Future.future();
		quotaService.incrementStorage(user.getUserId(), amount, this.quotaThreshold, ev -> {
			if (ev.isRight()) {
				JsonObject j = ev.right().getValue();
				UserUtils.addSessionAttribute(eventBus, user.getUserId(), "storage", j.getLong("storage"), null);
				if (j.getBoolean("notify", false)) {
					quotaService.notifySmallAmountOfFreeSpace(user.getUserId());
				}
				future.complete(null);
			} else {
				future.fail(ev.left().getValue());
			}
		});
		return future;
	}

	private Future<Void> decrementFreeSpace(UserInfos user, final long amount) {
		if (amount == 0) {
			return Future.succeededFuture();
		}
		Future<Void> future = Future.future();
		quotaService.decrementStorage(user.getUserId(), amount, this.quotaThreshold, ev -> {
			if (ev.isRight()) {
				future.complete(null);
			} else {
				future.fail(ev.left().getValue());
			}
		});
		return future;
	}

	@Override
	public void copy(String sourceId, Optional<String> destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		// fetch only if i have right on it (inherit share and owner)
		CompositeFuture.all(
				queryHelper.findOne(queryHelper.queryBuilder().filterByInheritShareAndOwner(user).withId(sourceId)),
				computFreeSpace(user)).compose(results -> {
					final JsonObject founded = (JsonObject) results.resultAt(0);
					final long freeSpace = (int) results.resultAt(1);
					final int type = DocumentHelper.getType(founded);
					switch (type) {
					case FOLDER_TYPE:
						return queryHelper.listRecursive(queryHelper.queryBuilder().withExcludeDeleted()
								.withParent(sourceId).withFileType(FILE_TYPE)).compose(rows -> {
									return Future.succeededFuture(DocumentHelper.getFileSize(rows));
								}).compose(size -> {
									if (freeSpace >= size) {
										Future<JsonArray> innerFuture = Future.future();
										this.folderManager.copy(sourceId, destinationFolderId, user,
												innerFuture.completer());
										return innerFuture;
									} else {
										return Future.failedFuture("files.too.large");
									}
								});
					case FILE_TYPE:
						final long size = DocumentHelper.getFileSize(founded);
						if (freeSpace >= size) {
							Future<JsonArray> innerFuture = Future.future();
							this.folderManager.copy(sourceId, destinationFolderId, user, innerFuture.completer());
							return innerFuture;
						} else {
							return Future.failedFuture("files.too.large");
						}
					default:
						return Future.failedFuture("Could not identify type for:" + sourceId);
					}
				})
				// update quota before return
				.compose(copies -> {
					final long size = DocumentHelper.getFileSize(copies);
					return decrementFreeSpace(user, size).map(copies);
				}).setHandler(handler);
	}

	@Override
	public void trash(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		// dont need to change quota
		this.folderManager.trash(id, user, handler);
	}

	@Override
	public void restore(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		// dont need to change quota
		this.folderManager.restore(id, user, handler);
	}

	@Override
	public void delete(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		Future<JsonArray> future = Future.future();
		this.delete(id, user, future.completer());
		future.compose(deleted -> {
			final long size = DocumentHelper.getFileSize(deleted);
			return incrementFreeSpace(user, size).map(deleted);
		}).setHandler(handler);
	}

	@Override
	public void share(String id, ElementShareOperations shareOperations, Handler<AsyncResult<JsonObject>> h) {
		// dont need to change
		this.folderManager.share(id, shareOperations, h);
	}

	@Override
	public void shareAll(Collection<String> ids, ElementShareOperations shareOperations,
			Handler<AsyncResult<Collection<JsonObject>>> h) {
		// dont need to change
		this.folderManager.shareAll(ids, shareOperations, h);
	}

	@Override
	public void findByQuery(ElementQuery query, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		this.folderManager.findByQuery(query, user, handler);
	}

	@Override
	public void addFile(Optional<String> parentId, JsonObject doc, String ownerId, String ownerName,
			Handler<AsyncResult<JsonObject>> handler) {
		this.folderManager.addFile(parentId, doc, ownerId, ownerName, handler);
	}

	@Override
	public void updateFile(String id, Optional<String> parentId, JsonObject doc, Handler<AsyncResult<JsonObject>> handler) {
		this.folderManager.updateFile(id, parentId, doc, handler);
	}

	@Override
	public void copyAll(Collection<String> sourceIds, Optional<String> destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		this.folderManager.copyAll(sourceIds, destinationFolderId, user, handler);
	}

	@Override
	public void markAsFavorites(Collection<String> ids, Handler<AsyncResult<Collection<JsonObject>>> h) {
		this.folderManager.markAsFavorites(ids, h);
	}

}
