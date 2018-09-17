package org.entcore.workspace.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;

/**
 * Created by sinthu on 24/08/18.
 */
public interface WorkspaceServiceI {

    public void addDocument(final float quality, final String name, final String application, final List<String> thumbnail,
                            final JsonObject doc, final JsonObject uploaded, final Handler<Message<JsonObject>> handler);
    public void emptySize(final UserInfos userInfos, final Handler<Long> emptySizeHandler);

    public void incrementStorage(JsonObject added);

    public void decrementStorage(JsonObject removed);

    public void decrementStorage(JsonObject removed, Handler<Either<String, JsonObject>> handler);

    public void incrementStorage(JsonArray added);

    public void decrementStorage(JsonArray removed);

    public void updateStorage(JsonObject added, JsonObject removed);

    public void updateStorage(JsonArray addeds, JsonArray removeds);

    public void updateStorage(JsonArray addeds, JsonArray removeds, final Handler<Either<String, JsonObject>> handler);

    public void deleteAllRevisions(final String documentId, final JsonArray alreadyDeleted);
}
