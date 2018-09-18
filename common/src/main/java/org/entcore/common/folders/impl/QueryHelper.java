package org.entcore.common.folders.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.DateUtils;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

import fr.wseduc.mongodb.AggregationsBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class QueryHelper {
	protected final MongoDb mongo = MongoDb.getInstance();
	private final String collection;

	public QueryHelper(String collection) {
		this.collection = collection;
	}

	static JsonObject toJson(QueryBuilder queryBuilder) {
		return MongoQueryBuilder.build(queryBuilder);
	}

	static boolean isOk(JsonObject body) {
		return "ok".equals(body.getString("status"));
	}

	static String toErrorStr(JsonObject body) {
		return body.getString("error");
	}

	public static class FileQueryBuilder {
		QueryBuilder builder = new QueryBuilder();
		private boolean excludeDeleted;

		private boolean onlyDeleted;

		FileQueryBuilder filterBySharedAndOwner(UserInfos user) {
			List<DBObject> groups = new ArrayList<>();
			groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
			for (String gpId : user.getGroupsIds()) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}
			builder.or(QueryBuilder.start("owner").is(user.getUserId()).get(), QueryBuilder.start("shared")
					.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
			return this;
		}

		FileQueryBuilder filterByInheritShareAndOwner(UserInfos user) {
			List<DBObject> groups = new ArrayList<>();
			groups.add(builder.and("userId").is(user.getUserId()).get());
			for (String gpId : user.getGroupsIds()) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}
			builder.or(QueryBuilder.start("owner").is(user.getUserId()).get(), QueryBuilder.start("inheritedShares")
					.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
			return this;
		}

		public FileQueryBuilder withParent(String id) {
			builder.and("eParent").is(id);
			return this;
		}

		public FileQueryBuilder withId(String id) {
			builder.and("_id").is(id);
			return this;
		}

		public FileQueryBuilder withIds(Collection<String> ids) {
			builder.and("_id").in(ids);
			return this;
		}

		public FileQueryBuilder withExcludeDeleted() {
			excludeDeleted = true;
			builder.and("deleted").notEquals(true);
			return this;
		}

		public FileQueryBuilder withOnlyDeletedFile() {
			onlyDeleted = true;
			builder.and("deleted").is(true);
			return this;
		}

		public FileQueryBuilder withFileType(final int type) {
			builder.and("eType").is(type);
			return this;
		}

		public FileQueryBuilder withNameMatch(final String pattern) {
			builder.and("name").regex(Pattern.compile("^" + pattern + "(_|$)"));
			return this;
		}

		public boolean isExcludeDeleted() {
			return excludeDeleted;
		}

		public boolean isOnlyDeleted() {
			return onlyDeleted;
		}

		public QueryBuilder build() {
			return builder;
		}
	}

	FileQueryBuilder queryBuilder() {
		return new FileQueryBuilder();
	}

	Future<JsonArray> listRecursive(FileQueryBuilder query) {
		Future<JsonArray> future = Future.future();
		//
		QueryBuilder match = query.withFileType(FolderManager.FOLDER_TYPE).build();
		// first : match only folder regarding criterias
		AggregationsBuilder agg = AggregationsBuilder.startWithCollection(this.collection);
		agg = agg.withMatch(match)
				// then build the graph from root folder
				.withGraphLookup("$eParent", "eParent", "_id", "tree", Optional.empty(), Optional.empty(),
						Optional.empty());
		if (query.isExcludeDeleted()) {
			// exclude deleted files => graphlookup reintroduce deleted children
			agg = agg.withMatch(queryBuilder().withExcludeDeleted().build());
		}
		if (query.isOnlyDeleted()) {
			agg = agg.withMatch(queryBuilder().withOnlyDeletedFile().build());
		}
		// finally project name and parent
		agg.withProjection(new JsonObject().put("_id", 1).put("name", 1).put("eType", 1).put("file", 1)
				.put("thumbnails", 1).put("eParent", 1).put("shared", 1).put("metadata", 1).put("parents", "$tree._id"))//
				.getCommand();
		JsonObject command = agg.getCommand();
		mongo.aggregate(command, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(body.getJsonObject("result", new JsonObject()).getJsonArray("result"));
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<JsonObject> findById(String id) {
		Future<JsonObject> future = Future.future();
		mongo.findOne(collection, toJson(QueryBuilder.start("_id").is(id)), message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(body.getJsonObject("result"));
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<JsonObject> findOne(FileQueryBuilder query) {
		Future<JsonObject> future = Future.future();
		mongo.findOne(collection, toJson(query.build()), message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(body.getJsonObject("result"));
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<JsonArray> findAll(FileQueryBuilder query) {
		Future<JsonArray> future = Future.future();
		mongo.find(collection, toJson(query.build()), message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(body.getJsonArray("result"));
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<JsonObject> insert(JsonObject file) {
		Future<JsonObject> future = Future.future();
		mongo.insert(collection, file, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(body.getJsonObject("result"));
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<JsonArray> insertAll(JsonArray files) {
		Future<JsonArray> future = Future.future();
		mongo.insert(collection, files, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(body.getJsonArray("result"));
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<JsonObject> updateInheritShares(JsonObject file) {
		Future<JsonObject> future = Future.future();
		String id = file.getString("_id");
		JsonArray inheritShared = file.getJsonArray("inheritedShares");
		JsonObject set = new MongoUpdateBuilder().addToSet("inheritedShares", inheritShared).build();
		mongo.update(collection, toJson(QueryBuilder.start("_id").is(id)), set, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(file);
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<Void> update(String id, JsonObject set) {
		Future<Void> future = Future.future();
		mongo.update(collection, toJson(QueryBuilder.start("_id").is(id)), set, message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(null);
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<Void> update(String id, MongoUpdateBuilder set) {
		Future<Void> future = Future.future();
		set.addToSet("modified", DateUtils.getDateJsonObject(new Date()));
		mongo.update(collection, toJson(QueryBuilder.start("_id").is(id)), set.build(), message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(null);
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<Void> updateAll(Set<String> id, MongoUpdateBuilder set) {
		Future<Void> future = Future.future();
		set.addToSet("modified", DateUtils.getDateJsonObject(new Date()));
		mongo.update(collection, toJson(QueryBuilder.start("_id").in(id)), set.build(), message -> {
			JsonObject body = message.body();
			if (isOk(body)) {
				future.complete(null);
			} else {
				future.fail(toErrorStr(body));
			}
		});
		return future;
	}

	Future<Void> bulkUpdate(JsonArray operations) {
		Future<Void> future = Future.future();
		mongo.bulk(collection, operations, bulkEv -> {
			if (isOk(bulkEv.body())) {
				future.complete((null));
			} else {
				future.fail(toErrorStr(bulkEv.body()));
			}
		});
		return future;
	}

	Future<Void> deleteFiles(Set<String> ids) {
		Future<Void> future = Future.future();
		mongo.delete(collection, toJson(QueryBuilder.start("_id").in(ids)), res -> {
			if (isOk(res.body())) {
				future.complete(null);
			} else {
				future.fail(toErrorStr(res.body()));
			}
		});
		return future;
	}
}
