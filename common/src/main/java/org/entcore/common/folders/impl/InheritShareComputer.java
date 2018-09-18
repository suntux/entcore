package org.entcore.common.folders.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.entcore.common.folders.FolderManager;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class InheritShareComputer {
	private final JsonObject root;
	private final JsonArray rows;
	private Map<String, JsonObject> rowsById;
	private Set<String> idsToProcess;

	public InheritShareComputer(JsonObject root, JsonArray rows) {
		this.root = root;
		this.rows = rows;
	}

	private void mapSharedById() {
		rowsById = new HashMap<>();
		for (int i = 0; i < rows.size(); i++) {
			JsonObject row = rows.getJsonObject(i);
			rowsById.put(row.getString("_id"), row);
		}
	}

	private void doUpdate(Set<String> idsToProcess, String id) throws Exception {
		idsToProcess.remove(id);
		JsonObject current = rowsById.get(id);
		String idParent = current.getString("eParent");
		if (idsToProcess.contains(idParent)) {
			doUpdate(idsToProcess, idParent);
		}
		JsonObject parent = rowsById.get(idParent);
		mergeShared(parent, current);
	}

	private void computeInheritShared() throws Exception {
		idsToProcess = rows.stream().map(o -> (JsonObject) o).map(row -> row.getString("_id"))
				.collect(Collectors.toSet());
		// do not process root (already done)
		idsToProcess.remove(root.getString("_id"));
		while (!idsToProcess.isEmpty()) {
			String id = idsToProcess.iterator().next();
			doUpdate(idsToProcess, id);
		}
	}

	public void compute() throws Exception {
		this.mapSharedById();
		this.computeInheritShared();
	}

	public JsonArray buildMongoBulk() {
		JsonArray operations = new JsonArray();
		rows.stream().map(o -> (JsonObject) o).forEach(row -> {
			operations.add(new JsonObject().put("operation", "update")//
					.put("document",
							new JsonObject().put("$set",
									new JsonObject().put("inheritedShares", row.getJsonArray("inheritedShares"))))//
					.put("criteria", new JsonObject().put("_id", row.getString("_id"))));
		});
		return operations;
	}

	public static void mergeShared(JsonObject parentFolder, JsonObject current) throws Exception {
		if (parentFolder.getInteger("eType", FolderManager.FILE_TYPE) != FolderManager.FOLDER_TYPE) {
			throw new Exception("The parent is not a folder :" + parentFolder.getString("_id"));
		} else {
			JsonArray parentShared = parentFolder.getJsonArray("inheritedShares", new JsonArray());
			JsonArray currentShared = current.getJsonArray("shared", new JsonArray());
			//
			currentShared.addAll(parentShared);
			//
			current.put("inheritedShares", currentShared);
		}
	}
}
