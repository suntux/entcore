package org.entcore.workspace.service;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
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
}
