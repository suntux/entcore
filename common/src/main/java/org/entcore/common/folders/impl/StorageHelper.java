package org.entcore.common.folders.impl;

import static org.entcore.common.folders.impl.QueryHelper.isOk;
import static org.entcore.common.folders.impl.QueryHelper.toErrorStr;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.storage.Storage;
import org.entcore.common.utils.StringUtils;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class StorageHelper {
	static Set<String> getThumbnailsFilesIds(JsonObject jsonDocument) {
		Set<String> thumbIds = Stream.of(jsonDocument.getJsonObject("thumbnails", null))
				.filter(thumbs -> thumbs != null)//
				.flatMap(thumbs -> thumbs.stream()
						.map(entry -> entry.getValue() != null ? entry.getValue().toString() : null))//
				.filter(file -> !StringUtils.isEmpty(file)).collect(Collectors.toSet());
		return thumbIds;
	}

	static void replaceAll(JsonObject jsonDocument, Map<String, String> oldFileIdForNewFileId) {
		// replace file id
		Set<String> fileIds = getFileId(jsonDocument);
		for (String fileId : fileIds) {
			if (!oldFileIdForNewFileId.containsKey(fileId)) {
				throw new IllegalStateException("Could not found newFileId of the file:" + fileId);
			}
			setFileId(jsonDocument, oldFileIdForNewFileId.get(fileId));
		}
		// replace thumb values
		fileIds = getThumbnailsFilesIds(jsonDocument);
		for (String fileId : fileIds) {
			if (!oldFileIdForNewFileId.containsKey(fileId)) {
				throw new IllegalStateException("Could not found newFileId of the file:" + fileId);
			}
			replaceThumbnailFileId(jsonDocument, fileId, oldFileIdForNewFileId.get(fileId));
		}
	}

	static Set<String> getFileId(JsonObject jsonDocument) {
		Set<String> filesIds = Stream.of(jsonDocument.getString("file", null))
				.filter(file -> !StringUtils.isEmpty(file)).collect(Collectors.toSet());
		return filesIds;
	}

	static void setFileId(JsonObject jsonDocument, String fileId) {
		jsonDocument.put("file", fileId);
	}

	static void replaceThumbnailFileId(JsonObject jsonDocument, String fileId, String newFileId) {
		for (String key : jsonDocument.fieldNames()) {
			String value = jsonDocument.getString(key);
			if (value != null && newFileId != null && value.equals(newFileId)) {
				jsonDocument.put(key, newFileId);
			}
		}
	}

	static List<String> getListOfFileIds(JsonObject jsonDocument) {
		List<String> listOfFilesIds = new ArrayList<>(getFileId(jsonDocument));
		listOfFilesIds.addAll(getThumbnailsFilesIds(jsonDocument));
		return listOfFilesIds;
	}

	static List<String> getListOfFileIds(Collection<JsonObject> files) {
		List<String> listOfFilesIds = files.stream().flatMap(f -> getListOfFileIds(f).stream())
				.collect(Collectors.toList());
		return listOfFilesIds;
	}

	static Future<Map<String, String>> copyFileInStorage(Storage storage, Collection<JsonObject> originals) {
		// only files are duplicated in storage
		Set<JsonObject> files = originals.stream().filter(o -> o.getInteger("eType", -1) == FolderManager.FILE_TYPE)
				.collect(Collectors.toSet());
		@SuppressWarnings("rawtypes")
		List<Future> copyFutures = new ArrayList<>();
		List<String> fileIds = StorageHelper.getListOfFileIds(files);
		for (String fId : fileIds) {
			Future<Entry<String, String>> future = Future.future();
			storage.copyFile(fId, res -> {
				if (isOk(res)) {
					JsonObject result = res.getJsonObject("result", new JsonObject());
					future.complete(new AbstractMap.SimpleEntry<String, String>(fId, result.getString("_id")));
				} else {
					future.fail(toErrorStr(res));
				}
			});
			copyFutures.add(future);
		}
		return CompositeFuture.all(copyFutures).map(copyStorageEvent -> {
			@SuppressWarnings("unchecked")
			Map<String, String> oldFileIdForNewFileId = copyStorageEvent.list().stream()
					.map(pair -> (Entry<String, String>) pair)
					.collect(Collectors.toMap(pair -> pair.getKey(), pair -> pair.getValue()));
			return oldFileIdForNewFileId;
		});
	}
}
