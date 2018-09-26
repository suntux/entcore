package org.entcore.workspace.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.folders.FolderManager;
import org.entcore.common.folders.impl.FolderManagerMongoImpl;
import org.entcore.common.user.UserInfos;

import java.util.List;

/**
 * Created by sinthu on 24/08/18.
 */
public interface WorkspaceServiceI extends FolderManager{

    public static final String WORKSPACE_NAME = "WORKSPACE";

    public void addDocument(final UserInfos user, final float quality, final String name, final String application, final List<String> thumbnail,
                            final JsonObject doc, final JsonObject uploaded, final Handler<AsyncResult<JsonObject>> handler);

    public void updateDocument(final String id, final float quality, final String name, final List<String> thumbnail,
                               final JsonObject uploaded, UserInfos user, final Handler<Message<JsonObject>> handler);

    public void addAfterUpload(final JsonObject uploaded, final JsonObject doc, String name, String application,
                                final List<String> thumbs,  final String ownerId, final String ownerName,
                                    final Handler<AsyncResult<JsonObject>> handler);

    public void updateAfterUpload(final String id, final String name, final JsonObject uploaded,
                                  final List<String> t, final UserInfos user, final Handler<Message<JsonObject>> handler);

    public void documentProperties(final String id, final Handler<JsonObject> handler);

    public void addComment(final String id, final String comment, final UserInfos user, final Handler<JsonObject> handler);

    public void deleteComment(final String id, final String comment, final Handler<JsonObject> handler);

    public void listRevisions(final String id, final Handler<Either<String, JsonArray>> handler);

    public void getRevision(final String documentId, final String revisionId, final Handler<Either<String, JsonObject>> handler);

    public void deleteAllRevisions(final String documentId, final JsonArray alreadyDeleted);

    public void deleteRevision(final String documentId, final String revisionId, final Handler<Either<String, JsonObject>> handler);

    public void emptySize(final UserInfos userInfos, final Handler<Long> emptySizeHandler);

    public void incrementStorage(JsonObject added);

    public void decrementStorage(JsonObject removed);

    public void decrementStorage(JsonObject removed, Handler<Either<String, JsonObject>> handler);

    public void incrementStorage(JsonArray added);

    public void decrementStorage(JsonArray removed);

    public void updateStorage(JsonObject added, JsonObject removed);

    public void updateStorage(JsonArray addeds, JsonArray removeds);

    public void updateStorage(JsonArray addeds, JsonArray removeds, final Handler<Either<String, JsonObject>> handler);

    public void findById(String id, final Handler<JsonObject> handler);

    public void findById(String id, String onwer, final Handler<JsonObject> handler);

    public void findById(String id, JsonObject keys, final Handler<JsonObject> handler);

    public void findById(String id, String onwer,  boolean publicOnly, final Handler<JsonObject> handler);

    public void getQuotaAndUsage(String userId, Handler<Either<String, JsonObject>> handler);

    public void getShareInfos(final String userId, String resourceId, final String acceptLanguage, final String search,
                              final Handler<Either<String, JsonObject>> handler);
    public void deleteFile(final String id, final JsonObject file, Handler<Either<JsonObject, JsonObject>> handler);
}
