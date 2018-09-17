package org.entcore.workspace.service.impl;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.workspace.service.QuotaService;
import org.entcore.workspace.service.WorkspaceServiceI;
import org.entcore.workspace.dao.DocumentDao;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.wseduc.webutils.Utils.getOrElse;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class DefaultWorkspaceService implements WorkspaceServiceI {

    private final Storage storage;
    private QuotaService quotaService;
    public static final String WORKSPACE_NAME = "WORKSPACE";
    public static final String DOCUMENT_REVISION_COLLECTION = "documentsRevisions";
    private static final Logger log = LoggerFactory.getLogger(DefaultWorkspaceService.class);
    private final MongoDb mongo;
    private int threshold;


    public DefaultWorkspaceService(Storage storage, MongoDb mongo, int threshold) {
        this.storage = storage;
        this.mongo = mongo;
        this.threshold = threshold;
    }

    public void addDocument(final float quality, final String name, final String application, final List<String> thumbnail,
                     final JsonObject doc, final JsonObject uploaded, final Handler<Message<JsonObject>> handler) {
        compressImage(uploaded, quality, new Handler<Integer>() {
            @Override
            public void handle(Integer size) {
                JsonObject meta = uploaded.getJsonObject("metadata");
                if (size != null && meta != null) {
                    meta.put("size", size);
                }
                addAfterUpload(uploaded, doc, name, application, thumbnail, handler);
            }
        });

    }

    private void compressImage(JsonObject srcFile, float quality, final Handler<Integer> handler) {
        if (!isImage(srcFile)) {
            handler.handle(null);
            return;
        }
        JsonObject json = new JsonObject()
                .put("action", "compress")
                .put("quality", quality)
                .put("src", storage.getProtocol() + "://" + storage.getBucket() + ":" + srcFile.getString("_id"))
                .put("dest", storage.getProtocol() + "://" + storage.getBucket() + ":" + srcFile.getString("_id"));
        eb.send(imageResizerAddress, json, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                Integer size = event.body().getInteger("size");
                handler.handle(size);
            }
        }));
    }

    private boolean isImage(JsonObject doc) {
        if (doc == null) {
            return false;
        }
        JsonObject metadata = doc.getJsonObject("metadata");
        return metadata != null && (
                "image/jpeg".equals(metadata.getString("content-type")) ||
                        "image/gif".equals(metadata.getString("content-type")) ||
                        "image/png".equals(metadata.getString("content-type")) ||
                        "image/tiff".equals(metadata.getString("content-type"))
        );
    }

    private void addAfterUpload(final JsonObject uploaded, final JsonObject doc, String name, String application,
                                final List<String> thumbs,
                                final Handler<Message<JsonObject>> handler) {
        doc.put("name", getOrElse(name, uploaded.getJsonObject("metadata")
                .getString("filename"), false));
        doc.put("metadata", uploaded.getJsonObject("metadata"));
        doc.put("file", uploaded.getString("_id"));
        doc.put("application", getOrElse(application, WORKSPACE_NAME)); // TODO check if application name is valid
        log.debug(doc.encodePrettily());
        mongo.save(DocumentDao.DOCUMENTS_COLLECTION, doc, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(final Message<JsonObject> res) {
                if ("ok".equals(res.body().getString("status"))) {
                    incrementStorage(doc);
                    createRevision(res.body().getString("_id"), uploaded.getString("_id"), doc.getString("name"), doc.getString("owner"), doc.getString("owner"), doc.getString("ownerName"), doc.getJsonObject("metadata"));
                    createThumbnailIfNeeded(mongoCollection, uploaded,
                            res.body().getString("_id"), null, thumbs, new Handler<Message<JsonObject>>() {
                                @Override
                                public void handle(Message<JsonObject> event) {
                                    if (handler != null) {
                                        handler.handle(res);
                                    }
                                }
                            });
                } else if (handler != null) {
                    handler.handle(res);
                }
            }
        });
    }

    public void emptySize(final UserInfos userInfos, final Handler<Long> emptySizeHandler) {
        try {
            long quota = Long.valueOf(userInfos.getAttribute("quota").toString());
            long storage = Long.valueOf(userInfos.getAttribute("storage").toString());
            emptySizeHandler.handle(quota - storage);
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
                                UserUtils.addSessionAttribute(eb, userInfos.getUserId(), attr, j.getLong(attr), null);
                            }
                            emptySizeHandler.handle(quota - storage);
                        }
                    }
                }
            });
        }
    }


    public void incrementStorage(JsonObject added) {
        updateStorage(new fr.wseduc.webutils.collections.JsonArray().add(added), null);
    }

    public void decrementStorage(JsonObject removed) {
        updateStorage(null, new fr.wseduc.webutils.collections.JsonArray().add(removed));
    }

    public void decrementStorage(JsonObject removed, Handler<Either<String, JsonObject>> handler) {
        updateStorage(null, new fr.wseduc.webutils.collections.JsonArray().add(removed), handler);
    }

    public void incrementStorage(JsonArray added) {
        updateStorage(added, null);
    }

    public void decrementStorage(JsonArray removed) {
        updateStorage(null, removed);
    }

    public void updateStorage(JsonObject added, JsonObject removed) {
        updateStorage(new fr.wseduc.webutils.collections.JsonArray().add(added), new JsonArray().add(removed));
    }

    public void updateStorage(JsonArray addeds, JsonArray removeds) {
        updateStorage(addeds, removeds, null);
    }

    public void updateStorage(JsonArray addeds, JsonArray removeds, final Handler<Either<String, JsonObject>> handler) {
        Map<String, Long> sizes = new HashMap<>();
        if (addeds != null) {
            for (Object o : addeds) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject added = (JsonObject) o;
                Long size = added.getJsonObject("metadata", new JsonObject()).getLong("size", 0l);
                String userId = (added.containsKey("to")) ? added.getString("to") : added.getString("owner");
                if (userId == null) {
                    log.info("UserId is null when update storage size");
                    log.info(added.encode());
                    continue;
                }
                Long old = sizes.get(userId);
                if (old != null) {
                    size += old;
                }
                sizes.put(userId, size);
            }
        }

        if (removeds != null) {
            for (Object o : removeds) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject removed = (JsonObject) o;
                Long size = removed.getJsonObject("metadata", new JsonObject()).getLong("size", 0l);
                String userId = (removed.containsKey("to")) ? removed.getString("to") : removed.getString("owner");
                if (userId == null) {
                    log.info("UserId is null when update storage size");
                    log.info(removed.encode());
                    continue;
                }
                Long old = sizes.get(userId);
                if (old != null) {
                    old -= size;
                } else {
                    old = -1l * size;
                }
                sizes.put(userId, old);
            }
        }

        for (final Map.Entry<String, Long> e : sizes.entrySet()) {
            quotaService.incrementStorage(e.getKey(), e.getValue(), threshold, new Handler<Either<String, JsonObject>>() {
                @Override
                public void handle(Either<String, JsonObject> r) {
                    if (r.isRight()) {
                        JsonObject j = r.right().getValue();
                        UserUtils.addSessionAttribute(eb, e.getKey(), "storage", j.getLong("storage"), null);
                        if (j.getBoolean("notify", false)) {
                            notifyEmptySpaceIsSmall(e.getKey());
                        }
                    } else {
                        log.error(r.left().getValue());
                    }
                    if (handler != null) {
                        handler.handle(r);
                    }
                }
            });
        }
    }

    public void deleteAllRevisions(final String documentId, final JsonArray alreadyDeleted){
        final QueryBuilder builder = QueryBuilder.start("documentId").is(documentId);
        JsonObject keys = new JsonObject();

        mongo.find(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), new JsonObject(), keys, MongoDbResult.validResultsHandler(new Handler<Either<String,JsonArray>>() {
            public void handle(Either<String, JsonArray> event) {
                if (event.isRight()) {
                    final JsonArray results = event.right().getValue();
                    final JsonArray ids = new fr.wseduc.webutils.collections.JsonArray();
                    for(Object obj : results){
                        JsonObject json = (JsonObject) obj;
                        String id = json.getString("file");
                        if (id != null && !alreadyDeleted.contains(id)) {
                            ids.add(id);
                        }
                    }
                    storage.removeFiles(ids, new Handler<JsonObject>() {
                        public void handle(JsonObject event) {
                            if(event != null && "ok".equals(event.getString("status"))){
                                //Delete revisions
                                mongo.delete(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), MongoDbResult.validResultHandler(new Handler<Either<String,JsonObject>>() {
                                    public void handle(Either<String, JsonObject> event) {
                                        if (event.isLeft()) {
                                            log.error("[Workspace] Error deleting revisions for document " + documentId + " - " + event.left().getValue());
                                        } else {
                                            for(Object obj : results){
                                                JsonObject result = (JsonObject) obj;
                                                if(!alreadyDeleted.contains(result.getString("file")))
                                                    decrementStorage(result);
                                            }
                                        }
                                    }
                                }));
                            } else {
                                log.error("[Workspace] Error deleting revision storage files for document " + documentId + " "+ids+" - " + event.getString("message"));
                            }
                        }
                    });
                } else {
                    log.error("[Workspace] Error finding revision for document " + documentId + " - " + event.left().getValue());
                }
            }
        }));
    }
}
