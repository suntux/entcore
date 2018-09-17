package org.entcore.workspace.service.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.workspace.service.QuotaService;
import org.entcore.workspace.service.WorkspaceServiceI;
import org.entcore.workspace.dao.DocumentDao;


import java.util.List;

import static fr.wseduc.webutils.Utils.getOrElse;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class DefaultWorkspaceService implements WorkspaceServiceI {

    private final Storage storage;
    private QuotaService quotaService;
    public static final String WORKSPACE_NAME = "WORKSPACE";


    public DefaultWorkspaceService(Storage storage) {
        this.storage = storage;
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
}
