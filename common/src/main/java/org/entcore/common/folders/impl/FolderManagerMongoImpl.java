package org.entcore.common.folders.impl;

import static org.entcore.common.folders.impl.QueryHelper.isOk;
import static org.entcore.common.folders.impl.QueryHelper.toErrorStr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.entcore.common.folders.ElementQuery;
import org.entcore.common.folders.ElementShareOperations;
import org.entcore.common.folders.FolderManager;
import org.entcore.common.folders.impl.QueryHelper.DocumentQueryBuilder;
import org.entcore.common.share.ShareService;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.swift.storage.DefaultAsyncResult;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.ETag;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FolderManagerMongoImpl implements FolderManager {
	protected final Storage storage;
	protected final FileSystem fileSystem;
	protected final QueryHelper queryHelper;
	protected final ShareService shareService;

	private static <T> AsyncResult<T> toError(String msg) {
		return new DefaultAsyncResult<>(new Exception(msg));
	}

	private static <T> AsyncResult<T> toError(Throwable msg) {
		return new DefaultAsyncResult<>((msg));
	}

	public FolderManagerMongoImpl(String collection, Storage sto, FileSystem fs, ShareService shareService) {
		this.storage = sto;
		this.fileSystem = fs;
		this.shareService = shareService;
		this.queryHelper = new QueryHelper(collection);
	}

	@Override
	public void findByQuery(ElementQuery query, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		DocumentQueryBuilder builder = queryHelper.queryBuilder();
		if (query.getVisibilities() != null) {
			// visibility and shared or owner
			if (query.getShared()) {
				builder.filterByInheritSharedAndOwnerVisibilities(user, query.getVisibilities());
			} else {
				builder.filterByOwnerVisibilities(user, query.getVisibilities());
			}
		} else {
			// search by share or owner
			if (query.getShared()) {
				builder.filterByInheritShareAndOwner(user);
			} else {
				builder.filterByOwner(user);
			}
		}
		//
		if (query.getId() != null) {
			builder.withId(query.getId());
		}
		if (query.getType() != null) {
			builder.withFileType(query.getType());
		}
		if (query.getTrash() != null) {
			if (query.getTrash()) {
				builder.withOnlyDeletedFile();
			} else {
				builder.withExcludeDeleted();
			}
		}
		if (query.getParentId() != null) {
			builder.withParent(query.getParentId());
		}
		if (query.getApplication() != null) {
			builder.withKeyValue("application", query.getApplication());
		}
		if (query.getSearchByName() != null) {
			builder.withNameMatch(query.getSearchByName());
		}
		if (query.getParams() != null && !query.getParams().isEmpty()) {
			query.getParams().forEach((key, value) -> {
				builder.withKeyValue(key, value);
			});
		}
		// advanced filters
		if (query.getFullTextSearch() != null) {
			builder.withFullTextSearch(query.getFullTextSearch());
		}
		if (query.getProjection() != null && !query.getProjection().isEmpty()) {
			builder.withProjections(query.getProjection());
		}
		if (query.getSort() != null && !query.getSort().isEmpty()) {
			builder.withSorts(query.getSort());
		}
		if (query.getLimit() != null) {
			builder.withSkipAndLimit(query.getSkip(), query.getLimit());
		}
		// query
		if (query.getHierarchical() != null && query.getHierarchical()) {
			queryHelper.listRecursive(builder).setHandler(handler);
		} else {
			queryHelper.findAll(builder).setHandler(handler);
		}
	}

	@Override
	public void addFile(Optional<String> parentId, JsonObject doc, UserInfos user,
			Handler<AsyncResult<JsonObject>> handler) {
		Future<JsonObject> future = parentId.isPresent() ? this.mergeShared(parentId.get(), doc)
				: Future.succeededFuture();
		future.compose(parent -> {
			String now = MongoDb.formatDate(new Date());
			if (parentId.isPresent()) {
				doc.put("eParent", parentId);
			}
			doc.put("eType", FILE_TYPE);
			doc.put("created", now);
			doc.put("modified", now);
			doc.put("owner", user.getUserId());
			doc.put("ownerName", user.getUsername());
			//
			return queryHelper.insert(doc);
		}).setHandler(handler);
	}

	@Override
	public void updateFile(String id, Optional<String> parentId, JsonObject doc, UserInfos user,
			Handler<AsyncResult<JsonObject>> handler) {
		Future<JsonObject> future = parentId.isPresent() ? this.mergeShared(parentId.get(), doc)
				: Future.succeededFuture();
		future.compose(parent -> {
			String now = MongoDb.formatDate(new Date());
			if (parentId.isPresent()) {
				doc.put("eParent", parentId);
			}
			doc.put("modified", now);
			//
			return queryHelper.update(id, doc).map(doc);
		}).setHandler(handler);
	}

	private Future<JsonObject> mergeShared(String parentFolderId, JsonObject current) {
		return queryHelper.findById(parentFolderId).compose(parentFolder -> {
			Future<JsonObject> future = Future.future();
			if (parentFolder == null) {
				future.fail("not.found");
			} else {
				try {
					InheritShareComputer.mergeShared(parentFolder, current);
					future.complete(current);
				} catch (Exception e) {
					future.fail(e);
				}
			}
			return future;
		});
	}

	public void share(String id, ElementShareOperations shareOperations, Handler<AsyncResult<Collection<String>>> hh) {
		UserInfos user = shareOperations.getUser();
		// TODO owner or shared action
		queryHelper.findOne(queryHelper.queryBuilder().withId(id).filterByInheritShareAndOwnerWithAction(user,
				shareOperations.getShareAction())).compose(ev -> {
					Future<JsonObject> futureShared = Future.future();
					// compute shared after sharing
					final Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {

						@Override
						public void handle(Either<String, JsonObject> event) {
							if (event.isRight()) {
								// TODO save current shared?
								updateShared(id, user, ev -> {
									if (ev.succeeded()) {
										futureShared.complete(e);
									} else {

									}
								});
							} else {
								futureShared.fail(event.left().getValue());
							}
						}
					};
					//
					switch (shareOperations.getKind()) {
					case GROUP_SHARE:
						this.shareService.groupShare(user.getUserId(), shareOperations.getGroupId(), id,
								shareOperations.getActions(), handler);
						break;
					case GROUP_SHARE_REMOVE:
						this.shareService.removeGroupShare(shareOperations.getGroupId(), id,
								shareOperations.getActions(), handler);
						break;
					case USER_SHARE:
						this.shareService.userShare(user.getUserId(), shareOperations.getUserId(), id,
								shareOperations.getActions(), handler);
						break;
					case USER_SHARE_REMOVE:
						this.shareService.removeUserShare(shareOperations.getUserId(), id, shareOperations.getActions(),
								handler);
						break;
					case SHARE_OBJECT:
						this.shareService.share(shareOperations.getUserId(), id, shareOperations.getShare(), handler);
						break;
					}
				});
	}

	@Override
	public void shareAll(Collection<String> ids, ElementShareOperations shareOperations,
			Handler<AsyncResult<Collection<Collection<String>>>> h) {
		@SuppressWarnings("rawtypes")
		List<Future> futures = ids.stream().map(id -> {
			Future<JsonObject> future = Future.future();
			share(id, shareOperations, future.completer());
			return future;
		}).collect(Collectors.toList());
		CompositeFuture.all(futures).map(res -> {
			Collection<JsonObject> temp = res.list();
			return temp;
		}).setHandler(h);
	}

	@Override
	public void createFolder(String destinationFolderId, UserInfos user, JsonObject folder,
			Handler<AsyncResult<JsonObject>> handler) {
		this.mergeShared(destinationFolderId, folder).compose(sharedFolder -> {
			Future<JsonObject> futureCreate = Future.future();
			sharedFolder.put("eParent", destinationFolderId);
			this.createFolder(sharedFolder, user, futureCreate.completer());
			return futureCreate;
		}).setHandler(handler);
	}

	@Override
	public void createFolder(JsonObject folder, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		folder.put("eType", FOLDER_TYPE);
		String now = MongoDb.formatDate(new Date());
		folder.put("created", now);
		folder.put("modified", now);
		folder.put("owner", user.getUserId());
		folder.put("ownerName", user.getUsername());
		queryHelper.insert(folder).setHandler(handler);
	}

	@Override
	public void info(String id, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		// find only non deleted file/folder that i can see
		Future<JsonObject> future = queryHelper
				.findOne(queryHelper.queryBuilder().withId(id).filterByInheritShareAndOwner(user).withExcludeDeleted());
		future.setHandler(handler);
	}

	@Override
	public void list(String idFolder, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		Future<JsonArray> future = queryHelper.findAll(queryHelper.queryBuilder().filterByInheritShareAndOwner(user)
				.withParent(idFolder).withExcludeDeleted());
		future.setHandler(handler);
	}

	@Override
	public void listFoldersRecursively(UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		Future<JsonArray> future = queryHelper
				.listRecursive(queryHelper.queryBuilder().filterBySharedAndOwner(user).withExcludeDeleted());
		future.setHandler(handler);
	}

	@Override
	public void listFoldersRecursivelyFromFolder(String idFolder, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		Future<JsonArray> future = queryHelper.listRecursive(
				queryHelper.queryBuilder().filterBySharedAndOwner(user).withExcludeDeleted().withId(idFolder));
		future.setHandler(handler);
	}

	private boolean inlineDocumentResponse(JsonObject doc, String application) {
		JsonObject metadata = doc.getJsonObject("metadata");
		String storeApplication = doc.getString("application");
		return metadata != null && !"WORKSPACE".equals(storeApplication) && ("image/jpeg"
				.equals(metadata.getString("content-type")) || "image/gif".equals(metadata.getString("content-type"))
				|| "image/png".equals(metadata.getString("content-type"))
				|| "image/tiff".equals(metadata.getString("content-type"))
				|| "image/vnd.microsoft.icon".equals(metadata.getString("content-type"))
				|| "image/svg+xml".equals(metadata.getString("content-type"))
				|| ("application/octet-stream".equals(metadata.getString("content-type")) && application != null));
	}

	private static void notModified(HttpServerRequest request, String fileId) {
		if (fileId != null && !fileId.trim().isEmpty()) {
			request.response().headers().add("ETag", fileId);
		}
		request.response().setStatusCode(304).setStatusMessage("Not Modified").end();
	}

	private void downloadOneFile(JsonObject bodyRoot, HttpServerRequest request) {
		String name = bodyRoot.getString("name");
		String file = bodyRoot.getString("file");
		JsonObject metadata = bodyRoot.getJsonObject("metadata");
		boolean inline = inlineDocumentResponse(bodyRoot, request.params().get("application"));
		if (inline && ETag.check(request, file)) {
			notModified(request, file);
		} else {
			storage.sendFile(file, name, request, inline, metadata);
		}
		return;
	}

	@Override
	public void downloadFiles(Collection<String> ids, UserInfos user, HttpServerRequest request) {
		queryHelper
				.findAll(
						queryHelper.queryBuilder().withIds(ids).filterByInheritShareAndOwner(user).withExcludeDeleted())
				.setHandler(msg -> {
					if (msg.succeeded() && msg.result().size() == ids.size()) {
						// download ONE file
						JsonArray bodyRoots = msg.result();
						if (bodyRoots.size() == 1 //
								&& bodyRoots.getJsonObject(0).getInteger("eType", FOLDER_TYPE) == FILE_TYPE) {
							downloadOneFile(bodyRoots.getJsonObject(0), request);
							return;
						}
						// download multiple files
						@SuppressWarnings("rawtypes")
						List<Future> futures = bodyRoots.stream().map(m -> (JsonObject) m).map(bodyRoot -> {
							switch (DocumentHelper.getType(bodyRoot)) {
							case FOLDER_TYPE:
								String idFolder = bodyRoot.getString("_id");
								Future<JsonArray> future = queryHelper.listRecursive(queryHelper.queryBuilder()
										.filterBySharedAndOwner(user).withExcludeDeleted().withId(idFolder));
								return future;
							case FILE_TYPE:
								JsonArray res = new JsonArray();
								res.add(bodyRoot);
								return Future.succeededFuture(res);
							default:
								Future<JsonArray> f = Future
										.failedFuture("Could not determine the type (file or folder) for id:"
												+ bodyRoot.getString("_id"));
								return f;
							}
						}).collect(Collectors.toList());
						// Zip all files
						CompositeFuture.all(futures).setHandler(result -> {
							if (result.succeeded()) {
								JsonArray rows = result.result().list().stream().map(a -> (JsonArray) a)
										.reduce(new JsonArray(), (a1, a2) -> {
											a1.addAll(a2);
											return a1;
										});
								ZipHelper zipBuilder = new ZipHelper(storage, fileSystem);
								zipBuilder.buildAndSend(rows, request).setHandler(zipEvent -> {
									if (zipEvent.failed()) {
										request.response().setStatusCode(500).end();
									}
								});
							} else {
								request.response().setStatusCode(400).setStatusMessage(result.cause().getMessage())
										.end();
							}
						});

					} else {
						request.response().setStatusCode(404).end();
					}

				});
	}

	@Override
	public void downloadFile(String id, UserInfos user, HttpServerRequest request) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject bodyRoot = msg.result();
				switch (DocumentHelper.getType(bodyRoot)) {
				case FOLDER_TYPE:
					String idFolder = bodyRoot.getString("_id");
					Future<JsonArray> future = queryHelper.listRecursive(queryHelper.queryBuilder()
							.filterBySharedAndOwner(user).withExcludeDeleted().withId(idFolder));

					future.setHandler(result -> {
						if (result.succeeded()) {
							JsonArray rows = result.result();
							ZipHelper zipBuilder = new ZipHelper(storage, fileSystem);
							zipBuilder.buildAndSend(bodyRoot, rows, request).setHandler(zipEvent -> {
								if (zipEvent.failed()) {
									request.response().setStatusCode(500).end();
								}
							});
						} else {
							request.response().setStatusCode(404).end();
						}
					});
					return;
				case FILE_TYPE:
					downloadOneFile(bodyRoot, request);
					return;
				default:
					request.response().setStatusCode(400)
							.setStatusMessage("Could not determine the type (file or folder) for id:" + id).end();
					return;
				}
			} else {
				request.response().setStatusCode(404).end();
			}
		});
	}

	private Future<JsonArray> updateInheritedShared(String parentId, JsonObject body) {
		if (parentId != null) {
			return this.mergeShared(parentId, body).compose(current -> {
				return queryHelper.updateInheritShares(current);
			}).map(current -> current.getJsonArray("inheritedShares"));
		} else {
			return Future.succeededFuture();
		}
	}

	@Override
	public void updateShared(String id, UserInfos user, Handler<AsyncResult<Void>> handler) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject bodyInfo = msg.result();
				String parent = bodyInfo.getString("eParent");
				switch (DocumentHelper.getType(bodyInfo)) {
				case FOLDER_TYPE:
					this.updateInheritedShared(parent, bodyInfo).compose(res -> {
						String idFolder = bodyInfo.getString("_id");
						return queryHelper.listRecursive(
								queryHelper.queryBuilder().filterBySharedAndOwner(user).withId(idFolder));
					}).compose(rows -> {
						try {
							InheritShareComputer computer = new InheritShareComputer(bodyInfo, rows);
							computer.compute();
							JsonArray operations = computer.buildMongoBulk();
							return queryHelper.bulkUpdate(operations);
						} catch (Exception e) {
							return Future.failedFuture(e);
						}
					}).setHandler(handler);
					return;
				case FILE_TYPE:
					this.updateInheritedShared(parent, bodyInfo).setHandler(res -> handler
							.handle(res.succeeded() ? new DefaultAsyncResult<>(null) : toError(res.cause())));
					return;
				default:
					handler.handle(toError("Could not determine the type (file or folder) for id:" + id));
					return;
				}
			} else {
				handler.handle(toError(msg.cause()));
			}
		});

	}

	@Override
	public void rename(String id, String newName, UserInfos user, Handler<AsyncResult<Void>> handler) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				MongoUpdateBuilder set = new MongoUpdateBuilder().addToSet("name", newName);
				queryHelper.update(id, set).setHandler(handler);
			} else {
				handler.handle(toError(msg.cause()));
			}
		});
	}

	@Override
	public void moveAll(Collection<String> sourceIds, String destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		@SuppressWarnings("rawtypes")
		List<Future> futures = sourceIds.stream().map(sourceId -> {
			Future<JsonObject> future = Future.future();
			this.move(sourceId, destinationFolderId, user, future.completer());
			return future;
		}).collect(Collectors.toList());
		CompositeFuture.all(futures).map(results -> {
			return results.list().stream().map(o -> (JsonObject) o).collect(JsonArray::new, JsonArray::add,
					JsonArray::addAll);
		}).setHandler(handler);
	}

	@Override
	public void move(String sourceId, String destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonObject>> handler) {
		this.info(sourceId, user, msg -> {
			if (msg.succeeded()) {
				JsonObject bodyFind = msg.result();
				JsonObject previous = bodyFind.getJsonObject("result");
				if (previous != null) {
					this.updateShared(sourceId, user, updateEvent -> {
						MongoUpdateBuilder set = new MongoUpdateBuilder().addToSet("eParent", destinationFolderId);
						queryHelper.update(sourceId, set).map(v -> previous).setHandler(handler);
					});
				} else {
					handler.handle(toError("not.found"));
				}
			} else {
				handler.handle(toError(msg.cause()));
			}
		});

	}

	private Future<JsonArray> copyFolderRecursively(UserInfos user, String oldParentId, Optional<String> newParentId) {
		// list all tree including folders excluding deleted files
		return queryHelper.findAll(queryHelper.queryBuilder().withParent(oldParentId).withExcludeDeleted())
				.compose(children -> {
					return this.copyFile(user, children.stream().map(u -> (JsonObject) u).collect(Collectors.toSet()),
							newParentId);
				}).compose(copies -> {
					// for each folder copy recursively
					Set<JsonObject> folders = copies.stream().map(v -> (JsonObject) v)
							.filter(o -> o.getInteger("eType", -1) == FOLDER_TYPE).collect(Collectors.toSet());
					@SuppressWarnings("rawtypes")
					List<Future> futures = folders.stream().map(folder -> {
						return copyFolderRecursively(user, folder.getString("copyFromId"),
								Optional.ofNullable(folder.getString("_id")));
					}).collect(Collectors.toList());
					// merge children result and current result
					return CompositeFuture.all(futures).map(res -> {
						JsonArray result = new JsonArray();
						result.addAll(copies);
						res.list().stream().map(a -> (JsonArray) a).forEach(a -> result.addAll(a));
						return result;
					});
				});
	}

	private Future<JsonArray> copyFile(UserInfos user, Collection<JsonObject> originals, Optional<String> parent) {
		return StorageHelper.copyFileInStorage(storage, originals).compose(oldFileIdForNewFileId -> {
			// set newFileIds and parent
			JsonArray copies = originals.stream().map(o -> {
				JsonObject copy = o.copy();
				copy.put("copyFromId", copy.getString("_id"));
				copy.remove("_id");
				if (parent.isPresent()) {
					copy.put("eParent", parent.get());
				} else {
					copy.remove("eParent");
				}
				copy.put("owner", user.getUserId());
				copy.put("ownerName", user.getUsername());
				StorageHelper.replaceAll(copy, oldFileIdForNewFileId);
				return copy;
			}).collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
			// save copies in database using bulk (new fileid)
			return queryHelper.insertAll(copies).map(copies);
		});
	}

	@Override
	public void copyAll(Collection<String> sourceIds, Optional<String> destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		@SuppressWarnings("rawtypes")
		List<Future> futures = sourceIds.stream().map(id -> {
			Future<JsonArray> future = Future.future();
			copy(id, destinationFolderId, user, future.completer());
			return future;
		}).collect(Collectors.toList());
		CompositeFuture.all(futures).compose(results -> {
			JsonArray array = new JsonArray();
			results.list().stream().forEach(r -> {
				array.addAll((JsonArray) r);
			});
			return Future.succeededFuture(array);
		}).setHandler(handler);
	}

	@Override
	public void copy(String sourceId, Optional<String> destFolderId, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		this.info(sourceId, user, message -> {
			if (message.succeeded()) {
				// check whether id is empty
				final Optional<String> safeDestFolderId = destFolderId.isPresent()
						&& StringUtils.isEmpty(destFolderId.get()) ? Optional.empty() : destFolderId;

				JsonObject original = message.result();
				String id = original.getString("_id");
				switch (DocumentHelper.getType(original)) {
				case FOLDER_TYPE:
					copyFolderRecursively(user, id, safeDestFolderId).setHandler(handler);
					return;
				case FILE_TYPE:
					copyFile(user, Arrays.asList(original), safeDestFolderId).setHandler(handler);
					return;
				default:
					handler.handle(toError("Could not determine the type (file or folder) for id:" + id));
					return;
				}
			} else {
				handler.handle(message.mapEmpty());
			}
		});
	}

	private Future<List<JsonObject>> deleteFiles(List<JsonObject> files) {
		// Set to avoid duplicate
		Set<String> ids = files.stream().map(o -> o.getString("_id")).collect(Collectors.toSet());
		return queryHelper.deleteFiles((ids)).compose(res -> {
			Future<List<JsonObject>> future = Future.future();
			List<String> listOfFilesIds = StorageHelper.getListOfFileIds(files);
			this.storage.removeFiles(new JsonArray(listOfFilesIds), resDelete -> {
				if (isOk(resDelete)) {
					future.complete(files);
				} else {
					future.fail(toErrorStr(resDelete));
				}
			});
			return future;
		});
	}

	private Future<List<JsonObject>> deleteFolders(List<JsonObject> files) {
		// Set to avoid duplicate
		Set<String> ids = files.stream().map(o -> o.getString("_id")).collect(Collectors.toSet());
		return queryHelper.deleteFiles((ids)).map(files);
	}

	private Future<List<JsonObject>> deleteFolderRecursively(JsonObject folder, UserInfos user) {
		String idFolder = folder.getString("_id");
		return queryHelper.listRecursive(queryHelper.queryBuilder().filterBySharedAndOwner(user).withId(idFolder))
				.compose(rows -> {
					if (rows.isEmpty()) {
						return Future.succeededFuture();
					}
					List<JsonObject> files = rows.stream().map(o -> (JsonObject) o)
							.filter(o -> o.getInteger("eType", -1) == FILE_TYPE).collect(Collectors.toList());
					List<JsonObject> folders = rows.stream().map(o -> (JsonObject) o)
							.filter(o -> o.getInteger("eType", -1) != FILE_TYPE).collect(Collectors.toList());

					return CompositeFuture.all(deleteFolders(folders), deleteFiles(files));
				}).map(result -> {
					if (result.result().size() == 0) {
						return new ArrayList<>();
					}
					List<JsonObject> array = result.result().resultAt(0);
					array.addAll(result.result().resultAt(1));
					return array;
				});
	}

	@Override
	public void delete(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject body = msg.result();
				switch (DocumentHelper.getType(body)) {
				case FOLDER_TYPE:
					deleteFolderRecursively(body, user)
							.map(v -> v.stream().collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
							.setHandler(handler);
					return;
				case FILE_TYPE:
					deleteFiles(Arrays.asList(body))
							.map(v -> v.stream().collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
							.setHandler(handler);
					return;
				default:
					handler.handle(toError("Could not determine the type (file or folder) for id:" + id));
					return;
				}
			} else {
				handler.handle(toError(msg.cause()));
			}
		});
	}

	private Future<JsonArray> setDeleteFlag(List<JsonObject> files, boolean deleted) {
		// Set to avoid duplicate
		Set<String> ids = files.stream().map(o -> o.getString("_id")).collect(Collectors.toSet());
		return queryHelper.updateAll(ids, new MongoUpdateBuilder().push("deleted", deleted))
				.map(new JsonArray(new ArrayList<>(ids)));
	}

	private Future<JsonArray> setDeleteFlagRecursively(JsonObject folder, UserInfos user, boolean deleted) {
		String idFolder = folder.getString("_id");
		return queryHelper.listRecursive(queryHelper.queryBuilder().filterBySharedAndOwner(user).withId(idFolder))
				.compose(rows -> {
					if (rows.isEmpty()) {
						return Future.succeededFuture();
					}
					List<JsonObject> all = rows.stream().map(o -> (JsonObject) o).collect(Collectors.toList());
					return setDeleteFlag(all, deleted);
				});
	}

	private void setDeleteFlag(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler, boolean deleted) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject body = msg.result();
				switch (DocumentHelper.getType(body)) {
				case FOLDER_TYPE:
					setDeleteFlagRecursively(body, user, deleted).setHandler(handler);
					return;
				case FILE_TYPE:
					setDeleteFlag(Arrays.asList(body), deleted).setHandler(handler);
					return;
				default:
					handler.handle(toError("Could not determine the type (file or folder) for id:" + id));
					return;
				}
			} else {
				handler.handle(toError(msg.cause()));
			}
		});
	}

	@Override
	public void restore(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		setDeleteFlag(id, user, handler, false);
	}

	@Override
	public void trash(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		setDeleteFlag(id, user, handler, true);
	}
}
