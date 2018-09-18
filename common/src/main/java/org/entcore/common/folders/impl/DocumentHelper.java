package org.entcore.common.folders.impl;

import org.entcore.common.folders.FolderManager;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class DocumentHelper {
	static int getType(JsonObject doc) {
		return doc.getInteger("eType", -1);
	}

	static boolean isFile(JsonObject doc) {
		return getType(doc) == FolderManager.FILE_TYPE;
	}

	static long getFileSize(JsonObject doc) {
		JsonObject metadata = doc.getJsonObject("metadata");
		if (metadata != null) {
			return metadata.getLong("size", 0l);
		}
		return 0;
	}

	static long getFileSize(JsonArray docs) {
		return docs.stream().map(o -> (JsonObject) o)//
				.map(o -> DocumentHelper.getFileSize(o)).reduce(0l, (a1, a2) -> a1 + a2);
	}
}
