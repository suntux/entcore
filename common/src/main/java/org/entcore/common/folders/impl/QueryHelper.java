package org.entcore.common.folders.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.folders.ElementQuery.ElementSort;
import org.entcore.common.service.impl.MongoDbSearchService;
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

	public static class DocumentQueryBuilder {
		QueryBuilder builder = new QueryBuilder();
		private boolean excludeDeleted;
		private JsonObject mongoSorts;
		private JsonObject mongoProjections;
		private boolean onlyDeleted;
		private Integer skip;
		private Integer limit;

		public DocumentQueryBuilder withSkipAndLimit(Integer skip, Integer limit) {
			if (skip == null) {
				skip = -1;
			}
			this.limit = limit;
			this.skip = skip;
			return this;
		}

		public DocumentQueryBuilder withProjections(List<String> projection) {
			mongoProjections = new JsonObject();
			for (String p : projection) {
				mongoProjections.put(p, 1);
			}
			return this;
		}

		public DocumentQueryBuilder withSorts(List<Map.Entry<String, ElementSort>> sorts) {
			mongoSorts = new JsonObject();
			for (Map.Entry<String, ElementSort> p : sorts) {
				mongoSorts.put(p.getKey(), p.getValue().equals(ElementSort.Asc) ? 1 : -1);
			}
			return this;
		}

		public DocumentQueryBuilder filterByInheritSharedAndOwnerVisibilities(UserInfos user,
				Collection<String> visibilities) {
			List<DBObject> groups = new ArrayList<>();
			groups.add(builder.and("userId").is(user.getUserId()).get());
			for (String gpId : user.getGroupsIds()) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}
			//
			List<DBObject> ors = new ArrayList<>();
			for (String v : visibilities) {
				ors.add(QueryBuilder.start("visibility").is(v).get());
			}
			ors.add(QueryBuilder.start("owner").is(user.getUserId()).get());
			ors.add(QueryBuilder.start("inheritedShares")
					.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
			//
			builder.or(ors.toArray(new DBObject[ors.size()]));
			return this;
		}

		public DocumentQueryBuilder filterByOwnerVisibilities(UserInfos user, Collection<String> visibilities) {
			List<DBObject> ors = new ArrayList<>();
			for (String v : visibilities) {
				ors.add(QueryBuilder.start("visibility").is(v).get());
			}
			ors.add(QueryBuilder.start("owner").is(user.getUserId()).get());
			//
			builder.or(ors.toArray(new DBObject[ors.size()]));
			return this;
		}

		public DocumentQueryBuilder withFullTextSearch(List<String> searchWordsLst) {
			if (searchWordsLst.isEmpty()) {
				return this;
			}
			final QueryBuilder worldsQuery = new QueryBuilder();
			worldsQuery.text(MongoDbSearchService.textSearchedComposition(searchWordsLst));
			builder.and(worldsQuery.get());
			return this;
		}

		public DocumentQueryBuilder filterBySharedAndOwner(UserInfos user) {
			List<DBObject> groups = new ArrayList<>();
			groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
			for (String gpId : user.getGroupsIds()) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}
			builder.or(QueryBuilder.start("owner").is(user.getUserId()).get(), QueryBuilder.start("shared")
					.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
			return this;
		}

		public DocumentQueryBuilder filterByInheritShareAndOwner(UserInfos user) {
			List<DBObject> groups = new ArrayList<>();
			groups.add(builder.and("userId").is(user.getUserId()).get());
			for (String gpId : user.getGroupsIds()) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}
			builder.or(QueryBuilder.start("owner").is(user.getUserId()).get(), QueryBuilder.start("inheritedShares")
					.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
			return this;
		}

		public DocumentQueryBuilder filterByOwner(UserInfos user) {
			builder.and("owner").is(user.getUserId());
			return this;
		}

		public DocumentQueryBuilder filterByInheritShareAndOwnerWithAction(UserInfos user, String action) {
			List<DBObject> groups = new ArrayList<>();
			groups.add(builder.and("userId").is(user.getUserId()).put(action).is(true).get());
			for (String gpId : user.getGroupsIds()) {
				groups.add(QueryBuilder.start("groupId").is(gpId).put(action).is(true).get());
			}
			builder.or(QueryBuilder.start("owner").is(user.getUserId()).get(), QueryBuilder.start("inheritedShares")
					.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
			return this;
		}

		public DocumentQueryBuilder withParent(String id) {
			builder.and("eParent").is(id);
			return this;
		}

		public DocumentQueryBuilder withId(String id) {
			builder.and("_id").is(id);
			return this;
		}

		public DocumentQueryBuilder withKeyValue(String key, Object value) {
			builder.and(key).is(value);
			return this;
		}

		public DocumentQueryBuilder withIds(Collection<String> ids) {
			builder.and("_id").in(ids);
			return this;
		}

		public DocumentQueryBuilder withExcludeDeleted() {
			excludeDeleted = true;
			builder.and("deleted").notEquals(true);
			return this;
		}

		public DocumentQueryBuilder withOnlyDeletedFile() {
			onlyDeleted = true;
			builder.and("deleted").is(true);
			return this;
		}

		public DocumentQueryBuilder withFileType(final int type) {
			builder.and("eType").is(type);
			return this;
		}

		public DocumentQueryBuilder withNameMatch(final String pattern) {
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

	DocumentQueryBuilder queryBuilder() {
		return new DocumentQueryBuilder();
	}

	Future<JsonArray> listRecursive(DocumentQueryBuilder query) {

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
		JsonObject projections = new JsonObject().put("_id", 1).put("name", 1).put("eType", 1).put("file", 1)
				.put("thumbnails", 1).put("eParent", 1).put("shared", 1).put("metadata", 1).put("parents", "$tree._id");
		if (query.mongoProjections != null) {
			projections = query.mongoProjections;
		}
		agg.withProjection(projections);
		// sort
		if (query.mongoSorts != null) {
			agg.withSort(query.mongoSorts);
		}
		// skip and limit
		if (query.skip != null) {
			agg.withSkip(query.skip);
		}
		if (query.limit != null) {
			agg.withLimit(query.limit);
		}
		//
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

	Future<JsonObject> findOne(DocumentQueryBuilder query) {
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

	Future<JsonArray> findAll(DocumentQueryBuilder query) {
		Future<JsonArray> future = Future.future();
		// finally project name and parent
		JsonObject projections = null;
		if (query.mongoProjections != null) {
			projections = query.mongoProjections;
		}
		// sort
		JsonObject mongoSorts = null;
		if (query.mongoSorts != null) {
			mongoSorts = (query.mongoSorts);
		}
		// limit skip
		Integer limit = -1, skip = -1;
		if (query.limit != null) {
			limit = query.limit;
		}
		if (query.skip != null) {
			skip = query.skip;
		}
		mongo.find(collection, toJson(query.build()), mongoSorts, projections, skip, limit, Integer.MAX_VALUE,
				message -> {
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
