package org.entcore.workspace.controllers;

import com.mongodb.QueryBuilder;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.mongodb.MongoDb;
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
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.folders.ElementQuery;
import org.entcore.common.folders.ElementShareOperations;
import org.entcore.common.http.request.ActionsUtils;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.folders.FolderManager;
import org.entcore.workspace.Workspace;
import org.entcore.workspace.service.WorkspaceService;
import org.entcore.workspace.service.WorkspaceServiceI;
import org.vertx.java.core.http.RouteMatcher;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.wseduc.webutils.Utils.getOrElse;
import static fr.wseduc.webutils.request.RequestUtils.bodyToJson;
import static org.entcore.common.http.response.DefaultResponseHandler.*;
import static org.entcore.common.user.UserUtils.getUserInfos;

public class WorkspaceController extends BaseController {

    private EventStore eventStore;
    private WorkspaceServiceI workspaceService;
    private TimelineHelper notification;
    private Storage storage;

    private enum WokspaceEvent { ACCESS, GET_RESOURCE }

    public WorkspaceController(Storage storage, WorkspaceServiceI workspaceService) {
        this.storage = storage;
        this.workspaceService = workspaceService;
    }

    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        notification = new TimelineHelper(vertx, eb, config);
        eventStore = EventStoreFactory.getFactory().getEventStore(Workspace.class.getSimpleName());
        post("/documents/copy/:ids", "copyDocuments");
        put("/documents/move/:ids", "moveDocuments");
    }


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
                    workspaceService.getQuotaAndUsage(user.getUserId(), new Handler<Either<String, JsonObject>>() {
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
                    workspaceService.getShareInfos(user.getUserId(), id, I18n.acceptLanguage(request),
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

                if ((groupId == null || groupId.isEmpty()) && (userId == null || userId.isEmpty())) {
                    badRequest(request);
                    return;
                }

                getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos user) {
                        String sharedMethod = "org-entcore-workspace-controllers-WorkspaceController|shareJsonSubmit";
                        Handler<AsyncResult<JsonObject>> handler = new Handler<AsyncResult<JsonObject>>() {
                            @Override
                            public void handle(AsyncResult<JsonObject> event) {
                                if (event.succeeded()) {
                                    JsonObject n = event.result().getJsonObject("notify-timeline");
                                    if (n != null) {
                                        notifyShare(request, id, user, new fr.wseduc.webutils.collections.JsonArray().add(n));
                                    }
                                    renderJson(request, event.result());
                                } else {
                                    JsonObject error = new JsonObject().put("error", event.cause().getMessage());
                                    renderJson(request, error, 400);
                                }
                            }
                        };
                        if(groupId != null){
                            workspaceService.share(id, ElementShareOperations.addShareGroup(sharedMethod, user, groupId, actions), handler);
                        }else{
                            workspaceService.share(id, ElementShareOperations.addShareUser(sharedMethod, user, userId, actions), handler);
                        }
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
                if (actions == null || actions.isEmpty()) {
                    badRequest(request);
                    return;
                }

                if ((groupId == null || groupId.isEmpty()) && (userId == null || userId.isEmpty())) {
                    badRequest(request);
                    return;
                }
                getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos user) {
                        if (user != null) {
                            String removeMethod = "org-entcore-workspace-controllers-WorkspaceController|removeShare";
                            if (groupId != null)
                                workspaceService.share(id, ElementShareOperations.removeShareGroup(removeMethod, user, groupId, actions), asyncDefaultResponseHandler(request));
                            else
                                workspaceService.share(id, ElementShareOperations.removeShareUser(removeMethod, user, userId, actions), asyncDefaultResponseHandler(request));

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
        getUserInfos(eb, request, user -> {
            if (user != null) {
                RequestUtils.bodyToJson(request, body -> {
                    String shareMethod = "org-entcore-workspace-controllers-WorkspaceController|shareResource";
                    Handler<AsyncResult<JsonObject>> handler = new Handler<AsyncResult<JsonObject>>() {
                        @Override
                        public void handle(AsyncResult<JsonObject> event) {
                            if (event.succeeded()) {
                                JsonArray n = event.result().getJsonArray("notify-timeline-array");
                                if (n != null) {
                                    notifyShare(request, id, user, n);
                                }
                                renderJson(request, event.result());
                            } else {
                                JsonObject error = new JsonObject().put("error", event.cause().getMessage());
                                renderJson(request, error, 400);
                            }
                        }
                    };
                    workspaceService.share(id, ElementShareOperations.addShareObject(shareMethod, user, body), handler);
                });
            } else {
                unauthorized(request);
            }
        });

    }


    @Post("/document")
    @SecuredAction("workspace.document.add")
    public void addDocument(final HttpServerRequest request) {

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {

                    final JsonObject doc = new JsonObject();
                    float quality = checkQuality(request.params().get("quality"));
                    String name = request.params().get("name");
                    List<String> thumbnail = request.params().getAll("thumbnail");
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
                    workspaceService.emptySize(userInfos, new Handler<Long>() {
                        @Override
                        public void handle(Long emptySize) {
                            request.resume();
                            storage.writeUploadFile(request, emptySize, new Handler<JsonObject>() {
                                @Override
                                public void handle(final JsonObject uploaded) {
                                    if ("ok".equals(uploaded.getString("status"))) {
                                        workspaceService.addDocument(userInfos, quality, name, application, thumbnail, doc, uploaded, asyncDefaultResponseHandler(request, 201));
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

                        if (userInfos != null) {
                            if(parentFolderId == null || parentFolderId.trim().isEmpty())
                                workspaceService.createFolder(new JsonObject().put("name", name), userInfos, asyncDefaultResponseHandler(request, 201));
                            else
                                workspaceService.createFolder(parentFolderId, userInfos, new JsonObject().put("name", name), asyncDefaultResponseHandler(request, 201));
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
                final String parentFolderId = request.formAttributes().get("parentFolderId");
                final String name = request.formAttributes().get("name");
                if (id == null || id.trim().isEmpty()) {
                    badRequest(request);
                    return;
                }
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos userInfos) {
                        if (userInfos != null) {
                            workspaceService.copy(id, Optional.ofNullable(parentFolderId), userInfos, new Handler<AsyncResult<JsonArray>>() {
                                @Override
                                public void handle(AsyncResult<JsonArray> event) {
                                    if(event.succeeded()){
                                        workspaceService.incrementStorage(event.result());
                                        renderJson(request, new JsonObject()
                                                .put("number", event.result().size()));
                                    }else{
                                        badRequest(request, event.cause().getMessage());
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

    @Put("/folder/move/:id")
    @SecuredAction(value = "workspace.folder.move", type = ActionType.AUTHENTICATED)
    public void moveFolder(final HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void v) {
                final String id = request.params().get("id");
                final String parentFolderId = request.formAttributes().get("parentFolderId");

                if (id == null || id.trim().isEmpty()) {
                    badRequest(request);
                    return;
                }
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos userInfos) {
                        if (userInfos != null) {
                            workspaceService.move(id, parentFolderId, userInfos, asyncDefaultResponseHandler(request));
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
                    workspaceService.trash(id, userInfos, asyncArrayResponseHandler(request));
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
                    workspaceService.restore(id, userInfos, asyncArrayResponseHandler(request));
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
                    workspaceService.delete(id, userInfos, new Handler<AsyncResult<JsonArray>>() {
                        @Override
                        public void handle(AsyncResult<JsonArray> event) {
                            if(event.succeeded()){
                                //Delete revisions for each sub-document
                                for(Object obj : event.result()){
                                    JsonObject item = (JsonObject) obj;
                                    if(item.containsKey("file"))
                                        workspaceService.deleteAllRevisions(item.getString("_id"), new fr.wseduc.webutils.collections.JsonArray().add(item.getString("file")));
                                }
                                workspaceService.decrementStorage(event.result());
                            }else{
                                badRequest(request, event.cause().getMessage());
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
        final String parentId = request.params().get("parentId");
        final boolean hierarchical = request.params().get("hierarchical") != null;
        if(parentId == null || parentId.trim().isEmpty()){
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {
                    boolean shared = "shared".equals(request.params().get("filter"));
                    ElementQuery query = new ElementQuery(shared);
                    query.setHierarchical(hierarchical);
                    query.setParentId(parentId);
                    query.setApplication(WorkspaceService.WORKSPACE_NAME);
                    workspaceService.findByQuery(query, userInfos, asyncArrayResponseHandler(request));
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
                    float quality = checkQuality(request.params().get("quality"));
                    String name = request.params().get("name");
                    List<String> thumbnail = request.params().getAll("thumbnail");
                    request.pause();
                    workspaceService.findById(documentId, new Handler<JsonObject>() {
                        public void handle(JsonObject event) {
                            if (!"ok".equals(event.getString("status"))) {
                                notFound(request);
                                return;
                            }

                            final String userId = event.getJsonObject("result").getString("owner");

                            workspaceService.emptySize(user, new Handler<Long>() {
                                @Override
                                public void handle(Long emptySize) {
                                    request.resume();
                                    storage.writeUploadFile(request, emptySize,
                                            new Handler<JsonObject>() {
                                                @Override
                                                public void handle(final JsonObject uploaded) {
                                                    if ("ok".equals(uploaded.getString("status"))) {
                                                        workspaceService.updateDocument(documentId, quality, name, thumbnail, uploaded, user, new Handler<Message<JsonObject>>() {
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
                                                    } else {
                                                        badRequest(request, uploaded.getString("message"));
                                                    }
                                                }
                                            });
                                }
                            });
                        }
                    });
                }else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/document/properties/:id")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void getDocumentProperties(final HttpServerRequest request) {
        workspaceService.documentProperties(request.params().get("id"), new Handler<JsonObject>() {
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
        getFile(request, null, false);
    }

    @Get("/pub/document/:id")
    public void getPublicDocument(HttpServerRequest request) {
        getFile(request, null, true);
    }

    private void getFile(final HttpServerRequest request, String owner, boolean publicOnly) {
        workspaceService.findById(request.params().get("id"), owner, publicOnly, new Handler<JsonObject>() {
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
                        eventStore.createAndStoreEvent(WokspaceEvent.GET_RESOURCE.name(), request,
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

    private boolean inlineDocumentResponse(JsonObject doc, String application) {
        JsonObject metadata = doc.getJsonObject("metadata");
        String storeApplication = doc.getString("application");
        return metadata != null && !"WORKSPACE".equals(storeApplication) && (
                "image/jpeg".equals(metadata.getString("content-type")) ||
                        "image/gif".equals(metadata.getString("content-type")) ||
                        "image/png".equals(metadata.getString("content-type")) ||
                        "image/tiff".equals(metadata.getString("content-type")) ||
                        "image/vnd.microsoft.icon".equals(metadata.getString("content-type")) ||
                        "image/svg+xml".equals(metadata.getString("content-type")) ||
                        ("application/octet-stream".equals(metadata.getString("content-type")) && application != null)
        );
    }

    @Delete("/document/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void deleteDocument(HttpServerRequest request) {
        final String id = request.params().get("id");
        workspaceService.findById(id, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                String status = res.getString("status");
                final JsonObject result = res.getJsonObject("result");
                if ("ok".equals(status) && result != null && result.getString("file") != null) {
                    workspaceService.deleteFile(id, result, new Handler<Either<JsonObject, JsonObject>>() {
                        @Override
                        public void handle(Either<JsonObject, JsonObject> event) {
                            if(event.isRight()){
                                renderJson(request, event.right().getValue(), 204);
                            }else{
                                renderError(request, event.left().getValue());
                            }
                        }
                    });
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
                    bodyToJson(request, new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject body) {
                            JsonArray ids = body.getJsonArray("id");
                            workspaceService.copyAll(ids.getList(), Optional.ofNullable(request.params().get("folder")), user, new Handler<AsyncResult<JsonArray>>() {
                                @Override
                                public void handle(AsyncResult<JsonArray> event) {
                                    if(event.succeeded()){
                                        renderJson(request, new JsonObject()
                                                .put("number", event.result().size()));
                                    }else{
                                        badRequest(request, event.cause().getMessage());
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

    @Post("/document/copy/:id/:folder")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void copyDocument(final HttpServerRequest request) {
        String id = request.params().get("id");
        String folder = request.params().get("folder");
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    workspaceService.copy(id, Optional.ofNullable(folder), user, new Handler<AsyncResult<JsonArray>>() {
                        @Override
                        public void handle(AsyncResult<JsonArray> event) {
                            if(event.succeeded()){
                                renderJson(request, new JsonObject()
                                        .put("number", event.result().size()));
                            }else{
                                badRequest(request, event.cause().getMessage());
                            }

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
                    ElementQuery query = new ElementQuery(false);
                    String filter = request.params().get("filter");
                    String parentId = request.params().get("folder");

                    if ("shared".equals(filter))
                        query.setShared(true);
                    if(parentId != null && !parentId.trim().isEmpty())
                        query.setParentId(parentId);
                    boolean hierarchical = request.params().get("hierarchical") != null;
                    query.setApplication(getOrElse(request.params().get("application"), WorkspaceService.WORKSPACE_NAME));
                    query.setHierarchical(hierarchical);
                    query.setType(FolderManager.FOLDER_TYPE);

                    workspaceService.findByQuery(query, user, event -> {
                        if(event.succeeded()){
                            Set<String> folders = new HashSet<String>();
                            for (Object value : event.result()) {
                                JsonObject v = (JsonObject) value;
                                folders.add(v.getString("name"));
                            }
                        }else{
                            renderError(request, new JsonObject().put("errors", event.cause().getMessage()));
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
        comment(request, false);
    }

    @Post("/folder/:id/comment")
    @SecuredAction(value = "workspace.comment", type = ActionType.RESOURCE)
    public void commentFolder(final HttpServerRequest request) {
        comment(request, true);
    }

    private void comment(final HttpServerRequest request, boolean isFolder){
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
                                workspaceService.addComment(request.params().get("id"), comment, user, new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject res) {
                                        if ("ok".equals(res.getString("status"))) {
                                            notifyComment(request, request.params().get("id"), user, isFolder);
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

        workspaceService.deleteComment(id, commentId, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                if ("ok".equals(res.getString("status"))) {
                    noContent(request);
                } else {
                    renderError(request, res);
                }
            }
        });
    }

    @Put("/document/move/:id/:folder")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void moveDocument(final HttpServerRequest request) {
        String folderId = request.params().get("folder");
        if (folderId == null || folderId.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    workspaceService.move(request.params().get("id"), folderId, user, asyncDefaultResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/document/trash/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void moveTrash(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    workspaceService.trash(request.params().get("id"), user, asyncArrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/documents/move/:ids/:folder")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void moveDocuments(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    bodyToJson(request, new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject body) {
                            JsonArray ids = body.getJsonArray("id");
                            workspaceService.moveAll(ids.getList(), request.params().get("folder"), user, new Handler<AsyncResult<JsonArray>>() {
                                @Override
                                public void handle(AsyncResult<JsonArray> event) {
                                    if(event.succeeded()){
                                        renderJson(request, new JsonObject()
                                                .put("number", event.result().size()));
                                    }else{
                                        badRequest(request, event.cause().getMessage());
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

    @Get("/documents")
    @SecuredAction("workspace.documents.list")
    public void listDocuments(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    ElementQuery query = new ElementQuery(false);
                    String filter = request.params().get("filter");
                    boolean hierarchical = request.params().get("hierarchical") != null;
                    query.setApplication(getOrElse(request.params().get("application"), WorkspaceService.WORKSPACE_NAME));
                    query.setHierarchical(hierarchical);
                    query.setType(FolderManager.FILE_TYPE);
                    if ("shared".equals(filter)) {
                        query.setShared(true);
                    } else {
                        Set<String> filterQuery = new HashSet<String>();
                        filterQuery.add(filter);
                        query.setVisibilities(new HashSet<>(filterQuery));
                    }

                    workspaceService.findByQuery(query, user, asyncArrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/documents/:folder")
    @SecuredAction("workspace.documents.list.by.folder")
    public void listDocumentsByFolder(final HttpServerRequest request) {
        String folderId = request.params().get("folder");
        if (folderId == null || folderId.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    ElementQuery query = new ElementQuery(false);
                    String filter = request.params().get("filter");
                    if ("shared".equals(filter))
                        query.setShared(true);
                    boolean hierarchical = request.params().get("hierarchical") != null;
                    query.setApplication(getOrElse(request.params().get("application"), WorkspaceService.WORKSPACE_NAME));
                    query.setHierarchical(hierarchical);
                    query.setParentId(folderId);
                    query.setType(FolderManager.FILE_TYPE);


                    workspaceService.findByQuery(query, user, asyncArrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/trash")
    @SecuredAction("workspace.documents.list")
    public void listTrashDocuments(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

            @Override
            public void handle(UserInfos user) {
                if (user != null && user.getUserId() != null) {
                    ElementQuery query = new ElementQuery(false);
                    query.setTrash(true);
                    workspaceService.findByQuery(query, user, asyncArrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/restore/document/:id")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void restoreTrash(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos userInfos) {
                if (userInfos != null) {
                    workspaceService.restore(request.params().get("id"), userInfos, asyncArrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
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

    private void getDocument(final Message<JsonObject> message) {
        workspaceService.findById(message.body().getString("id"), new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject res) {
                message.reply(res);
            }
        });
    }

    private void addDocument(final Message<JsonObject> message) {
        JsonObject uploaded = message.body().getJsonObject("uploaded");
        JsonObject doc = message.body().getJsonObject("document");
        String ownerId = doc.getString("owner");
        String ownerName = doc.getString("ownerName");
        if (doc == null || uploaded == null || ownerId == null || ownerName == null) {
            message.reply(new JsonObject().put("status", "error")
                    .put("message", "missing.attribute"));
            return;
        }
        String name = message.body().getString("name");
        String application = message.body().getString("application");
        JsonArray t = message.body().getJsonArray("thumbs", new fr.wseduc.webutils.collections.JsonArray());
        List<String> thumbs = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            thumbs.add(t.getString(i));
        }
        workspaceService.addAfterUpload(uploaded, doc,  name, application, thumbs, ownerId, ownerName, new Handler<AsyncResult<JsonObject>>() {
            @Override
            public void handle(AsyncResult<JsonObject> m) {
                if(m.succeeded()){
                    message.reply(m.result());
                }else{
                    message.reply(new JsonObject().put("status", "error")
                            .put("message", m.cause().getMessage()));
                }
            }
        });
    }

    private void updateDocument(final Message<JsonObject> message) {
        JsonObject uploaded = message.body().getJsonObject("uploaded");
        String id = message.body().getString("id");
        if (uploaded == null || id == null || id.trim().isEmpty()) {
            message.reply(new JsonObject().put("status", "error")
                    .put("message", "missing.attribute"));
            return;
        }
        String name = message.body().getString("name");
        JsonArray t = message.body().getJsonArray("thumbs", new fr.wseduc.webutils.collections.JsonArray());
        List<String> thumbs = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            thumbs.add(t.getString(i));
        }
        workspaceService.updateAfterUpload(id, name, uploaded, thumbs, null, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> m) {
                if (m != null) {
                    message.reply(m.body());
                }
            }
        });
    }

    public void copyDocument(final Message<JsonObject> message) {
        workspaceService.emptySize(message.body().getJsonObject("user").getString("userId"), new Handler<Long>() {
            @Override
            public void handle(Long emptySize) {
                //TODO : use unsafe copyfile
                copyFile(message.body(), documentDao, emptySize, new Handler<JsonObject>() {
                    @Override
                    public void handle(JsonObject res) {
                        if ("ok".equals(res.getString("status")))
                            message.reply(res);
                        else
                            message.fail(500, res.getString("status"));
                    }
                });
            }
        });
    }



    @Put("/folder/rename/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void renameFolder(final HttpServerRequest request){
        RequestUtils.bodyToJson(request, pathPrefix + "rename", new Handler<JsonObject>() {
            public void handle(final JsonObject body) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    public void handle(UserInfos userInfos) {
                        if(userInfos != null){
                            final String name = body.getString("name");
                            String id = request.params().get("id");
                            workspaceService.rename(id, name, userInfos, asyncDefaultResponseHandler(request));
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
                            final String name = body.getString("name");
                            //TODO : check the usage of alt and legend
                            if (body.getString("name") != null) modifier.set("name", body.getString("name"));
                            if (body.getString("alt") != null) modifier.set("alt", body.getString("alt"));
                            if (body.getString("legend") != null) modifier.set("legend", body.getString("legend"));

                            workspaceService.rename(id, name, userInfos, asyncDefaultResponseHandler(request));

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
        workspaceService.listRevisions(id, arrayResponseHandler(request));
    }

    @Get("/document/:id/revision/:revisionId")
    @SecuredAction(value = "workspace.read", type = ActionType.RESOURCE)
    public void getRevision(HttpServerRequest request) {
        String documentId = request.params().get("id");
        String revisionId = request.params().get("revisionId");
        if (revisionId == null || revisionId.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        workspaceService.getRevision(documentId, revisionId, new Handler<Either<String,JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if(event.isLeft()){
                    notFound(request);
                    return;
                }
                JsonObject result = event.right().getValue();
                String file = result.getString("file");
                if (file != null && !file.trim().isEmpty()) {
                    if (ETag.check(request, file)) {
                        notModified(request, file);
                    } else {
                        storage.sendFile(file, result.getString("name"), request, false, result.getJsonObject("metadata"));
                    }
                    eventStore.createAndStoreEvent(WokspaceEvent.GET_RESOURCE.name(), request,
                            new JsonObject().put("resource", documentId));
                } else {
                    notFound(request);
                }
            }
        });
    }


    @Delete("/document/:id/revision/:revisionId")
    @SecuredAction(value = "workspace.manager", type = ActionType.RESOURCE)
    public void deleteRevision(final HttpServerRequest request){
        final String id = request.params().get("id");
        final String revisionId = request.params().get("revisionId");

        workspaceService.deleteRevision(id, revisionId, defaultResponseHandler(request));

    }

    private void notifyComment(final HttpServerRequest request, final String id, final UserInfos user, final boolean isFolder) {
        final JsonObject params = new JsonObject()
                .put("userUri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
                .put("userName", user.getUsername())
                .put("appPrefix", pathPrefix+"/workspace");

        final String notifyName = WorkspaceService.WORKSPACE_NAME.toLowerCase() + "." + (isFolder ? "comment-folder" : "comment");

        workspaceService.findById(id, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject event) {
                if ("ok".equals(event.getString("status")) && event.getJsonObject("result") != null) {
                    final JsonObject document = event.getJsonObject("result");
                    params.put("resourceName", document.getString("name", ""));
                    String parentId = document.getString("eParent");

                    //Send the notification to the shared network
                    getRecipients(user, document.getJsonArray("shared", new fr.wseduc.webutils.collections.JsonArray()), new Handler<List<String>>() {
                        public void handle(List<String> recipients) {
                            JsonObject sharedNotifParams = params.copy();

                            if(parentId != null){
                                sharedNotifParams.put("resourceUri", pathPrefix + "/workspace#/shared/folder/" + parentId);
                            } else {
                                sharedNotifParams.put("resourceUri", pathPrefix + "/workspace#/shared");
                            }

                            // don't send comment with share uri at owner
                            final String o = document.getString("owner");
                            if(o != null && recipients.contains(o)) {
                                recipients.remove(o);
                            }
                            notification.notifyTimeline(request, notifyName, user, recipients, id, sharedNotifParams);
                        }
                    });

                    //If the user commenting is not the owner, send a notification to the owner
                    if(!document.getString("owner").equals(user.getUserId())){
                        JsonObject ownerNotif = params.copy();
                        ArrayList<String> ownerList = new ArrayList<>();
                        ownerList.add(document.getString("owner"));

                        if(parentId != null){
                            ownerNotif.put("resourceUri", pathPrefix + "/workspace#/folder/" + parentId);
                        } else {
                            ownerNotif.put("resourceUri", pathPrefix + "/workspace");
                        }
                        notification.notifyTimeline(request, notifyName, user, ownerList, id, null, ownerNotif, true);
                    }

                } else {
                    log.error("Unable to send timeline notification : missing name on resource " + id);
                }
            }
        });
    }

    private void notifyShare(final HttpServerRequest request, final String resource, final UserInfos user, JsonArray sharedArray) {
        getRecipients(user, sharedArray, event -> sendNotify(request, resource, user, event));
    }

    private void getRecipients(final UserInfos user, JsonArray sharedArray, Handler<List<String>> handler) {
        final List<String> recipients = new ArrayList<>();
        final AtomicInteger remaining = new AtomicInteger(sharedArray.size());
        for (Object j : sharedArray) {
            JsonObject json = (JsonObject) j;
            String userId = json.getString("userId");
            if (userId != null) {
                if(!userId.equals(user.getUserId()))
                    recipients.add(userId);
                remaining.getAndDecrement();
            } else {
                String groupId = json.getString("groupId");
                if (groupId != null) {
                    UserUtils.findUsersInProfilsGroups(groupId, eb, user.getUserId(), false, new Handler<JsonArray>() {
                        @Override
                        public void handle(JsonArray event) {
                            if (event != null) {
                                for(Object o: event) {
                                    if (!(o instanceof JsonObject)) continue;
                                    JsonObject j = (JsonObject) o;
                                    String id = j.getString("id");
                                    log.debug(id);
                                    if(!id.equals(user.getUserId()))
                                        recipients.add(id);
                                }
                            }
                            if (remaining.decrementAndGet() < 1) {
                                handler.handle(recipients);
                            }
                        }
                    });
                }
            }
        }
        if (remaining.get() < 1) {
            handler.handle(recipients);
        }
    }


    private void sendNotify(final HttpServerRequest request, final String resource, final UserInfos user, final List<String> recipients) {
        final JsonObject params = new JsonObject()
                .put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
                .put("username", user.getUsername())
                .put("appPrefix", pathPrefix + "/workspace")
                .put("doc", "share");


        workspaceService.findById(resource, new JsonObject().put("name", 1).put("eType", 1), new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject event) {
                if ("ok".equals(event.getString("status")) && event.getJsonObject("result") != null) {
                    String resourceName = event.getJsonObject("result").getString("name", "");
                    boolean isFolder = event.getJsonObject("result").getInteger("name") == FolderManager.FOLDER_TYPE;
                    final JsonObject pushNotif = new JsonObject();
                    final String i18nPushNotifBody;

                    if (isFolder) {
                        params.put("resourceUri", pathPrefix + "/workspace#/shared/folder/" + resource);
                        pushNotif.put("title", "push.notif.folder.share");
                        i18nPushNotifBody = user.getUsername() + " " + I18n.getInstance().translate("workspace.shared.folder",
                                getHost(request), I18n.acceptLanguage(request)) + " : ";
                    } else {
                        params.put("resourceUri", pathPrefix + "/document/" + resource);
                        pushNotif.put("title", "push.notif.file.share");
                        i18nPushNotifBody = user.getUsername() + " " + I18n.getInstance().translate("workspace.shared.document",
                                getHost(request), I18n.acceptLanguage(request)) + " : ";
                    }

                    final String notificationName = workspaceService.WORKSPACE_NAME.toLowerCase() + "." + (isFolder ? "share-folder" : "share");
                    params.put("resourceName", resourceName);
                    params.put("pushNotif", pushNotif.put("body", i18nPushNotifBody + resourceName));
                    notification.notifyTimeline(request, notificationName, user, recipients, resource, params);
                } else {
                    log.error("Unable to send timeline notification : missing name on resource " + resource);
                }
            }
        });
    }
}
