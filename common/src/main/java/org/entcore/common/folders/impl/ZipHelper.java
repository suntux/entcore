package org.entcore.common.folders.impl;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.Deflater;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.storage.Storage;
import org.entcore.common.utils.Zip;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ZipHelper {
	static class ZipContext {
		String basePath;
		String baseName;
		String zipFullPath;
		String zipName;
		Map<String, String> namesByIds = new HashMap<>();
		Map<String, String> pathByFileId = new HashMap<>();
		Set<String> folders = new HashSet<>();
	}

	private void buildMapping(JsonArray rows, ZipContext context) {
		for (int i = 0; i < rows.size(); i++) {
			JsonObject row = rows.getJsonObject(i);
			context.namesByIds.put(row.getString("_id"), row.getString("name", "undefined"));
		}

		for (int i = 0; i < rows.size(); i++) {
			JsonObject row = rows.getJsonObject(i);
			if (row.getInteger("eType", -1) == FolderManager.FILE_TYPE) {
				String name = row.getString("name", "undefined");
				String fileId = row.getString("file");
				JsonArray parentIds = row.getJsonArray("parents");
				String folderPath = parentIds.stream().map(o -> ((String) o))//
						.map(parentId -> context.namesByIds.get(parentId))//
						.reduce("", (t, u) -> t + File.pathSeparator + u);
				String fullPath = Paths.get(context.basePath, folderPath, name).normalize().toString();

				context.folders.add(folderPath);
				context.pathByFileId.put(fileId, fullPath);
			}
		}
	}

	private CompositeFuture mkdirs(ZipContext context) {
		@SuppressWarnings("rawtypes")
		List<Future> futures = new ArrayList<>();
		for (String path : context.folders) {
			Future<Void> future = Future.future();
			futures.add(future);
			fs.mkdirs(path, future.completer());
		}
		return CompositeFuture.all(futures);
	}

	private CompositeFuture copyFiles(ZipContext context) {
		@SuppressWarnings("rawtypes")
		List<Future> futures = new ArrayList<>();
		for (String fileId : context.pathByFileId.keySet()) {
			Future<JsonObject> future = Future.future();
			futures.add(future);
			String filePath = context.pathByFileId.get(fileId);
			storage.writeFsFile(fileId, filePath, res -> {
				if ("ok".equals(res.getString("status"))) {
					future.complete(res);
				} else {
					future.fail(res.getString("error"));
				}
			});
		}
		return CompositeFuture.all(futures);
	}

	private Future<JsonObject> createZip(ZipContext context) {
		Future<JsonObject> future = Future.future();
		Zip.getInstance().zipFolder(context.basePath, context.zipName, true, Deflater.NO_COMPRESSION, res -> {
			if ("ok".equals(res.body().getString("status"))) {
				future.complete(res.body());
			} else {
				future.fail(res.body().getString("message"));
			}
		});
		return future;
	}

	private final FileSystem fs;
	private final Storage storage;

	public ZipHelper(Storage storage, FileSystem fs) {
		this.fs = fs;
		this.storage = storage;
	}

	public Future<ZipContext> build(Optional<JsonObject> root, JsonArray rows) {
		Future<Void> future = Future.future();
		ZipContext context = new ZipContext();
		context.baseName = root.isPresent() ? root.get().getString("name", "archive") : "archive";
		context.basePath = Paths.get(System.getProperty("java.io.tmpdir"), context.baseName).normalize().toString();
		context.zipName = context.baseName + ".zip";
		context.zipFullPath = Paths.get(context.basePath, context.zipName).normalize().toString();
		this.buildMapping(rows, context);
		return this.mkdirs(context).compose(res -> {
			if (res.failed()) {
				future.fail(res.cause());
			}
		}, this.copyFiles(context)).compose(res -> {
			if (res.failed()) {
				future.fail(res.cause());
			}
		}, this.createZip(context)).map(res -> context);
	}

	public Future<Void> send(HttpServerRequest req, ZipContext context) {
		Future<Void> future = Future.future();
		final HttpServerResponse resp = req.response();
		resp.putHeader("Content-Disposition", "attachment; filename=\"" + context.zipName + "\"");
		resp.putHeader("Content-Type", "application/octet-stream");
		resp.putHeader("Content-Description", "File Transfer");
		resp.putHeader("Content-Transfer-Encoding", "binary");
		resp.sendFile(context.zipFullPath, future.completer());
		return future;
	}

	public Future<Void> buildAndSend(JsonObject root, JsonArray rows, HttpServerRequest req) {
		return this.build(Optional.ofNullable(root), rows).compose(res -> {
			return this.send(req, res);
		});
	}

	public Future<Void> buildAndSend(JsonArray rows, HttpServerRequest req) {
		return this.build(Optional.empty(), rows).compose(res -> {
			return this.send(req, res);
		});
	}
}
