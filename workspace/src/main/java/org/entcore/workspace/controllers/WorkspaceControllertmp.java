package org.entcore.workspace.controllers;

import com.mongodb.QueryBuilder;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.ETag;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.request.ActionsUtils;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.share.ShareService;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.folders.FolderManager;
import org.entcore.common.folders.impl.FolderManagerMongoImpl;
import org.entcore.workspace.dao.DocumentDao;
import org.entcore.workspace.dao.GenericDao;
import org.entcore.workspace.service.FolderService;
import org.entcore.workspace.service.QuotaService;
import org.entcore.workspace.service.WorkspaceService;
import org.entcore.workspace.service.WorkspaceServiceI;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.wseduc.webutils.Utils.getOrElse;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;
import static org.entcore.workspace.dao.DocumentDao.DOCUMENTS_COLLECTION;

public class WorkspaceControllertmp extends BaseController {

    private EventStore eventStore;
    private QuotaService quotaService;
    private ShareService shareService;
    private WorkspaceServiceI wokspaceService;
    private Storage storage;
    private FolderManager folderManager;


    private enum WokspaceEvent { ACCESS, GET_RESOURCE }





    @Get("/workspace")
    @SecuredAction("workspace.view")
    public void view(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    if (user.getAttribute("storage") != null && user.getAttribute("quota") != null) {
                        renderView(request);
                        eventStore.createAndStoreEvent(WokspaceEvent.ACCESS.name(), request);
                        return;
                    }
                    quotaService.quotaAndUsage(user.getUserId(), new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> r) {
                            if (r.isRight()) {
                                JsonObject j = r.right().getValue();
                                for (String attr : j.fieldNames()) {
                                    UserUtils.addSessionAttribute(eb, user.getUserId(), attr, j.getLong(attr), null);
                                }
                            }
                            renderView(request);
                            eventStore.createAndStoreEvent(WokspaceEvent.ACCESS.name(), request);
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/share/json/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void shareJson(final HttpServerRequest request) {
        final String id = request.params().get("id");
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    shareService.shareInfos(user.getUserId(), id, I18n.acceptLanguage(request),
                            request.params().get("search"), defaultResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/share/json/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void shareJsonSubmit(final HttpServerRequest request) {
        final String id = request.params().get("id");
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        request.setExpectMultipart(true);
        request.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void v) {
                final List<String> actions = request.formAttributes().getAll("actions");
                final String groupId = request.formAttributes().get("groupId");
                final String userId = request.formAttributes().get("userId");
                if (actions == null || actions.isEmpty()) {
                    badRequest(request);
                    return;
                }
                getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos user) {
                        mongo.findOne(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject().put("_id", id), new Handler<Message<JsonObject>>() {
                            @Override
                            public void handle(Message<JsonObject> event) {
                                if ("ok".equals(event.body().getString("status")) && event.body().getJsonObject("result") != null) {
                                    final boolean isFolder = !event.body().getJsonObject("result").containsKey("file");
                                    if(isFolder)
                                        shareFolderAction(request, id, user, actions, groupId, userId, false);
                                    else
                                        shareFileAction(request, id, user, actions, groupId, userId, false);
                                } else {
                                    unauthorized(request);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Put("/share/remove/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void removeShare(final HttpServerRequest request) {
        final String id = request.params().get("id");
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }

        request.setExpectMultipart(true);
        request.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void v) {
                final List<String> actions = request.formAttributes().getAll("actions");
                final String groupId = request.formAttributes().get("groupId");
                final String userId = request.formAttributes().get("userId");
                getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos user) {
                        if (user != null) {
                            mongo.findOne(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject().put("_id", id), new Handler<Message<JsonObject>>() {
                                @Override
                                public void handle(Message<JsonObject> event) {
                                    if ("ok".equals(event.body().getString("status")) && event.body().getJsonObject("result") != null) {
                                        final boolean isFolder = !event.body().getJsonObject("result").containsKey("file");
                                        if (isFolder)
                                            shareFolderAction(request, id, user, actions, groupId, userId, true);
                                        else
                                            shareFileAction(request, id, user, actions, groupId, userId, true);
                                    } else {
                                        unauthorized(request);
                                    }
                                }
                            });
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/share/resource/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void shareResource(final HttpServerRequest request) {
        final String id = request.params().get("id");
        request.pause();
        getUserInfos(eb, request, user -> {
            if (user != null) {
                mongo.findOne(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject().put("_id", id), new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> event) {
                        if ("ok".equals(event.body().getString("status")) && event.body().getJsonObject("result") != null) {
                            request.resume();
                            final JsonObject res = event.body().getJsonObject("result");
                            final boolean isFolder = !res.containsKey("file");
                            if(isFolder)
                                shareFolderAction(request, id, user, res);
                            else
                                shareFileAction(request, id, user, res);
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            } else {
                badRequest(request, "invalid.user");
            }
        });

    }



    @Post("/document")
    @SecuredAction("workspace.document.add")
    public void addDocument(final HttpServerRequest request) {
        float quality = checkQuality(request.params().get("quality"));
        String name = request.params().get("name");
        String application = request.params().get("application");
        List<String> thumbnail = request.params().getAll("thumbnail");



        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {

                    final JsonObject doc = new JsonObject();
                    String now = MongoDb.formatDate(new Date());
                    doc.put("created", now);
                    doc.put("modified", now);
                    doc.put("owner", userInfos.getUserId());
                    doc.put("ownerName", userInfos.getUsername());
                    String application = request.params().get("application");
                    String protectedContent = request.params().get("protected");
                    String publicContent = request.params().get("public");
                    if (application != null && !application.trim().isEmpty() &&
                            "true".equals(protectedContent)) {
                        doc.put("protected", true);
                    } else if (application != null && !application.trim().isEmpty() &&
                            "true".equals(publicContent)) {
                        doc.put("public", true);
                    }
                    request.pause();
                    wokspaceService.emptySize(userInfos, new Handler<Long>() {
                        @Override
                        public void handle(Long emptySize) {
                            request.resume();
                            storage.writeUploadFile(request, emptySize, new Handler<JsonObject>() {
                                @Override
                                public void handle(final JsonObject uploaded) {
                                    if ("ok".equals(uploaded.getString("status"))) {
                                        wokspaceService.addDocument(quality, name, application, thumbnail, doc, uploaded, new Handler<Message<JsonObject>>() {
                                            @Override
                                            public void handle(Message<JsonObject> res) {
                                                if ("ok".equals(res.body().getString("status"))) {
                                                    renderJson(request, res.body(), 201);
                                                } else {
                                                    renderError(request, res.body());
                                                }
                                            }
                                        });
                                    } else {
                                        badRequest(request, uploaded.getString("message"));
                                    }
                                }
                            });
                        }
                    });
                } else {
                    request.response().setStatusCode(401).end();
                }
            }
        });
    }

    private float checkQuality(String quality){
        float q;
        if (quality != null) {
            try {
                q = Float.parseFloat(quality);
            } catch (NumberFormatException e) {
                log.warn(e.getMessage(), e);
                q = 0.8f;
            }
        } else {
            q = 0.8f;
        }

        return q;
    }

    @Post("/folder")
    @SecuredAction("workspace.folder.add")
    public void addFolder(final HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void v) {
                final String name = request.formAttributes().get("name");
                final String parentFolderId = request.formAttributes().get("parentFolderId");
                if (name == null || name.trim().isEmpty()) {
                    badRequest(request);
                    return;
                }
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos userInfos) {

                        Handler<AsyncResult<JsonObject>> handler = new Handler<AsyncResult<JsonObject>>() {
                            @Override
                            public void handle(AsyncResult<JsonObject> event) {
                                if(event.succeeded()){
                                    renderJson(request, event.result(), 201);
                                }else{
                                    unauthorized(request, event.cause().getMessage());
                                }
                            }
                        };


                        if (userInfos != null) {
                            if(parentFolderId == null || parentFolderId.trim().isEmpty())
                                folderManager.createFolder(new JsonObject().put("name", name), userInfos, handler);
                            else
                                folderManager.createFolder(parentFolderId, userInfos, new JsonObject().put("name", name), handler);
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/folder/copy/:id")
    @SecuredAction(value = "workspace.folder.copy", type = ActionType.AUTHENTICATED)
    public void copyFolder(final HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void v) {
                final String id = request.params().get("id");
                final String path = request.formAttributes().get("path");
                final String name = replaceUnderscore(request.formAttributes().get("name"));
                if (id == null || id.trim().isEmpty()) {
                    badRequest(request);
                    return;
                }
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos userInfos) {
                        if (userInfos != null) {
                            emptySize(userInfos, new Handler<Long>() {
                                @Override
                                public void handle(Long emptySize) {
                                    folderService.copy(id, name, path, userInfos, emptySize,
                                            new Handler<Either<String, JsonArray>>() {
                                                @Override
                                                public void handle(Either<String, JsonArray> r) {
                                                    if (r.isRight()) {
                                                        incrementStorage(r.right().getValue());
                                                        renderJson(request, new JsonObject()
                                                                .put("number", r.right().getValue().size()));
                                                    } else {
                                                        badRequest(request, r.left().getValue());
                                                    }
                                                }
                                            });
                                }
                            });
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/folder/move/:id")
    @SecuredAction(value = "workspace.folder.move", type = ActionType.AUTHENTICATED)
    public void moveFolder(final HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void v) {
                final String id = request.params().get("id");
                String p;
                try {
                    p = getOrElse(request.formAttributes().get("path"), "");
                } catch (IllegalStateException e) {
                    p = "";
                }
                final String path = p;
                if (id == null || id.trim().isEmpty()) {
                    badRequest(request);
                    return;
                }
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos userInfos) {
                        if (userInfos != null) {
                            folderService.move(id, path, userInfos, defaultResponseHandler(request));
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/folder/trash/:id")
    @SecuredAction(value = "workspace.folder.trash", type = ActionType.AUTHENTICATED)
    public void moveTrashFolder(final HttpServerRequest request) {
        final String id = request.params().get("id");
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {
                    folderService.trash(id, userInfos, defaultResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/folder/restore/:id")
    @SecuredAction(value = "workspace.folder.trash", type = ActionType.AUTHENTICATED)
    public void restoreFolder(final HttpServerRequest request) {
        final String id = request.params().get("id");
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {
                    folderService.restore(id, userInfos, defaultResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Delete("/folder/:id")
    @SecuredAction(value = "workspace.folder.delete", type = ActionType.AUTHENTICATED)
    public void deleteFolder(final HttpServerRequest request) {
        final String id = request.params().get("id");
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {
                    folderService.delete(id, userInfos, new Handler<Either<String, JsonArray>>() {
                        @Override
                        public void handle(Either<String, JsonArray> r) {
                            if (r.isRight()) {
                                //Delete revisions for each sub-document
                                for(Object obj : r.right().getValue()){
                                    JsonObject item = (JsonObject) obj;
                                    if(item.containsKey("file"))
                                        deleteAllRevisions(item.getString("_id"), new fr.wseduc.webutils.collections.JsonArray().add(item.getString("file")));
                                }
                                //Decrement storage
                                decrementStorage(r.right().getValue());
                                renderJson(request, new JsonObject()
                                        .put("number", r.right().getValue().size()), 204);
                            } else {
                                badRequest(request, r.left().getValue());
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/folders/list")
    @SecuredAction(value = "workspace.folders.list", type = ActionType.AUTHENTICATED)
    public void folders(final HttpServerRequest request) {
        final String path = request.params().get("path");
        final boolean hierarchical = request.params().get("hierarchical") != null;
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {
                    String filter = request.params().get("filter");
                    folderService.list(path, userInfos, hierarchical, filter, arrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/document/:id")
    @SecuredAction(value = "workspace.contrib", type = ActionType.RESOURCE)
    public void updateDocument(final HttpServerRequest request) {
        final String documentId = request.params().get("id");

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    request.pause();
                    documentDao.findById(documentId, new Handler<JsonObject>() {
                        public void handle(JsonObject event) {
                            if (!"ok".equals(event.getString("status"))) {
                                notFound(request);
                                return;
                            }

                            final String userId = event.getJsonObject("result").getString("owner");

                            emptySize(userId, new Handler<Long>() {
                                @Override
                                public void handle(Long emptySize) {
                                    request.resume();
                                    storage.writeUploadFile(request, emptySize,
                                            new Handler<JsonObject>() {
                                                @Override
                                                public void handle(final JsonObject uploaded) {
                                                    if ("ok".equals(uploaded.getString("status"))) {
                                                        compressImage(uploaded, request.params().get("quality"), new Handler<Integer>() {
                                                            @Override
                                                            public void handle(Integer size) {
                                                                JsonObject meta = uploaded.getJsonObject("metadata");
                                                                if (size != null && meta != null) {
                                                                    meta.put("size", size);
                                                                }
                                                                updateAfterUpload(documentId, request.params().get("name"),
                                                                        uploaded, request.params().getAll("thumbnail"), user,
                                                                        new Handler<Message<JsonObject>>() {
                                                                            @Override
                                                                            public void handle(Message<JsonObject> res) {
                                                                                if (res == null) {
                                                                                    request.response().setStatusCode(404).end();
                                                                                } else if ("ok".equals(res.body().getString("status"))) {
                                                                                    renderJson(request, res.body());
                                                                                } else {
                                                                                    renderError(request, res.body());
                                                                                }
                                                                            }
                                                                        });
                                                            }
                                                        });
                                                    } else {
                                                        badRequest(request, uploaded.getString("message"));
                                                    }
                                                }
                                            });
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    @Get("/document/properties/:id")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void getDocumentProperties(final HttpServerRequest request) {
        documentDao.findById(request.params().get("id"), PROPERTIES_KEYS, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                JsonObject result = res.getJsonObject("result");
                if ("ok".equals(res.getString("status")) && result != null) {
                    renderJson(request, result);
                } else {
                    notFound(request);
                }
            }
        });
    }

    @Get("/document/:id")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void getDocument(HttpServerRequest request) {
        getFile(request, documentDao, null, false);
    }

    @Get("/pub/document/:id")
    public void getPublicDocument(HttpServerRequest request) {
        getFile(request, documentDao, null, true);
    }

    private void getFile(final HttpServerRequest request, GenericDao dao, String owner, boolean publicOnly) {
        dao.findById(request.params().get("id"), owner, publicOnly, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                String status = res.getString("status");
                JsonObject result = res.getJsonObject("result");
                String thumbSize = request.params().get("thumbnail");
                if ("ok".equals(status) && result != null) {
                    String file;
                    if (thumbSize != null && !thumbSize.trim().isEmpty()) {
                        file = result.getJsonObject("thumbnails", new JsonObject())
                                .getString(thumbSize, result.getString("file"));
                    } else {
                        file = result.getString("file");
                    }
                    if (file != null && !file.trim().isEmpty()) {
                        boolean inline = inlineDocumentResponse(result, request.params().get("application"));
                        if (inline && ETag.check(request, file)) {
                            notModified(request, file);
                        } else {
                            storage.sendFile(file, result.getString("name"), request,
                                    inline, result.getJsonObject("metadata"));
                        }
                        eventStore.createAndStoreEvent(WorkspaceService.WokspaceEvent.GET_RESOURCE.name(), request,
                                new JsonObject().put("resource", request.params().get("id")));
                    } else {
                        request.response().setStatusCode(404).end();
                    }
                } else {
                    request.response().setStatusCode(404).end();
                }
            }
        });
    }

    @Delete("/document/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void deleteDocument(HttpServerRequest request) {
        deleteFile(request, documentDao, null);
    }

    private void deleteFile(final HttpServerRequest request, final GenericDao dao, String owner) {
        final String id = request.params().get("id");
        dao.findById(id, owner, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                String status = res.getString("status");
                final JsonObject result = res.getJsonObject("result");
                if ("ok".equals(status) && result != null && result.getString("file") != null) {

                    final String file = result.getString("file");
                    Set<Map.Entry<String, Object>> thumbnails = new HashSet<Map.Entry<String, Object>>();
                    if(result.containsKey("thumbnails")){
                        thumbnails = result.getJsonObject("thumbnails").getMap().entrySet();
                    }

                    storage.removeFile(file,
                            new Handler<JsonObject>() {
                                @Override
                                public void handle(JsonObject event) {
                                    if (event != null && "ok".equals(event.getString("status"))) {
                                        dao.delete(id, new Handler<JsonObject>() {
                                            @Override
                                            public void handle(final JsonObject result2) {
                                                if ("ok".equals(result2.getString("status"))) {
                                                    deleteAllRevisions(id, new fr.wseduc.webutils.collections.JsonArray().add(file));
                                                    decrementStorage(result, new Handler<Either<String, JsonObject>>() {
                                                        @Override
                                                        public void handle(Either<String, JsonObject> event) {
                                                            renderJson(request, result2, 204);
                                                        }
                                                    });
                                                } else {
                                                    renderError(request, result2);
                                                }
                                            }
                                        });
                                    } else {
                                        renderError(request, event);
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
                } else {
                    request.response().setStatusCode(404).end();
                }
            }
        });
    }

    @Post("/documents/copy/:ids/:folder")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void copyDocuments(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    copyFiles(request, DocumentDao.DOCUMENTS_COLLECTION, null, user);
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Post("/document/copy/:id/:folder")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void copyDocument(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    emptySize(user, new Handler<Long>() {
                        @Override
                        public void handle(Long emptySize) {
                            copyFile(request, documentDao, null, emptySize);
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/folders")
    @SecuredAction("workspace.document.list.folders")
    public void listFolders(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    final String hierarchical = request.params().get("hierarchical");
                    String relativePath2;
                    try {
                        relativePath2 = URLDecoder.decode(request.params().get("folder"), "UTF-8");
                    } catch (UnsupportedEncodingException | NullPointerException e) {
                        relativePath2 = request.params().get("folder");
                    }
                    final String relativePath = relativePath2;
                    String filter = request.params().get("filter");
                    String query = "{ ";
                    if ("owner".equals(filter)) {
                        query += "\"owner\": \"" + user.getUserId() + "\"";
                    } else if ("shared".equals(filter)) {
                        query += "\"shared\" : { \"$elemMatch\" : " + orSharedElementMatch(user) + "}";
                    } else {
                        query += "\"$or\" : [{ \"owner\": \"" + user.getUserId() +
                                "\"}, {\"shared\" : { \"$elemMatch\" : " + orSharedElementMatch(user) + "}}]";
                    }
                    if (relativePath != null) {
                        query += ", \"folder\" : { \"$regex\" : \"^" + relativePath + "(_|$)\" }}";
                    } else {
                        query += "}";
                    }
                    mongo.distinct(DocumentDao.DOCUMENTS_COLLECTION, "folder", new JsonObject(query),
                            new Handler<Message<JsonObject>>() {
                                @Override
                                public void handle(Message<JsonObject> res) {
                                    if ("ok".equals(res.body().getString("status"))) {
                                        JsonArray values = res.body().getJsonArray("values", new fr.wseduc.webutils.collections.JsonArray("[]"));
                                        JsonArray out = values;
                                        if (hierarchical != null) {
                                            Set<String> folders = new HashSet<String>();
                                            for (Object value : values) {
                                                String v = (String) value;
                                                if (relativePath != null) {
                                                    if (v != null && v.contains("_") &&
                                                            v.indexOf("_", relativePath.length() + 1) != -1 &&
                                                            v.substring(v.indexOf("_", relativePath.length() + 1)).contains("_")) {
                                                        folders.add(v.substring(0, v.indexOf("_", relativePath.length() + 1)));
                                                    } else {
                                                        folders.add(v);
                                                    }
                                                } else {
                                                    if (v != null && v.contains("_")) {
                                                        folders.add(v.substring(0, v.indexOf("_")));
                                                    } else {
                                                        folders.add(v);
                                                    }
                                                }
                                            }
                                            out = new fr.wseduc.webutils.collections.JsonArray(new ArrayList<>(folders));
                                        }
                                        renderJson(request, out);
                                    } else {
                                        renderJson(request, new fr.wseduc.webutils.collections.JsonArray());
                                    }
                                }
                            });
                } else {
                    unauthorized(request);
                }
            }
        });
    }


    @Post("/document/:id/comment")
    @SecuredAction(value = "workspace.comment", type = ActionType.RESOURCE)
    public void commentDocument(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    request.setExpectMultipart(true);
                    request.endHandler(new Handler<Void>() {
                        @Override
                        public void handle(Void v) {
                            String comment = request.formAttributes().get("comment");
                            if (comment != null && !comment.trim().isEmpty()) {
                                final String id = UUID.randomUUID().toString();
                                JsonObject query = new JsonObject()
                                        .put("$push", new JsonObject()
                                                .put("comments", new JsonObject()
                                                        .put("id", id)
                                                        .put("author", user.getUserId())
                                                        .put("authorName", user.getUsername())
                                                        .put("posted", MongoDb.formatDate(new Date()))
                                                        .put("comment", comment)));
                                documentDao.update(request.params().get("id"), query,
                                        new Handler<JsonObject>() {
                                            @Override
                                            public void handle(JsonObject res) {
                                                if ("ok".equals(res.getString("status"))) {
                                                    notifyComment(request, request.params().get("id"), user, false);
                                                    renderJson(request, res.put("id", id));
                                                } else {
                                                    renderError(request, res);
                                                }
                                            }
                                        });
                            } else {
                                badRequest(request);
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Post("/folder/:id/comment")
    @SecuredAction(value = "workspace.comment", type = ActionType.RESOURCE)
    public void commentFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    request.setExpectMultipart(true);
                    request.endHandler(new Handler<Void>() {
                        @Override
                        public void handle(Void v) {
                            String comment = request.formAttributes().get("comment");
                            if (comment != null && !comment.trim().isEmpty()) {
                                final String id = UUID.randomUUID().toString();
                                JsonObject query = new JsonObject()
                                        .put("$push", new JsonObject()
                                                .put("comments", new JsonObject()
                                                        .put("id", id)
                                                        .put("author", user.getUserId())
                                                        .put("authorName", user.getUsername())
                                                        .put("posted", MongoDb.formatDate(new Date()))
                                                        .put("comment", comment)));
                                documentDao.update(request.params().get("id"), query,
                                        new Handler<JsonObject>() {
                                            @Override
                                            public void handle(JsonObject res) {
                                                if ("ok".equals(res.getString("status"))) {
                                                    notifyComment(request, request.params().get("id"), user, true);
                                                    renderJson(request, res.put("id", id));
                                                } else {
                                                    renderError(request, res);
                                                }
                                            }
                                        });
                            } else {
                                badRequest(request);
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Delete("/document/:id/comment/:commentId")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void deleteComment(final HttpServerRequest request) {
        final String id = request.params().get("id");
        final String commentId = request.params().get("commentId");

        QueryBuilder query = QueryBuilder.start("_id").is(id);
        MongoUpdateBuilder queryUpdate = new MongoUpdateBuilder().pull("comments", new JsonObject().put("id", commentId));

        mongo.update(DocumentDao.DOCUMENTS_COLLECTION, MongoQueryBuilder.build(query), queryUpdate.build(), MongoDbResult.validActionResultHandler(defaultResponseHandler(request)));
    }

    @Put("/document/move/:id/:folder")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void moveDocument(final HttpServerRequest request) {
        String folder = getOrElse(request.params().get("folder"), "");
        try {
            folder = URLDecoder.decode(folder, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.warn(e.getMessage(), e);
        }
        moveOne(request, folder, documentDao, null);
    }

    @Put("/document/trash/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void moveTrash(final HttpServerRequest request) {
        moveOne(request, "Trash", documentDao, null);
    }

    private void moveOne(final HttpServerRequest request, final String folder,
                         final GenericDao dao, final String owner) {
        String obj = "{ \"$rename\" : { \"folder\" : \"old-folder\"}}";
        dao.update(request.params().get("id"), new JsonObject(obj), owner, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                if ("ok".equals(res.getString("status"))) {
                    String obj2 = "";
                    if("Trash".equals(folder)){
                        obj2 = "{ \"$set\" : { \"folder\": \"" + folder +"\", "
                                + "\"modified\" : \""+ MongoDb.formatDate(new Date()) + "\"}, "
                                + "\"$unset\": { \"shared\": true }}";
                    } else {
                        obj2 = "{ \"$set\" : { \"folder\": \"" + folder +
                                "\", \"modified\" : \""+ MongoDb.formatDate(new Date()) + "\" }}";
                    }
                    dao.update(request.params().get("id"), new JsonObject(obj2), owner, new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject res) {
                            if ("ok".equals(res.getString("status"))) {
                                renderJson(request, res);
                            } else {
                                renderJson(request, res, 404);
                            }
                        }
                    });
                } else {
                    renderJson(request, res, 404);
                }
            }
        });
    }

    @Put("/documents/move/:ids/:folder")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void moveDocuments(final HttpServerRequest request) {
        final String ids = request.params().get("ids"); // TODO refactor with json in request body
        String tempFolder = getOrElse(request.params().get("folder"), "");
        try {
            tempFolder = URLDecoder.decode(tempFolder, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.warn(e.getMessage(), e);
        }
        final String folder = tempFolder;
        final String cleanedFolder = folder.replaceAll(Pattern.quote("\\"), Matcher.quoteReplacement ("\\\\")).replaceAll(Pattern.quote("\""), Matcher.quoteReplacement ("\\\""));

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    if (ids != null && !ids.trim().isEmpty()) {
                        JsonArray idsArray = new fr.wseduc.webutils.collections.JsonArray(Arrays.asList(ids.split(",")));
                        final String criteria = "{ \"_id\" : { \"$in\" : " + idsArray.encode() + "}}";

                        if (folder != null && !folder.trim().isEmpty()) {

                            //If the document has a parent folder, replicate sharing rights
                            String parentName, parentFolder;
                            if (folder.lastIndexOf('_') < 0) {
                                parentName = folder;
                                parentFolder = folder;
                            } else {
                                String[] splittedPath = folder.split("_");
                                parentName = splittedPath[splittedPath.length - 1];
                                parentFolder = folder;
                            }

                            folderService.getParentRights(parentName, parentFolder, user, new Handler<Either<String, JsonArray>>(){
                                public void handle(Either<String, JsonArray> event) {
                                    final JsonArray parentSharedRights = event.right() == null || event.isLeft() ?
                                            null : event.right().getValue();

                                    String obj = "{ \"$set\" : { \"folder\": \"" + cleanedFolder +
                                            "\", \"modified\" : \""+ MongoDb.formatDate(new Date()) + "\"";
                                    if(parentSharedRights != null && parentSharedRights.size() > 0)
                                        obj += ", \"shared\" : "+parentSharedRights.toString()+" }}";
                                    else
                                        obj += "}, \"$unset\" : { \"shared\": 1 }}";

                                    mongo.update(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject(criteria),
                                            new JsonObject(obj), false, true, new Handler<Message<JsonObject>>() {
                                                @Override
                                                public void handle(Message<JsonObject> r) {
                                                    JsonObject res = r.body();
                                                    if ("ok".equals(res.getString("status"))) {
                                                        renderJson(request, res);
                                                    } else {
                                                        renderJson(request, res, 404);
                                                    }
                                                }
                                            });
                                }
                            });
                        } else {
                            String obj = "{ \"$set\" : { \"modified\" : \""+ MongoDb.formatDate(new Date()) + "\" }, " +
                                    " \"$unset\" : { \"folder\" : 1, \"shared\": 1 }}";

                            mongo.update(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject(criteria),
                                    new JsonObject(obj), false, true, new Handler<Message<JsonObject>>() {
                                        @Override
                                        public void handle(Message<JsonObject> r) {
                                            JsonObject res = r.body();
                                            if ("ok".equals(res.getString("status"))) {
                                                renderJson(request, res);
                                            } else {
                                                renderJson(request, res, 404);
                                            }
                                        }
                                    });
                        }
                    } else {
                        badRequest(request);
                    }
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/documents")
    @SecuredAction("workspace.documents.list")
    public void listDocuments(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    String filter = request.params().get("filter");
                    String query = "{ ";
                    String forApplication = ", \"application\": \"" + getOrElse(request.params()
                            .get("application"), WorkspaceService.WORKSPACE_NAME) + "\"";
                    if ("owner".equals(filter)) {
                        query += "\"owner\": \"" + user.getUserId() + "\"";
                    } else if ("protected".equals(filter)) {
                        query += "\"owner\": \"" + user.getUserId() + "\", \"protected\":true";
                        forApplication = "";
                    } else if ("public".equals(filter)) {
                        query += "\"owner\": \"" + user.getUserId() + "\", \"public\":true";
                        forApplication = "";
                    } else if ("shared".equals(filter)) {
                        query += "\"owner\": { \"$ne\":\"" + user.getUserId() +
                                "\"},\"shared\" : { \"$elemMatch\" : " + orSharedElementMatch(user) + "}";
                    } else {
                        query += "\"$or\" : [{ \"owner\": \"" + user.getUserId() +
                                "\"}, {\"shared\" : { \"$elemMatch\" : " + orSharedElementMatch(user) + "}}]";
                    }

                    if (request.params().get("hierarchical") != null) {
                        query += ", \"file\" : { \"$exists\" : true }" +
                                forApplication + ", \"folder\" : { \"$exists\" : false }}";
                    } else {
                        query += ", \"file\" : { \"$exists\" : true }" + forApplication + "}";
                    }
                    mongo.find(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject(query), new Handler<Message<JsonObject>>() {
                        @Override
                        public void handle(Message<JsonObject> res) {
                            String status = res.body().getString("status");
                            JsonArray results = res.body().getJsonArray("results");
                            if ("ok".equals(status) && results != null) {
                                renderJson(request, results);
                            } else {
                                renderJson(request, new fr.wseduc.webutils.collections.JsonArray());
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/documents/:folder")
    @SecuredAction("workspace.documents.list.by.folder")
    public void listDocumentsByFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    String filter = request.params().get("filter");
                    String query = "{ ";
                    if ("owner".equals(filter)) {
                        query += "\"owner\": \"" + user.getUserId() + "\"";
                    } else if ("shared".equals(filter)) {
                        String ownerId = request.params().get("ownerId");
                        query += "\"shared\" : { \"$elemMatch\" : " + orSharedElementMatch(user) + "}";
                        if(ownerId != null){
                            query += ", \"owner\": \"" + ownerId + "\"";
                        }
                    } else {
                        query += "\"$or\" : [{ \"owner\": \"" + user.getUserId() +
                                "\"}, {\"shared\" : { \"$elemMatch\" : " + orSharedElementMatch(user) + "}}]";
                    }
                    String folder = getOrElse(request.params().get("folder"), "");
                    try {
                        folder = URLDecoder.decode(folder, "UTF-8");
                        folder = folder.replaceAll(Pattern.quote("\\"), Matcher.quoteReplacement ("\\\\")).replaceAll(Pattern.quote("\""), Matcher.quoteReplacement ("\\\""));
                    } catch (UnsupportedEncodingException e) {
                        log.warn(e.getMessage(), e);
                    }
                    String forApplication = getOrElse(request.params()
                            .get("application"), WorkspaceService.WORKSPACE_NAME);
                    if (request.params().get("hierarchical") != null) {
                        query += ", \"file\" : { \"$exists\" : true }, \"application\": \"" +
                                forApplication + "\", \"folder\" : \"" + folder + "\" }";
                    } else {
                        query += ", \"file\" : { \"$exists\" : true }, \"application\": \"" +
                                forApplication + "\", \"folder\" : { \"$regex\" : \"^" +
                                folder + "(_|$)\" }}";
                    }
                    mongo.find(DocumentDao.DOCUMENTS_COLLECTION, new JsonObject(query), new Handler<Message<JsonObject>>() {
                        @Override
                        public void handle(Message<JsonObject> res) {
                            String status = res.body().getString("status");
                            JsonArray results = res.body().getJsonArray("results");
                            if ("ok".equals(status) && results != null) {
                                renderJson(request, results);
                            } else {
                                renderJson(request, new fr.wseduc.webutils.collections.JsonArray());
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/restore/document/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void restoreTrash(HttpServerRequest request) {
        restore(request, documentDao, null);
    }

    private void restore(final HttpServerRequest request, final GenericDao dao, final String to) {
        final String id = request.params().get("id");
        if (id != null && !id.trim().isEmpty()) {
            dao.findById(id, to, new Handler<JsonObject>() {

                @Override
                public void handle(JsonObject res) {
                    if ("ok".equals(res.getString("status"))) {
                        JsonObject doc = res.getJsonObject("result");
                        if (doc.getString("old-folder") != null) {
                            doc.put("folder", doc.getString("old-folder"));
                        } else {
                            doc.remove("folder");
                        }
                        doc.remove("old-folder");
                        dao.update(id, doc, to, new Handler<JsonObject>() {
                            @Override
                            public void handle(JsonObject res) {
                                if ("ok".equals(res.getString("status"))) {
                                    renderJson(request, res);
                                } else {
                                    renderJson(request, res, 404);
                                }
                            }
                        });
                    } else {
                        renderJson(request, res, 404);
                    }
                }
            });
        } else {
            badRequest(request);
        }
    }

    @Get("/workspace/availables-workflow-actions")
    @SecuredAction(value = "workspace.habilitation", type = ActionType.AUTHENTICATED)
    public void getActionsInfos(final HttpServerRequest request) {
        ActionsUtils.findWorkflowSecureActions(eb, request, this);
    }

    @BusAddress("org.entcore.workspace")
    public void workspaceEventBusHandler(final Message<JsonObject> message) {
        switch (message.body().getString("action", "")) {
            case "addDocument" : addDocument(message);
                break;
            case "updateDocument" : updateDocument(message);
                break;
            case "getDocument" : getDocument(message);
                break;
            case "copyDocument" : copyDocument(message);
                break;
            default:
                message.reply(new JsonObject().put("status", "error")
                        .put("message", "invalid.action"));
        }
    }


    @Put("/folder/rename/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void renameFolder(final HttpServerRequest request){
        RequestUtils.bodyToJson(request, pathPrefix + "rename", new Handler<JsonObject>() {
            public void handle(final JsonObject body) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    public void handle(UserInfos userInfos) {
                        if(userInfos != null){
                            final String name = replaceUnderscore(body.getString("name"));
                            String id = request.params().get("id");
                            folderService.rename(id, name, userInfos, defaultResponseHandler(request));
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });

    }

    @Put("/rename/document/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void renameDocument(final HttpServerRequest request){
        RequestUtils.bodyToJson(request, pathPrefix + "rename", new Handler<JsonObject>() {
            public void handle(final JsonObject body) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    public void handle(UserInfos userInfos) {
                        if(userInfos != null){
                            String id = request.params().get("id");

                            final QueryBuilder matcher = QueryBuilder.start("_id").is(id).put("owner").is(userInfos.getUserId()).and("file").exists(true);
                            MongoUpdateBuilder modifier = new MongoUpdateBuilder();
                            if (body.getString("name") != null) modifier.set("name", body.getString("name"));
                            if (body.getString("alt") != null) modifier.set("alt", body.getString("alt"));
                            if (body.getString("legend") != null) modifier.set("legend", body.getString("legend"));

                            mongo.update(DOCUMENTS_COLLECTION, MongoQueryBuilder.build(matcher), modifier.build(), MongoDbResult.validResultHandler(defaultResponseHandler(request)));
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Get("/document/:id/revisions")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void listRevisions(HttpServerRequest request) {
        String id = request.params().get("id");
        final QueryBuilder builder = QueryBuilder.start("documentId").is(id);
        mongo.find(DOCUMENT_REVISION_COLLECTION, MongoQueryBuilder.build(builder), MongoDbResult.validResultsHandler(arrayResponseHandler(request)));
    }

    @Get("/document/:id/revision/:revisionId")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void getRevision(HttpServerRequest request) {
        getRevisionFile(request, request.params().get("id"), request.params().get("revisionId"));
    }

    @Delete("/document/:id/revision/:revisionId")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void deleteRevision(final HttpServerRequest request){
        final String id = request.params().get("id");
        final String revisionId = request.params().get("revisionId");
        final QueryBuilder builder = QueryBuilder.start("_id").is(revisionId).and("documentId").is(id);

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
                                            badRequest(request, event.left().getValue());
                                        } else {
                                            decrementStorage(result);
                                            renderJson(request, event.right().getValue());
                                        }
                                    }
                                }));
                            } else {
                                log.error("[Workspace] Error deleting revision storage file " + revisionId + " ["+file+"] - " + event.getString("message"));
                                badRequest(request, event.getString("message"));
                            }
                        }
                    });
                } else {
                    log.error("[Workspace] Error finding revision storage file " + revisionId + " - " + event.left().getValue());
                    notFound(request, event.left().getValue());
                }
            }
        }));
    }

}
