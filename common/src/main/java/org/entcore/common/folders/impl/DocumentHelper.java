package org.entcore.common.folders.impl;

import java.text.ParseException;
import java.util.Date;
import java.util.Optional;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.utils.StringUtils;

import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DocumentHelper {
	public static String getId(JsonObject doc) {
		return doc.getString("_id");
	}

	public static String getOwner(JsonObject doc) {
		return doc.getString("owner");
	}

	public static String getOwnerName(JsonObject doc) {
		return doc.getString("ownerName");
	}

	public static String getModifiedStr(JsonObject doc) {
		return doc.getString("modified");
	}

	public static Optional<Date> getModified(JsonObject doc) {
		try {
			return Optional.ofNullable(MongoDb.parseDate(doc.getString("modified")));
		} catch (ParseException e) {
			return Optional.empty();
		}
	}

	public static String getName(JsonObject doc) {
		return doc.getString("name");
	}

	public static String getParent(JsonObject doc) {
		return doc.getString("eParent");
	}

	public static boolean hasParent(JsonObject doc) {
		return !StringUtils.isEmpty(getParent(doc));
	}

	public static int getType(JsonObject doc) {
		return doc.getInteger("eType", -1);
	}

	public static boolean isFile(JsonObject doc) {
		return getType(doc) == FolderManager.FILE_TYPE;
	}

	public static long getFileSize(JsonObject doc) {
		JsonObject metadata = doc.getJsonObject("metadata");
		if (metadata != null) {
			return metadata.getLong("size", 0l);
		}
		return 0;
	}

	public static long getFileSize(JsonArray docs) {
		return docs.stream().map(o -> (JsonObject) o)//
				.map(o -> DocumentHelper.getFileSize(o)).reduce(0l, (a1, a2) -> a1 + a2);
	}
}
