package org.entcore.workspace.service.impl;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.workspace.dao.GenericDao;
import org.entcore.workspace.service.QuotaService;
import org.entcore.workspace.service.WorkspaceServiceI;
import org.entcore.workspace.dao.DocumentDao;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.wseduc.webutils.Utils.getOrElse;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class DefaultWorkspaceService extends DocumentDao implements WorkspaceServiceI {

    private final Storage storage;
    private QuotaService quotaService;
    public static final String DOCUMENT_REVISION_COLLECTION = "documentsRevisions";
    private static final JsonObject PROPERTIES_KEYS = new JsonObject().put("name", 1).put("alt", 1).put("legend", 1);
    private static final Logger log = LoggerFactory.getLogger(DefaultWorkspaceService.class);
    private int threshold;
    private String imageResizerAddress;
    private EventBus eb;
    private TimelineHelper notification;


    //TODO: replace mongo direct calls

    public DefaultWorkspaceService(Storage storage, MongoDb mongo, int threshold, String imageResizerAddress, EventBus eb) {
        super(mongo);
        this.storage = storage;
        this.threshold = threshold;
        this.imageResizerAddress = imageResizerAddress;
        this.eb = eb;
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

    public void updateDocument(final String id, final float quality, final String name, final List<String> thumbnail,
                               final JsonObject uploaded, UserInfos user, final Handler<Message<JsonObject>> handler){
        compressImage(uploaded, quality, new Handler<Integer>() {
            @Override
            public void handle(Integer size) {
                JsonObject meta = uploaded.getJsonObject("metadata");
                if (size != null && meta != null) {
                    meta.put("size", size);
                }
                updateAfterUpload(id, name, uploaded, thumbnail, user, handler);
            }
        });
    }

    public void documentProperties(final String id, final Handler<JsonObject> handler){
        this.findById(id, PROPERTIES_KEYS, handler);
    }



    public void addComment(final String id, final String comment, final UserInfos user, final Handler<JsonObject> handler){
        final String commentId = UUID.randomUUID().toString();
        JsonObject query = new JsonObject()
                .put("$push", new JsonObject()
                        .put("comments", new JsonObject()
                                .put("id", commentId)
                                .put("author", user.getUserId())
                                .put("authorName", user.getUsername())
                                .put("posted", MongoDb.formatDate(new Date()))
                                .put("comment", comment)));
        update(id, query, handler);
    }


    public void deleteComment(final String id, final String commentId, final Handler<JsonObject> handler){
        JsonObject query = new JsonObject()
                .put("$pull", new JsonObject()
                        .put("comments", new JsonObject()
                                .put("id", commentId)));

        update(id, query, handler);
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

    public void addAfterUpload(final JsonObject uploaded, final JsonObject doc, String name, String application,
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
                    createThumbnailIfNeeded(DocumentDao.DOCUMENTS_COLLECTION, uploaded,
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


    public void updateAfterUpload(final String id, final String name, final JsonObject uploaded,
                                   final List<String> t, final UserInfos user, final Handler<Message<JsonObject>> handler) {
        findById(id, new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject old) {
                if ("ok".equals(old.getString("status"))) {
                    final JsonObject metadata = uploaded.getJsonObject("metadata");
                    JsonObject set = new JsonObject();
                    final JsonObject doc = new JsonObject();
                    doc.put("name", getOrElse(name, metadata.getString("filename")));
                    final String now = MongoDb.formatDate(new Date());
                    doc.put("modified", now);
                    doc.put("metadata", metadata);
                    doc.put("file", uploaded.getString("_id"));
                    final JsonObject thumbs = old.getJsonObject("result", new JsonObject())
                            .getJsonObject("thumbnails");

                    String query = "{ \"_id\": \"" + id + "\"}";
                    set.put("$set", doc).put("$unset", new JsonObject().put("thumbnails", ""));
                    mongo.update(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject(query), set,
                            new Handler<Message<JsonObject>>() {
                                @Override
                                public void handle(final Message<JsonObject> res) {
                                    String status = res.body().getString("status");
                                    JsonObject result = old.getJsonObject("result");
                                    if ("ok".equals(status) && result != null) {
                                        String userId = user != null ? user.getUserId() : result.getString("owner");
                                        String userName = user != null ? user.getUsername() : result.getString("ownerName");
                                        doc.put("owner", result.getString("owner"));
                                        incrementStorage(doc);
                                        createRevision(id, doc.getString("file"), doc.getString("name"), result.getString("owner"), userId, userName, metadata);
                                        createThumbnailIfNeeded(DocumentDao.DOCUMENTS_COLLECTION,
                                                uploaded, id, thumbs, t, new Handler<Message<JsonObject>>() {
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
                } else if (handler != null) {
                    handler.handle(null);
                }
            }
        });
    }


    private void createRevision(final String id, final String file, final String name, String ownerId, String userId, String userName, JsonObject metadata){
        JsonObject document = new JsonObject();
        document
                .put("documentId", id)
                .put("file", file)
                .put("name", name)
                .put("owner", ownerId)
                .put("userId", userId)
                .put("userName", userName)
                .put("date", MongoDb.now())
                .put("metadata", metadata);

        mongo.save(DOCUMENT_REVISION_COLLECTION, document, MongoDbResult.validResultHandler(new Handler<Either<String,JsonObject>>() {
            public void handle(Either<String, JsonObject> event) {
                if (event.isLeft()) {
                    log.error("[Workspace] Error creating revision " + id + "/" + file + " - " + event.left().getValue());
                }
            }
        }));
    }

    public void listRevisions(final String id, final Handler<Either<String, JsonArray>> handler){
        final QueryBuilder builder = QueryBuilder.start("documentId").is(id);
        mongo.find(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), MongoDbResult.validResultsHandler(handler));
    }

    public void getRevision(final String documentId, final String revisionId, final Handler<Either<String, JsonObject>> handler){
        final QueryBuilder builder = QueryBuilder.start("_id").is(revisionId).and("documentId").is(documentId);
        mongo.findOne(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), MongoDbResult.validResultHandler(handler));
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

    public void deleteRevision(final String documentId, final String revisionId, final Handler<Either<String, JsonObject>> handler){
        final QueryBuilder builder = QueryBuilder.start("_id").is(revisionId).and("documentId").is(documentId);

        //Find revision
        mongo.findOne(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), MongoDbResult.validResultHandler(new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if (event.isRight()) {
                    final JsonObject result = event.right().getValue();
                    final String file = result.getString("file");
                    //Delete file in storage
                    storage.removeFile(file, new Handler<JsonObject>(){
                        public void handle(JsonObject event) {
                            if(event != null && "ok".equals(event.getString("status"))){
                                //Delete revision
                                mongo.delete(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), MongoDbResult.validResultHandler(new Handler<Either<String,JsonObject>>() {
                                    public void handle(Either<String, JsonObject> event) {
                                        if (event.isLeft()) {
                                            log.error("[Workspace] Error deleting revision " + revisionId + " - " + event.left().getValue());
                                            handler.handle(event);
                                        } else {
                                            decrementStorage(result);
                                            handler.handle(event);
                                        }
                                    }
                                }));
                            } else {
                                log.error("[Workspace] Error deleting revision storage file " + revisionId + " ["+file+"] - " + event.getString("message"));
                                handler.handle(new Either.Left<>(event.getString("message")));
                            }
                        }
                    });
                } else {
                    log.error("[Workspace] Error finding revision storage file " + revisionId + " - " + event.left().getValue());
                    handler.handle(event);
                }
            }
        }));
    }



    private void createThumbnailIfNeeded(final String collection, final JsonObject srcFile,
                                         final String documentId, final JsonObject oldThumbnail, final List<String> thumbs,
                                         final Handler<Message<JsonObject>> callback) {
        if (documentId != null && thumbs != null && !documentId.trim().isEmpty() && !thumbs.isEmpty() &&
                srcFile != null && isImage(srcFile) && srcFile.getString("_id") != null) {
            createThumbnails(thumbs, srcFile, collection, documentId, callback);
        } else {
            callback.handle(null);
        }
        if (oldThumbnail != null) {
            for (final String attr: oldThumbnail.fieldNames()) {
                storage.removeFile(oldThumbnail.getString(attr), new Handler<JsonObject>() {
                    @Override
                    public void handle(JsonObject event) {
                        if (!"ok".equals(event.getString("status"))) {
                            log.error("Error removing thumbnail " + oldThumbnail.getString(attr) + " : "
                                    + event.getString("message"));
                        }
                    }
                });
            }
        }
    }

    private void createThumbnails(List<String> thumbs, JsonObject srcFile, final String collection,
                                  final String documentId, final Handler<Message<JsonObject>> callback) {
        Pattern size = Pattern.compile("([0-9]+)x([0-9]+)");
        JsonArray outputs = new fr.wseduc.webutils.collections.JsonArray();
        for (String thumb: thumbs) {
            Matcher m = size.matcher(thumb);
            if (m.matches()) {
                try {
                    int width = Integer.parseInt(m.group(1));
                    int height = Integer.parseInt(m.group(2));
                    if (width == 0 && height == 0) continue;
                    JsonObject j = new JsonObject().put("dest",
                            storage.getProtocol() + "://" + storage.getBucket());
                    if (width != 0) {
                        j.put("width", width);
                    }
                    if (height != 0) {
                        j.put("height", height);
                    }
                    outputs.add(j);
                } catch (NumberFormatException e) {
                    log.error("Invalid thumbnail size.", e);
                }
            }
        }
        if (outputs.size() > 0) {
            JsonObject json = new JsonObject()
                    .put("action", "resizeMultiple")
                    .put("src", storage.getProtocol() + "://" + storage.getBucket() + ":"
                            + srcFile.getString("_id"))
                    .put("destinations", outputs);
            eb.send(imageResizerAddress, json, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
                @Override
                public void handle(Message<JsonObject> event) {
                    JsonObject thumbnails = event.body().getJsonObject("outputs");
                    if ("ok".equals(event.body().getString("status")) && thumbnails != null) {
                        mongo.update(collection, new JsonObject().put("_id", documentId),
                                new JsonObject().put("$set", new JsonObject()
                                        .put("thumbnails", thumbnails)), callback);
                    }
                }
            }));
        } else if (callback != null) {
            callback.handle(null);
        }
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

    private void notifyEmptySpaceIsSmall(String userId) {
        List<String> recipients = new ArrayList<>();
        recipients.add(userId);
        notification.notifyTimeline(new JsonHttpServerRequest(new JsonObject()), WORKSPACE_NAME.toLowerCase() + ".storage",
                null, recipients, null, new JsonObject());
    }




    public void deleteFile(final String id, final JsonObject file, Handler<Either<JsonObject, JsonObject>> handler) {
        final String fileId = file.getString("file");
        Set<Map.Entry<String, Object>> thumbnails = new HashSet<Map.Entry<String, Object>>();
        if(file.containsKey("thumbnails")){
            thumbnails = file.getJsonObject("thumbnails").getMap().entrySet();
        }

        storage.removeFile(fileId,
                new Handler<JsonObject>() {
                    @Override
                    public void handle(JsonObject event) {
                        if (event != null && "ok".equals(event.getString("status"))) {
                            delete(id, new Handler<JsonObject>() {
                                @Override
                                public void handle(final JsonObject result2) {
                                    if ("ok".equals(result2.getString("status"))) {
                                        deleteAllRevisions(id, new fr.wseduc.webutils.collections.JsonArray().add(file));
                                        decrementStorage(file, new Handler<Either<String, JsonObject>>() {
                                            @Override
                                            public void handle(Either<String, JsonObject> event) {
                                                handler.handle(new Either.Right<>(result2));
                                            }
                                        });
                                    } else {
                                        handler.handle(new Either.Left<>(result2));
                                    }
                                }
                            });
                        } else {
                            handler.handle(new Either.Left<>(event));
                        }
                    }
                });

        //Delete thumbnails
        for(final Map.Entry<String, Object> thumbnail : thumbnails){
            storage.removeFile(thumbnail.getValue().toString(), new Handler<JsonObject>(){
                public void handle(JsonObject event) {
                    if (event == null || !"ok".equals(event.getString("status"))) {
                        log.error("Error while deleting thumbnail "+thumbnail);
                    }
                }
            });
        }
    }


    public void setQuotaService(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    public void setNotification(TimelineHelper notification) {
        this.notification = notification;
    }



}
