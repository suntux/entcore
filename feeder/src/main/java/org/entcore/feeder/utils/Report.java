/*
 * Copyright © WebServices pour l'Éducation, 2016
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.feeder.utils;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.I18n;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.feeder.exceptions.TransactionException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Report {

	public static final Logger log = LoggerFactory.getLogger(Report.class);
	public static final String FILES = "files";
	public static final String PROFILES = "profiles";
	private static final String MAPPINGS = "mappings";
	public static final String KEYS_CLEANED = "keysCleaned";
	public final JsonObject result;
	private final I18n i18n = I18n.getInstance();
	public final String acceptLanguage;
	private long endTime;
	private long startTime;
	private String source;
	private Set<Object> loadedFiles = new HashSet<>();
	public enum State { NEW, UPDATED, DELETED }

	public Report(String acceptLanguage) {
		this.acceptLanguage = acceptLanguage;
		final JsonObject errors = new JsonObject();
		final JsonObject files = new JsonObject();
		JsonObject ignored = new JsonObject();
		result = new JsonObject().put("_id", UUID.randomUUID().toString()).put("created", MongoDb.now())
				.put("errors", errors).put(FILES, files).put("ignored", ignored)
				.put("source", getSource());
	}

	public Report addError(String error) {
		addErrorWithParams(error);
		return this;
	}

	public void addError(String file, String error) {
		addErrorByFile(file, error);
	}

	public void addErrorWithParams(String key, String... errors) {
		addErrorByFile("global", key, errors);
	}

	public void addFailedUser(String filename, String key, JsonObject props, String... errors) {
		final String file = "error." + filename;
		JsonArray f = result.getJsonObject("errors").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("errors").put(file, f);
		}
		String error = i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, errors);
		props.put("error", error);
		f.add(props);
		log.error(error + " :\n" + Arrays.asList(props));
	}

	public void addErrorByFile(String filename, String key, String... errors) {
		final String file = "error." + filename;
		JsonArray f = result.getJsonObject("errors").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("errors").put(file, f);
		}
		String error = i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, errors);
		f.add(error);
		log.error(error);
	}

	public void addSoftErrorByFile(String file, String key, String lineNumber, String... errors) {
		JsonObject softErrors = result.getJsonObject("softErrors");
		if (softErrors == null) {
			softErrors = new JsonObject();
			result.put("softErrors", softErrors);
		}
		JsonArray reasons = softErrors.getArray("reasons");
		if (reasons == null) {
			reasons = new JsonArray();
			softErrors.putArray("reasons", reasons);
		}
		if (!reasons.contains(key)) {
			reasons.addString(key);
		}

		JsonArray fileErrors = softErrors.getArray(file);
		if (fileErrors == null) {
			fileErrors = new JsonArray();
			softErrors.putArray(file, fileErrors);
		}
		JsonObject error = new JsonObject().copy()
				.put("line",lineNumber)
				.put("reason", key)
				.put("attribute", errors.length > 0 ? errors[0] : "")
				.put("value", errors.length > 1 ? errors[1] : "");

		List<String> errorContext = new ArrayList<>(Arrays.asList(errors)); // Hack to support "add" operation
		errorContext.add(0, lineNumber);
		String translation = i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, errorContext.toArray(new String[errorContext.size()]));
		error.put("translation", translation);

		fileErrors.addObject(error);
		log.error(translation);
		//String cleanKey = key.replace('.','-'); // Mongo don't support '.' characters in document field's name
	}

	public void addUser(String file, JsonObject props) {
		JsonArray f = result.getJsonObject(FILES).getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject(FILES).put(file, f);
		}
		f.add(props);
	}

	public void addProfile(String profile) {
		JsonArray f = result.getJsonArray(PROFILES);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.put(PROFILES, f);
		}
		f.add(profile);
	}

	public void addIgnored(String file, String reason, JsonObject object) {
		JsonArray f = result.getJsonObject("ignored").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("ignored").put(file, f);
		}
		f.add(new JsonObject().put("reason", reason).put("object", object));
	}

	public String translate(String key, String... params) {
		return i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, params);
	}

	public JsonObject getResult() {
		return result.copy();
	}

	public void setUsersExternalId(JsonArray usersExternalIds) {
		result.put("usersExternalIds", usersExternalIds);
	}

	public JsonArray getUsersExternalId() {
		final JsonArray res = new fr.wseduc.webutils.collections.JsonArray();
		for (String f : result.getJsonObject(FILES).getFieldNames()) {
			JsonArray a = result.getJsonObject(FILES).getJsonArray(f);
			if (a != null) {
				for (Object o : a) {
					if (!(o instanceof JsonObject)) continue;
					final String externalId = ((JsonObject) o).getString("externalId");
					if (externalId != null) {
						res.add(externalId);
					}
				}
			}
		}
		return res;
	}

	public boolean containsErrors() {
		return result.getJsonObject("errors", new JsonObject()).size() > 0;
	}

	public void persist(Handler<Message<JsonObject>> handler) {
		cleanKeys();
		MongoDb.getInstance().save("imports", this.getResult(), handler);
	}

	public void updateErrors(Handler<Message<JsonObject>> handler) {
		boolean cleaned = updateCleanKeys();
		JsonObject modif = new JsonObject()
				.putObject("errors", result.getObject("errors"))
				.putObject("softErrors", result.getObject("softErrors"));
		if (cleaned) {
			modif.putBoolean(KEYS_CLEANED, true);
		}
		MongoDb.getInstance().update("imports", new JsonObject().put("_id", result.getString("_id")),
				new JsonObject().putObject("$set", modif), handler);
	}

	protected void cleanKeys() {}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public void loadedFile(String file) {
		loadedFiles.add(file);
	}

	public void countDiff(final Handler<Void> handler) {
		try {
			TransactionHelper tx = TransactionManager.getTransaction();
			JsonObject params = new JsonObject()
					.put("source", source)
					.put("start", startTime).put("end", endTime)
					.put("startTime", new DateTime(startTime).toString())
					.put("endTime", new DateTime(endTime).toString());
			tx.add(
					"MATCH (u:User {source:{source}}) " +
					"WHERE HAS(u.created) AND u.created >= {startTime} AND u.created < {endTime} " +
					"RETURN count(*) as createdCount", params);
			tx.add(
					"MATCH (u:User {source:{source}}) " +
					"WHERE HAS(u.modified) AND u.modified >= {startTime} AND u.modified < {endTime} " +
					"RETURN count(*) as modifiedCount", params);
			tx.add(
					"MATCH (u:User {source:{source}}) " +
					"WHERE HAS(u.disappearanceDate) AND u.disappearanceDate >= {start} AND u.disappearanceDate < {end} " +
					"RETURN count(*) as disappearanceCount", params);
			tx.commit(new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					JsonArray results = event.body().getJsonArray("results");
					if ("ok".equals(event.body().getString("status")) && results != null && results.size() == 3) {
						try {
							int created = results.getJsonArray(0).getJsonObject(0).getInteger("createdCount");
							int modified = results.getJsonArray(1).getJsonObject(0).getInteger("modifiedCount");
							int disappearance = results.getJsonArray(2).getJsonObject(0).getInteger("disappearanceCount");
							result.put("userCount", new JsonObject()
									.put("created", created)
									.put("modified", (modified - created))
									.put("disappearance", disappearance)
							);
							result.put("source", source);
							result.put("startTime", new DateTime(startTime).toString());
							result.put("endTime", new DateTime(endTime).toString());
							result.put("loadedFiles", new fr.wseduc.webutils.collections.JsonArray(new ArrayList<>(loadedFiles)));
//							persist(new Handler<Message<JsonObject>>() {
//								@Override
//								public void handle(Message<JsonObject> event) {
//									if (!"ok".equals(event.body().getString("status"))) {
//										log.error("Error persist report : " + event.body().getString("message"));
//									}
//								}
//							});
						} catch (RuntimeException e) {
							log.error("Error parsing count diff response.", e);
						}
					} else {
						log.error("Error in count diff transaction.");
					}
					if (handler != null) {
						handler.handle(null);
					}
				}
			});
		} catch (TransactionException e) {
			log.error("Exception in count diff transaction.", e);
			if (handler != null) {
				handler.handle(null);
			}
		}
	}

	public void emailReport(final Vertx vertx, final JsonObject config) {
		final JsonObject sendReport = config.getJsonObject("sendReport");
		if (sendReport == null || sendReport.getJsonArray("to") == null || sendReport.getJsonArray("to").size() == 0 ||
				sendReport.getJsonArray("sources") == null || !sendReport.getJsonArray("sources").contains(source) ) {
			return;
		}

		final JsonObject reqParams = new JsonObject()
				.put("headers", new JsonObject().put("Accept-Language", acceptLanguage));
		EmailFactory emailFactory = new EmailFactory(vertx, config);
		emailFactory.getSender().sendEmail(
				new JsonHttpServerRequest(reqParams),
				sendReport.getJsonArray("to").getList(),
				sendReport.getJsonArray("cc") != null ? sendReport.getJsonArray("cc").getList() : null,
				sendReport.getJsonArray("bcc") != null ? sendReport.getJsonArray("bcc").getList() : null,
				sendReport.getString("project", "") + i18n.translate("import.report", I18n.DEFAULT_DOMAIN, acceptLanguage) +
						" - " + DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd")),
				"email/report.html",
				result,
				false,
				ar -> {
					if (ar.failed()) {
						log.error("Error sending report email.", ar.cause());
					}
				}
		);
	}

	public void addMapping(String profile, JsonObject mappping) {
		JsonObject mappings = result.getObject(MAPPINGS);
		if (mappings == null) {
			mappings = new JsonObject();
			result.putObject(MAPPINGS, mappings);
		}
		mappings.putObject(profile, mappping);
	}

	public JsonObject getMappings() {
		return result.getObject(MAPPINGS);
  }

	protected boolean updateCleanKeys() { return false; }

	protected int cleanAttributeKeys(JsonObject attribute) {
		int count = 0;
		if (attribute != null) {
			for (String attr : attribute.copy().getFieldNames()) {
				Object j = attribute.getValue(attr);
				if (j instanceof JsonObject) {
					for (String attr2 : ((JsonObject) j).copy().getFieldNames()) {
						if (attr2.contains(".")) {
							count++;
							((JsonObject) j).put(
									attr2.replaceAll("\\.", "_|_"), (String) ((JsonObject) j).removeField(attr2));
						}
					}
				} else if (j instanceof JsonArray && attr.contains(".")) {
					attribute.putArray(attr.replaceAll("\\.", "_|_"), (JsonArray) j);
					attribute.removeField(attr);
					count++;
				}
			}
		}
		return count;
	}

	protected void uncleanAttributeKeys(JsonObject attribute) {
		if (attribute != null) {
			for (String attr : attribute.copy().getFieldNames()) {
				Object j = attribute.getValue(attr);
				if (j instanceof JsonObject) {
					for (String attr2 : ((JsonObject) j).copy().getFieldNames()) {
						if (attr2.contains("_|_")) {
							((JsonObject) j).put(
									attr2.replaceAll("_\\|_", "."), (String) ((JsonObject) j).removeField(attr2));
						}
					}
				} else if (j instanceof JsonArray && attr.contains("_|_")) {
					attribute.putArray(attr.replaceAll("_\\|_", "."), (JsonArray) j);
					attribute.removeField(attr);
				}
			}
		}
	}

	public String getSource() {
		return "REPORT";
	}

}
