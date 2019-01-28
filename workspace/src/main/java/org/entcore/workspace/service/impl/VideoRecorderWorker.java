/*
 * Copyright © "Open Digital Education", 2017
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 */

package org.entcore.workspace.service.impl;

import fr.wseduc.webutils.collections.PersistantBuffer;
import fr.wseduc.webutils.data.ZLib;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.entcore.common.user.UserUtils;
import org.vertx.java.busmods.BusModBase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;


public class VideoRecorderWorker extends BusModBase implements Handler<Message<JsonObject>> {

	private Storage storage;
	private WorkspaceHelper workspaceHelper;
	private final Map<String, PersistantBuffer> buffers = new HashMap<>();
	private final Map<String, MessageConsumer<byte[]>> consumers = new HashMap<>();
	private final Set<String> disabledCompression = new HashSet<>();
	private final Map<String, AtomicInteger> counters = new HashMap<>();

	@Override
	public void start() {
		super.start();
		storage = new StorageFactory(vertx, config).getStorage();
		workspaceHelper = new WorkspaceHelper(vertx.eventBus(), storage);
		vertx.eventBus().localConsumer(VideoRecorderWorker.class.getSimpleName(), this);
	}

	@Override
	public void handle(Message<JsonObject> message) {
		final String action = message.body().getString("action", "");
		final String id = message.body().getString("id");
		switch (action) {
			case "open" :
				open(id, message);
				break;
			case "cancel":
				cancel(id, message);
				break;
			case "save":
				save(id, message);
				break;
			case "rawdata":
				disableCompression(id, message);
				break;
		}
	}

	private void disableCompression(String id, Message<JsonObject> message) {
		disabledCompression.add(id);
		sendOK(message);
	}

	private void save(final String id, final Message<JsonObject> message) {
		final JsonObject session = message.body().getJsonObject("session");
		final String name = message.body().getString("name", "Capture " + System.currentTimeMillis()) + ".webm";
		final PersistantBuffer buffer = buffers.get(id);
		if (buffer != null) {
			buffer.getBuffer(buf -> {
				try {
					vertx.fileSystem().writeFile("/tmp/video/" + name, buf.result(), event -> {
						if (event.failed()) {
							logger.error("error writing video file", event.cause());
						}
					});
//					storage.writeBuffer(id, toMp3(toWav(buf.result())), "audio/mp3", name, new Handler<JsonObject>() {
//						@Override
//						public void handle(JsonObject f) {
//							if ("ok".equals(f.getString("status"))) {
//								workspaceHelper.addDocument(f,
//										UserUtils.sessionToUserInfos(session), name, "mediaLibrary",
//										true, new fr.wseduc.webutils.collections.JsonArray(), handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
//											@Override
//											public void handle(Message<JsonObject> event) {
//												if ("ok".equals(event.body().getString("status"))) {
//													sendOK(message);
//												} else {
//													sendError(message, "workspace.add.error");
//												}
//											}
//										}));
//							} else {
//								sendError(message, "write.file.error");
//							}
//							cancel(id, null);
//						}
//					});
				} catch (Exception e) {
					sendError(message, "encoding.file.error");
					cancel(id, null);
					logger.error("Error writing audio capture.", e);
				}
			});
		} else {
			sendError(message, "missing.buffer.error");
		}
	}

	private void cancel(String id, Message<JsonObject> message) {
		disabledCompression.remove(id);
		PersistantBuffer buffer = buffers.remove(id);
		if (buffer != null) {
			buffer.clear();
		}
		MessageConsumer<byte[]> consumer = consumers.remove(id);
		if (consumer != null) {
			consumer.unregister();
		}
		if (message != null) {
			sendOK(message);
		}
	}

	private void open(final String id, final Message<JsonObject> message) {
		//final String path = "/tmp/video/" + id;
		Handler<Message<byte[]>> handler = chunk -> {
			try {
//				vertx.fileSystem().writeFile("/tmp/video/" + id +"/" +
//						counters.get(id).incrementAndGet() + ".webm", Buffer.buffer(chunk.body()), res -> {
//					chunk.reply(new JsonObject().put("status", res.succeeded() ? "ok" : "error"));
//				});
				final PersistantBuffer buf = buffers.get(id);
				final Buffer tmp;
//				if (disabledCompression.contains(id)) {
					tmp = Buffer.buffer(chunk.body());
//				} else {
//					tmp = Buffer.buffer(ZLib.decompress(chunk.body()));
//				}
				logger.info("chunk size : " + chunk.body().length);
				if (buf != null) {
					buf.appendBuffer(tmp);
				} else {
					PersistantBuffer pb = new PersistantBuffer(vertx, tmp, id);
					pb.exceptionHandler(event -> logger.error("Error with PersistantBuffer " + id, event));
					buffers.put(id, pb);
				}
				chunk.reply(new JsonObject().put("status", "ok"));
			} catch (Exception e) {
				logger.error("Error receiving chunk.", e);
				chunk.reply(new JsonObject().put("status", "error")
						.put("message", "audioworker.chunk.error"));
			}
		};
		//vertx.fileSystem().mkdirsBlocking(path);
		counters.put(id, new AtomicInteger(0));
		MessageConsumer<byte[]> consumer = vertx.eventBus().localConsumer(VideoRecorderWorker.class.getSimpleName() + id, handler);
		consumers.put(id, consumer);
		sendOK(message);
	}

}
