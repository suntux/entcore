package org.entcore.common.folders.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.DateUtils;
import org.entcore.common.utils.StringUtils;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

import fr.wseduc.mongodb.AggregationsBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.swift.storage.DefaultAsyncResult;
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
	protected final String collection;
	protected final FileSystem fileSystem;
	protected final MongoDb mongo = MongoDb.getInstance();

	private static JsonObject toJson(QueryBuilder queryBuilder) {
		return MongoQueryBuilder.build(queryBuilder);
	}

	private static boolean isOk(JsonObject body) {
		return "ok".equals(body.getString("status"));
	}

	private static <T> AsyncResult<T> toError(String msg) {
		return new DefaultAsyncResult<>(new Exception(msg));
	}

	private static <T> AsyncResult<T> toError(Throwable msg) {
		return new DefaultAsyncResult<>((msg));
	}

	private static <T> AsyncResult<T> toError(JsonObject body) {
		return new DefaultAsyncResult<>(new Exception(toErrorStr(body)));
	}

	private static String toErrorStr(JsonObject body) {
		return body.getString("error");
	}

	public FolderManagerMongoImpl(String collection, Storage sto, FileSystem fs) {
		this.storage = sto;
		this.fileSystem = fs;
		this.collection = collection;
	}

	private void mergeShared(String parentFolderId, JsonObject current, Handler<AsyncResult<JsonObject>> handler) {
		mongo.findOne(collection, toJson(QueryBuilder.start("_id").is(parentFolderId)), message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				JsonObject parentFolder = body.getJsonObject("result");
				if (parentFolder == null) {
					handler.handle(toError("Could not found parent folder with id :" + parentFolderId));
				} else {
					try {
						InheritShareComputer.mergeShared(parentFolder, current);
						handler.handle(new DefaultAsyncResult<>(current));
					} catch (Exception e) {
						handler.handle(toError(e));
					}
				}
			} else {
				handler.handle(toError(body));
			}
		});
	}

	@Override
	public void createFolder(String destinationFolderId, UserInfos user, JsonObject folder,
			Handler<AsyncResult<JsonObject>> handler) {
		this.mergeShared(destinationFolderId, folder, message -> {
			if (message.succeeded()) {
				folder.put("eParent", destinationFolderId);
				this.createFolder(folder, user, handler);
			} else {
				handler.handle(message);
			}
		});
	}

	@Override
	public void createFolder(JsonObject folder, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		folder.put("eType", FolderManager.FOLDER_TYPE);
		folder.put("created", DateUtils.getDateJsonObject(new Date()));
		folder.put("modified", DateUtils.getDateJsonObject(new Date()));
		folder.put("owner", user.getUserId());
		folder.put("ownerName", user.getUsername());
		mongo.insert(collection, folder, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				handler.handle(new DefaultAsyncResult<>(body.getJsonObject("result")));
			} else {
				handler.handle(toError(body));
			}
		});
	}

	@Override
	public void info(String id, UserInfos user, Handler<AsyncResult<JsonObject>> handler) {
		// find only non deleted file/folder that i can see
		mongo.find(collection,
				toJson(filterByInheritShareAndOwner(user).and("_id").is(id).and("deleted").notEquals(true)),
				message -> {
					JsonObject body = message.body();
					if (isOk(body)) {
						handler.handle(new DefaultAsyncResult<>(body.getJsonObject("result")));
					} else {
						handler.handle(toError(body));
					}
				});
	}

	@Override
	public void list(String idFolder, Handler<AsyncResult<JsonArray>> handler) {
		mongo.find(collection, toJson(QueryBuilder.start("eParent").is(idFolder).and("deleted").notEquals(true)),
				message -> {
					JsonObject body = message.body();
					if (isOk(body)) {
						handler.handle(new DefaultAsyncResult<>(body.getJsonArray("result")));
					} else {
						handler.handle(toError(body));
					}
				});
	}

	@Override
	public void listFoldersRecursively(UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		JsonObject command = AggregationsBuilder.startWithCollection(this.collection)//
				// first : match only not deleted shared folders (root folders)
				.withMatch(filterBySharedAndOwner(user).and("eType").is(FolderManager.FOLDER_TYPE).and("deleted")
						.notEquals(true))//
				// then build the graph from root folder
				.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(), Optional.empty(),
						Optional.empty())//
				// match not deleted files => graphlookup reintroduce deleted children
				.withMatch(QueryBuilder.start("deleted").notEquals(true))//
				// finally project name and parent
				.withProjection(new JsonObject().put("_id", 1).put("name", 1).put("parents", "$tree._id"))//
				.getCommand();
		mongo.aggregate(command, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				handler.handle(new DefaultAsyncResult<>(
						body.getJsonObject("result", new JsonObject()).getJsonArray("result")));
			} else {
				handler.handle(toError(body));
			}
		});
	}

	private QueryBuilder filterBySharedAndOwner(UserInfos user) {
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
		for (String gpId : user.getGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		return new QueryBuilder().or(QueryBuilder.start("owner").is(user.getUserId()).get(),
				QueryBuilder.start("shared")
						.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
	}

	private QueryBuilder filterByInheritShareAndOwner(UserInfos user) {
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
		for (String gpId : user.getGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		return new QueryBuilder().or(QueryBuilder.start("owner").is(user.getUserId()).get(),
				QueryBuilder.start("inheritedShares")
						.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
	}

	@Override
	public void listFoldersRecursivelyFromFolder(String idFolder, UserInfos user,
			Handler<AsyncResult<JsonArray>> handler) {
		JsonObject command = AggregationsBuilder.startWithCollection(this.collection)//
				// match non deleted shared folder
				.withMatch(filterBySharedAndOwner(user).and("eType").is(FolderManager.FOLDER_TYPE).and("_id")
						.is(idFolder).and("deleted").notEquals(true))//
				// build graph
				.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(), Optional.empty(),
						Optional.empty())//
				// match not deleted files => graphlookup reintroduce deleted children
				.withMatch(QueryBuilder.start("deleted").notEquals(true))//
				// project name ids and parents
				.withProjection(new JsonObject().put("_id", 1).put("name", 1).put("parents", "$tree._id"))//
				.getCommand();
		mongo.aggregate(command, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				handler.handle(new DefaultAsyncResult<>(
						body.getJsonObject("result", new JsonObject()).getJsonArray("result")));
			} else {
				handler.handle(toError(body));
			}
		});
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

	@Override
	public void downloadFile(String id, UserInfos user, HttpServerRequest request) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject bodyRoot = msg.result();
				String name = bodyRoot.getString("name");
				String file = bodyRoot.getString("file");
				JsonObject metadata = bodyRoot.getJsonObject("metadata");
				switch (bodyRoot.getInteger("eType", -1)) {
				case FolderManager.FOLDER_TYPE:
					String idFolder = bodyRoot.getString("_id");
					JsonObject command = AggregationsBuilder.startWithCollection(this.collection)//
							.withMatch(
									filterBySharedAndOwner(user).and("_id").is(idFolder).and("deleted").notEquals(true))//
							.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(), Optional.empty(),
									Optional.empty())//
							// match not deleted files => graphlookup reintroduce deleted children
							.withMatch(QueryBuilder.start("deleted").notEquals(true))//
							.withProjection(new JsonObject().put("_id", 1).put("eType", 1).put("file", 1).put("name", 1)
									.put("parents", "$tree._id"))//
							.getCommand();
					mongo.aggregate(command, message -> {
						JsonObject body = message.body();
						if (isOk(body)) {
							JsonArray rows = body.getJsonObject("result", new JsonObject()).getJsonArray("result");
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
				case FolderManager.FILE_TYPE:
					boolean inline = inlineDocumentResponse(bodyRoot, request.params().get("application"));
					if (inline && ETag.check(request, file)) {
						notModified(request, file);
					} else {
						storage.sendFile(file, name, request, inline, metadata);
					}
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
			Future<JsonArray> future = Future.future();
			this.mergeShared(parentId, body, res -> {
				JsonArray inheritShared = res.result().getJsonArray("inheritedShares");
				JsonObject set = new MongoUpdateBuilder().addToSet("inheritedShares", inheritShared).build();
				String id = body.getString("_id");
				mongo.update(collection, toJson(QueryBuilder.start("_id").is(id)), set, mongoRes -> {
					if (isOk(mongoRes.body())) {
						future.complete(inheritShared);
					} else {
						future.fail(toErrorStr(mongoRes.body()));
					}
				});
			});
			return future;
		} else {
			return Future.future();
		}
	}

	@Override
	public void updateShared(String id, UserInfos user, Handler<AsyncResult<Void>> handler) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject bodyInfo = msg.result();
				String parent = bodyInfo.getString("eParent");
				switch (bodyInfo.getInteger("eType", -1)) {
				case FolderManager.FOLDER_TYPE:
					this.updateInheritedShared(parent, bodyInfo).setHandler(res -> {
						String idFolder = bodyInfo.getString("_id");
						JsonObject command = AggregationsBuilder.startWithCollection(this.collection)//
								.withMatch(filterBySharedAndOwner(user).and("_id").is(idFolder))//
								.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(),
										Optional.empty(), Optional.empty())//
								.withProjection(new JsonObject().put("_id", 1).put("eParent", 1).put("eType", 1)
										.put("shared", 1))//
								.getCommand();
						mongo.aggregate(command, message -> {
							JsonObject body = message.body();
							if (isOk(body)) {
								JsonArray rows = body.getJsonObject("result", new JsonObject()).getJsonArray("result");
								InheritShareComputer computer = new InheritShareComputer(bodyInfo, rows);
								try {
									computer.compute();
									JsonArray operations = computer.buildMongoBulk();
									mongo.bulk(collection, operations, bulkEv -> {
										if (isOk(bulkEv.body())) {
											handler.handle(new DefaultAsyncResult<>(null));
										} else {
											handler.handle(toError(bulkEv.body()));
										}
									});
								} catch (Exception e) {
									handler.handle(toError(e));
								}
							} else {
								handler.handle(toError(body));
							}
						});
					});
					return;
				case FolderManager.FILE_TYPE:
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
	public void rename(String id, String newName, Handler<AsyncResult<Void>> handler) {
		JsonObject info = new JsonObject().put("name", newName)//
				.put("modified", DateUtils.getDateJsonObject(new Date()));
		mongo.update(collection, toJson(QueryBuilder.start("_id").is(id)), info, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				handler.handle(new DefaultAsyncResult<>(null));
			} else {
				handler.handle(toError(body));
			}
		});
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
						JsonObject current = previous.copy().put("eParent", destinationFolderId)//
								.put("modified", DateUtils.getDateJsonObject(new Date()));
						mongo.update(collection, toJson(QueryBuilder.start("_id").is(sourceId)), current, message -> {
							JsonObject body = message.body();
							if (isOk(body)) {
								handler.handle(new DefaultAsyncResult<>(null));
							} else {
								handler.handle(toError(body));
							}
						});
					});
				} else {
					handler.handle(toError("Could not found source with id :" + sourceId));
				}
			} else {
				handler.handle(toError(msg.cause()));
			}
		});

	}

	@Override
	public void copy(String sourceId, String destinationFolderId, UserInfos user,
			Handler<AsyncResult<JsonObject>> handler) {
		this.info(sourceId, user, message -> {
			if (message.succeeded()) {
				JsonObject folder = message.result().copy();
				folder.remove("_id");
				this.createFolder(destinationFolderId, user, folder, handler);
				// TODO recursive copy (recompute inheritSHared)
				// TODO bulk insert?
			} else {
				handler.handle(message);
			}
		});
	}

	private Future<JsonArray> deleteFiles(List<JsonObject> files) {
		Future<JsonArray> future = Future.future();
		// Set to avoid duplicate
		Set<String> ids = files.stream().map(o -> o.getString("_id")).collect(Collectors.toSet());
		mongo.delete(collection, toJson(QueryBuilder.start("_id").in(ids)), res -> {
			if (isOk(res.body())) {
				Set<String> filesIds = files.stream().map(file -> file.getString("file", null))
						.filter(file -> !StringUtils.isEmpty(file)).collect(Collectors.toSet());
				Set<String> thumbIds = files.stream().map(file -> file.getJsonObject("thumbnails", null))
						.filter(thumbs -> thumbs != null)//
						.flatMap(thumbs -> thumbs.stream()
								.map(entry -> entry.getValue() != null ? entry.getValue().toString() : null))//
						.filter(file -> !StringUtils.isEmpty(file)).collect(Collectors.toSet());

				//
				List<String> listOfFilesIds = new ArrayList<>(filesIds);
				listOfFilesIds.addAll(thumbIds);
				this.storage.removeFiles(new JsonArray(listOfFilesIds), resDelete -> {
					if (isOk(resDelete)) {
						future.complete(new JsonArray(new ArrayList<>(ids)));
					} else {
						future.fail(toErrorStr(res.body()));
					}
				});
			} else {
				future.fail(toErrorStr(res.body()));
			}
		});
		return future;
	}

	private Future<JsonArray> deleteFolders(List<JsonObject> files) {
		Future<JsonArray> future = Future.future();
		// Set to avoid duplicate
		Set<String> ids = files.stream().map(o -> o.getString("_id")).collect(Collectors.toSet());
		mongo.delete(collection, toJson(QueryBuilder.start("_id").in(ids)), res -> {
			if (isOk(res.body())) {
				future.complete(new JsonArray(new ArrayList<>(ids)));
			} else {
				future.fail(toErrorStr(res.body()));
			}
		});
		return future;
	}

	private void deleteFolderRecursively(JsonObject folder, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		String idFolder = folder.getString("_id");
		JsonObject command = AggregationsBuilder.startWithCollection(this.collection)//
				.withMatch(filterBySharedAndOwner(user).and("_id").is(idFolder))//
				.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(), Optional.empty(),
						Optional.empty())//
				.withProjection(new JsonObject().put("_id", 1).put("eType", 1).put("file", 1).put("thumbnails", 1)
						.put("parents", "$tree._id"))//
				.getCommand();
		mongo.aggregate(command, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				JsonArray rows = body.getJsonObject("result", new JsonObject()).getJsonArray("result");
				if (rows.isEmpty()) {
					handler.handle(new DefaultAsyncResult<>(new JsonArray()));
					return;
				}

				List<JsonObject> files = rows.stream().map(o -> (JsonObject) o)
						.filter(o -> o.getInteger("eType", -1) == FolderManager.FILE_TYPE).collect(Collectors.toList());
				List<JsonObject> folders = rows.stream().map(o -> (JsonObject) o)
						.filter(o -> o.getInteger("eType", -1) != FolderManager.FILE_TYPE).collect(Collectors.toList());

				CompositeFuture.all(deleteFolders(folders), deleteFiles(files)).setHandler(result -> {
					if (result.succeeded()) {
						JsonArray array = result.result().resultAt(0);
						array.addAll(result.result().resultAt(1));
						handler.handle(new DefaultAsyncResult<>(array));
					} else {
						handler.handle(result.cause() != null ? toError(result.cause())
								: toError("An error occured during deletion"));
					}

				});
			} else {
				handler.handle(toError(body));
			}
		});
	}

	@Override
	public void delete(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject body = msg.result();
				switch (body.getInteger("eType", -1)) {
				case FolderManager.FOLDER_TYPE:
					deleteFolderRecursively(body, user, handler);
					return;
				case FolderManager.FILE_TYPE:
					deleteFiles(Arrays.asList(body)).setHandler(result -> {
						if (result.succeeded()) {
							handler.handle(new DefaultAsyncResult<>(result.result()));
						} else {
							handler.handle(result.cause() != null ? toError(result.cause())
									: toError("An error occured during deletion"));
						}
					});
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
		Future<JsonArray> future = Future.future();
		// Set to avoid duplicate
		Set<String> ids = files.stream().map(o -> o.getString("_id")).collect(Collectors.toSet());
		mongo.update(collection, toJson(QueryBuilder.start("_id").in(ids)),
				new MongoUpdateBuilder().push("deleted", deleted).build(), res -> {
					if (isOk(res.body())) {
						future.complete(new JsonArray(new ArrayList<>(ids)));
					} else {
						future.fail(toErrorStr(res.body()));
					}
				});
		return future;
	}

	private void setDeleteFlagRecursively(JsonObject folder, UserInfos user, boolean deleted,
			Handler<AsyncResult<JsonArray>> handler) {
		String idFolder = folder.getString("_id");
		JsonObject command = AggregationsBuilder.startWithCollection(this.collection)//
				.withMatch(filterBySharedAndOwner(user).and("_id").is(idFolder))//
				.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(), Optional.empty(),
						Optional.empty())//
				.withProjection(new JsonObject().put("_id", 1).put("parents", "$tree._id"))//
				.getCommand();
		mongo.aggregate(command, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				JsonArray rows = body.getJsonObject("result", new JsonObject()).getJsonArray("result");
				if (rows.isEmpty()) {
					handler.handle(new DefaultAsyncResult<>(new JsonArray()));
					return;
				}

				List<JsonObject> all = rows.stream().map(o -> (JsonObject) o).collect(Collectors.toList());

				setDeleteFlag(all, deleted).setHandler(result -> {
					if (result.succeeded()) {
						handler.handle(new DefaultAsyncResult<>(result.result()));
					} else {
						handler.handle(result.cause() != null ? toError(result.cause())
								: toError("An error occured during deletion"));
					}

				});
			} else {
				handler.handle(toError(body));
			}
		});
	}

	private void setDeleteFlag(String id, UserInfos user, Handler<AsyncResult<JsonArray>> handler, boolean deleted) {
		this.info(id, user, msg -> {
			if (msg.succeeded()) {
				JsonObject body = msg.result();
				switch (body.getInteger("eType", -1)) {
				case FolderManager.FOLDER_TYPE:
					setDeleteFlagRecursively(body, user, deleted, handler);
					return;
				case FolderManager.FILE_TYPE:
					setDeleteFlag(Arrays.asList(body), deleted).setHandler(result -> {
						if (result.succeeded()) {
							handler.handle(new DefaultAsyncResult<>(result.result()));
						} else {
							handler.handle(result.cause() != null ? toError(result.cause())
									: toError("An error occured during deletion"));
						}
					});
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
