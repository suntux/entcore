package org.entcore.feeder.dictionary.structures;

import org.entcore.feeder.utils.Joiner;
import org.entcore.feeder.utils.Neo4j;
import org.entcore.feeder.utils.TransactionHelper;
import org.entcore.feeder.utils.Validator;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Importer {

	private static final Logger log = LoggerFactory.getLogger(Importer.class);
	private final ConcurrentMap<String, Structure> structures = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Profile> profiles = new ConcurrentHashMap<>();
	private TransactionHelper transactionHelper;
	private final Validator structureValidator;
	private final Validator profileValidator;
	private final Validator studyValidator;
	private final Validator moduleValidator;
	private final Validator userValidator;
	private final Validator personnelValidator;
	private final Validator studentValidator;
	private boolean firstImport = false;
	private Neo4j neo4j;

	private Importer() {
		structureValidator = new Validator("dictionary/schema/Structure.json");
		profileValidator = new Validator("dictionary/schema/Profile.json");
		studyValidator = new Validator("dictionary/schema/FieldOfStudy.json");
		moduleValidator = new Validator("dictionary/schema/Module.json");
		userValidator = new Validator("dictionary/schema/User.json");
		personnelValidator = new Validator("dictionary/schema/Personnel.json");
		studentValidator = new Validator("dictionary/schema/Student.json");
	}

	private static class StructuresHolder {
		private static final Importer instance = new Importer();
	}

	public static Importer getInstance() {
		return StructuresHolder.instance;
	}

	public void init(final Neo4j neo4j, final Handler<Message<JsonObject>> handler) {
		this.neo4j = neo4j;
		this.transactionHelper = new TransactionHelper(neo4j, 1000);
		String query =
				"MATCH (s:Structure) " +
				"OPTIONAL MATCH s<-[:DEPENDS]-(g:FunctionalGroup) " +
				"OPTIONAL MATCH s<-[:BELONGS]-(c:Class) " +
				"return s, collect(g.externalId) as groups, collect(c.externalId) as classes ";
		neo4j.execute(query, new JsonObject(), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				String query =
						"MATCH (p:Profile) " +
						"OPTIONAL MATCH p<-[:COMPOSE]-(f:Function) " +
						"return p, collect(f.externalId) as functions ";
				neo4j.execute(query, new JsonObject(), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> message) {
						JsonArray res = message.body().getArray("result");
						if ("ok".equals(message.body().getString("status")) && res != null) {
							for (Object o : res) {
								if (!(o instanceof JsonObject)) continue;
								JsonObject r = (JsonObject) o;
								JsonObject p = r.getObject("p", new JsonObject()).getObject("data");
								profiles.putIfAbsent(p.getString("externalId"),
										new Profile(p, r.getArray("functions")));
							}
						}
						if (handler != null) {
							handler.handle(message);
						}
					}
				});
				JsonArray res = message.body().getArray("result");
				if ("ok".equals(message.body().getString("status")) && res != null) {
					firstImport = res.size() == 0;
					for (Object o : res) {
						if (!(o instanceof JsonObject)) continue;
						JsonObject r = (JsonObject) o;
						JsonObject s = r.getObject("s", new JsonObject()).getObject("data");
						structures.putIfAbsent(s.getString("externalId"),
								new Structure(s, r.getArray("groups"), r.getArray("classes")));
					}
				}
			}
		});
	}


	public TransactionHelper getTransaction() {
		return transactionHelper;
	}

	public void clear() {
		structures.clear();
		profiles.clear();
		transactionHelper = null;
	}

	public boolean isReady() {
		return transactionHelper == null;
	}

	public void persist(final Handler<Message<JsonObject>> handler) {
		if (transactionHelper != null) {
			transactionHelper.commit(new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					transactionHelper = new TransactionHelper(neo4j, 1000);
					if (handler != null) {
						handler.handle(message);
					}
				}
			});
		}
		transactionHelper = null;
	}

	public void flush(Handler<Message<JsonObject>> handler) {
		if (transactionHelper != null) {
			transactionHelper.flush(handler);
		}
	}

	public Structure createOrUpdateStructure(JsonObject struct) {
		final String error = structureValidator.validate(struct);
		Structure s = null;
		if (error != null) {
			log.warn(error);
		} else {
			String externalId = struct.getString("externalId");
			s = structures.get(externalId);
			if (s != null) {
				s.update(struct);
			} else {
				try {
					s = new Structure(externalId, struct);
					structures.putIfAbsent(externalId, s);
					s.create();
				} catch (IllegalArgumentException e) {
					log.error(e.getMessage());
				}
			}
		}
		return s;
	}

	public Profile createOrUpdateProfile(JsonObject profile) {
		final String error = profileValidator.validate(profile);
		Profile p = null;
		if (error != null) {
			log.warn(error);
		} else {
			String externalId = profile.getString("externalId");
			p = profiles.get(externalId);
			if (p != null) {
				p.update(profile);
			} else {
				try {
					p = new Profile(externalId, profile);
					profiles.putIfAbsent(externalId, p);
					p.create();
				} catch (IllegalArgumentException e) {
					log.error(e.getMessage());
				}
			}
		}
		return p;
	}

	public void createOrUpdateFieldOfStudy(JsonObject object) {
		final String error = studyValidator.validate(object);
		if (error != null) {
			log.warn(error);
		} else {
			String query;
			JsonObject params;
			if (!firstImport) {
				query =
						"MERGE (fos:FieldOfStudy { externalId : {externalId}}) " +
						"ON CREATE SET fos.id = {id} " +
						"WITH fos " +
						"WHERE fos.checksum IS NULL OR fos.checksum <> {checksum} " +
						"SET " + Neo4j.nodeSetPropertiesFromJson("fos", object, "id", "externalId");
				params = object;
			} else {
				query = "CREATE (fos:FieldOfStudy {props}) ";
				params = new JsonObject().putObject("props", object);
			}
			transactionHelper.add(query, params);
		}
	}

	public void createOrUpdateModule(JsonObject object) {
		final String error = moduleValidator.validate(object);
		if (error != null) {
			log.warn(error);
		} else {
			String query;
			JsonObject params;
			if (!firstImport) {
				query =
						"MERGE (m:Module { externalId : {externalId}}) " +
						"ON CREATE SET m.id = {id} " +
						"WITH m " +
						"WHERE m.checksum IS NULL OR m.checksum <> {checksum} " +
						"SET " + Neo4j.nodeSetPropertiesFromJson("m", object, "id", "externalId");
				params = object;
			} else {
				query = "CREATE (m:Module {props}) ";
				params = new JsonObject().putObject("props", object);
			}
			transactionHelper.add(query, params);
		}
	}

	public void createOrUpdateUser(JsonObject object) {
		createOrUpdateUser(object, null);
	}

	public void createOrUpdateUser(JsonObject object, JsonArray linkStudent) {
		final String error = userValidator.validate(object);
		if (error != null) {
			log.warn(error);
		} else {
			String query;
			JsonObject params;
			if (!firstImport) {
				query =
						"MERGE (u:User { externalId : {externalId}}) " +
						"ON CREATE SET u.id = {id}, u.login = {login} " +
						"WITH u " +
						"WHERE u.checksum IS NULL OR u.checksum <> {checksum} " +
						"SET " + Neo4j.nodeSetPropertiesFromJson("u", object, "id", "externalId", "login");
				params = object;
			} else {
				query = "CREATE (u:User {props}) ";
				params = new JsonObject().putObject("props", object);
			}
			transactionHelper.add(query, params);
			if (linkStudent != null && linkStudent.size() > 0) {
				String query2 =
						"START u=node:node_auto_index(externalId={externalId}), " +
						"s=node:node_auto_index({studentExternalIds}) " +
						"CREATE u<-[:RELATED]-s ";
				JsonObject p = new JsonObject()
						.putString("externalId", object.getString("externalId"))
						.putString("studentExternalIds",
								"externalId:" + Joiner.on(" OR externalId:").join(linkStudent));
				transactionHelper.add(query2, p);
			}
		}
	}


	public boolean isFirstImport() {
		return firstImport;
	}

	public void structureConstraints() {
		JsonObject j = new JsonObject();
		transactionHelper.add("CREATE CONSTRAINT ON (structure:Structure) ASSERT structure.id IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (structure:Structure) ASSERT structure.externalId IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (structure:Structure) ASSERT structure.UAI IS UNIQUE;", j);
	}

	public void fieldOfStudyConstraints() {
		JsonObject j = new JsonObject();
		transactionHelper.add("CREATE CONSTRAINT ON (fos:FieldOfStudy) ASSERT fos.id IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (fos:FieldOfStudy) ASSERT fos.externalId IS UNIQUE;", j);
	}

	public void moduleConstraints() {
		JsonObject j = new JsonObject();
		transactionHelper.add("CREATE CONSTRAINT ON (module:Module) ASSERT module.id IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (module:Module) ASSERT module.externalId IS UNIQUE;", j);
	}

	public void userConstraints() {
		JsonObject j = new JsonObject();
		transactionHelper.add("CREATE CONSTRAINT ON (user:User) ASSERT user.id IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (user:User) ASSERT user.externalId IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (user:User) ASSERT user.login IS UNIQUE;", j);
	}

	public void profileConstraints() {
		JsonObject j = new JsonObject();
		transactionHelper.add("CREATE CONSTRAINT ON (profile:Profile) ASSERT profile.id IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (profile:Profile) ASSERT profile.externalId IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (profile:Profile) ASSERT profile.name IS UNIQUE;", j);
	}

	public void functionConstraints() {
		JsonObject j = new JsonObject();
		transactionHelper.add("CREATE CONSTRAINT ON (function:Function) ASSERT function.id IS UNIQUE;", j);
		transactionHelper.add("CREATE CONSTRAINT ON (function:Function) ASSERT function.externalId IS UNIQUE;", j);
	}

	public void createOrUpdatePersonnel(JsonObject object, String profileExternalId, String[][] linkClasses,
			String[][] linkGroups, boolean nodeQueries, boolean relationshipQueries) {
		final String error = personnelValidator.validate(object);
		if (error != null) {
			log.warn(error);
		} else {
			if (nodeQueries) {
				StringBuilder sb = new StringBuilder();
				JsonObject params;
				if (!firstImport) {
					sb.append("MERGE (u:`User` { externalId : {externalId}}) ");
					sb.append("ON CREATE SET u.id = {id}, u.login = {login} ");
					sb.append("WITH u ");
					sb.append("WHERE u.checksum IS NULL OR u.checksum <> {checksum} ");
					sb.append("SET ").append(Neo4j.nodeSetPropertiesFromJson("u", object, "id", "externalId", "login"));
					params = object;
				} else {
					sb.append("CREATE (u:User {props}) ");
					params = new JsonObject().putObject("props", object);
				}
				transactionHelper.add(sb.toString(), params);
			}
			if (relationshipQueries) {
				final String externalId = object.getString("externalId");
				JsonArray structures = object.getArray("structures");
				if (externalId != null && structures != null && structures.size() > 0) {
					String query;
					JsonObject p = new JsonObject().putString("userExternalId", externalId);
					if (structures.size() == 1) {
						query = "MATCH (s:Structure)<-[:DEPENDS]-(g:ProfileGroup)-[:HAS_PROFILE]->(p:Profile), " +
								"(u:User) " +
								"USING INDEX s:Structure(externalId) " +
								"USING INDEX u:User(externalId) " +
								"USING INDEX p:Profile(externalId) " +
								"WHERE s.externalId = {structureAdmin} AND u.externalId = {userExternalId} " +
								"AND p.externalId = {profileExternalId} " +
								"CREATE u-[:ADMINISTRATIVE_ATTACHMENT]->s, " +
								"u-[:IN]->g";
						p.putString("structureAdmin", (String) structures.get(0))
								.putString("profileExternalId", profileExternalId);
					} else {
						query = "MATCH (s:Structure)<-[:DEPENDS]-(g:ProfileGroup)-[:HAS_PROFILE]->(p:Profile), " +
								"(u:User) " +
								"USING INDEX s:Structure(externalId) " +
								"USING INDEX u:User(externalId) " +
								"USING INDEX p:Profile(externalId) " +
								"WHERE s.externalId IN {structuresAdmin} AND u.externalId = {userExternalId} " +
								"AND p.externalId = {profileExternalId} " +
								"CREATE u-[:ADMINISTRATIVE_ATTACHMENT]->s, " +
								"u-[:IN]->g";
						p.putArray("structuresAdmin", structures)
								.putString("profileExternalId", profileExternalId);
					}
					transactionHelper.add(query, p);
				}
				if (externalId != null && linkClasses != null) {
					for (String[] structClass : linkClasses) {
						if (structClass != null && structClass[0] != null && structClass[1] != null) {
							String query =
									"MATCH (s:Structure)<-[:BELONGS]-(c:Class)<-[:DEPENDS]-(g:ProfileGroup)" +
									"-[:DEPENDS]->(pg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile), (u:User) " +
									"USING INDEX s:Structure(externalId) " +
									"USING INDEX u:User(externalId) " +
									"USING INDEX p:Profile(externalId) " +
									"WHERE s.externalId = {structure} AND c.externalId = {class} " +
									"AND u.externalId = {userExternalId} AND p.externalId = {profileExternalId} " +
									"CREATE UNIQUE u-[:IN]->g";
							JsonObject p = new JsonObject()
									.putString("userExternalId", externalId)
									.putString("profileExternalId", profileExternalId)
									.putString("structure", structClass[0])
									.putString("class", structClass[1]);
							transactionHelper.add(query, p);
						}
					}
				}
				if (externalId != null && linkGroups != null) {
					for (String[] structGroup : linkGroups) {
						if (structGroup != null && structGroup[0] != null && structGroup[1] != null) {
							String query =
									"MATCH (s:Structure)" +
									"<-[:DEPENDS]-(g:FunctionalGroup), " +
									"(u:User) " +
									"USING INDEX s:Structure(externalId) " +
									"USING INDEX u:User(externalId) " +
									"WHERE s.externalId = {structure} AND g.externalId = {group} AND u.externalId = {userExternalId} " +
									"CREATE UNIQUE u-[:IN]->g";
							JsonObject p = new JsonObject()
									.putString("userExternalId", externalId)
									.putString("structure", structGroup[0])
									.putString("group", structGroup[1]);
							transactionHelper.add(query, p);
						}
					}
				}
			}
		}
	}

	public void createOrUpdateStudent(JsonObject object, String profileExternalId, String module, JsonArray fieldOfStudy,
			String[][] linkClasses, String[][] linkGroups, JsonArray relative, boolean nodeQueries,
			boolean relationshipQueries) {
		final String error = studentValidator.validate(object);
		if (error != null) {
			log.warn(error);
		} else {
			if (nodeQueries) {
				StringBuilder sb = new StringBuilder();
				JsonObject params;
				if (!firstImport) {
					sb.append("MERGE (u:`User` { externalId : {externalId}}) ");
					sb.append("ON CREATE SET u.id = {id}, u.login = {login} ");
					sb.append("WITH u ");
					sb.append("WHERE u.checksum IS NULL OR u.checksum <> {checksum} ");
					sb.append("SET ").append(Neo4j.nodeSetPropertiesFromJson("u", object, "id", "externalId", "login"));
					params = object;
				} else {
					sb.append("CREATE (u:User {props}) ");
					params = new JsonObject().putObject("props", object);
				}
				transactionHelper.add(sb.toString(), params);
			}
			if (relationshipQueries) {
				final String externalId = object.getString("externalId");
				JsonArray structures = object.getArray("structures");
				if (externalId != null && structures != null && structures.size() > 0) {
					String query;
					JsonObject p = new JsonObject().putString("userExternalId", externalId);
					if (structures.size() == 1) {
						query = "MATCH (s:Structure)<-[:DEPENDS]-(g:ProfileGroup)-[:HAS_PROFILE]->(p:Profile), " +
								"(u:User) " +
								"USING INDEX s:Structure(externalId) " +
								"USING INDEX u:User(externalId) " +
								"USING INDEX p:Profile(externalId) " +
								"WHERE s.externalId = {structureAdmin} AND u.externalId = {userExternalId} " +
								"AND p.externalId = {profileExternalId} " +
								"CREATE u-[:ADMINISTRATIVE_ATTACHMENT]->s, " +
								"u-[:IN]->g";
						p.putString("structureAdmin", (String) structures.get(0))
								.putString("profileExternalId", profileExternalId);
					} else {
						query = "MATCH (s:Structure)<-[:DEPENDS]-(g:ProfileGroup)-[:HAS_PROFILE]->(p:Profile), " +
								"(u:User) " +
								"USING INDEX s:Structure(externalId) " +
								"USING INDEX u:User(externalId) " +
								"USING INDEX p:Profile(externalId) " +
								"WHERE s.externalId IN {structuresAdmin} AND u.externalId = {userExternalId} " +
								"AND p.externalId = {profileExternalId} " +
								"CREATE u-[:ADMINISTRATIVE_ATTACHMENT]->s, " +
								"u-[:IN]->g";
						p.putArray("structuresAdmin", structures)
								.putString("profileExternalId", profileExternalId);
					}
					transactionHelper.add(query, p);
				}
				if (externalId != null && linkClasses != null) {
					for (String[] structClass : linkClasses) {
						if (structClass != null && structClass[0] != null && structClass[1] != null) {
							String query =
									"MATCH (s:Structure)<-[:BELONGS]-(c:Class)<-[:DEPENDS]-(g:ProfileGroup)" +
									"-[:DEPENDS]->(pg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile), (u:User) " +
									"USING INDEX s:Structure(externalId) " +
									"USING INDEX u:User(externalId) " +
									"USING INDEX p:Profile(externalId) " +
									"WHERE s.externalId = {structure} AND c.externalId = {class} " +
									"AND u.externalId = {userExternalId} AND p.externalId = {profileExternalId} " +
									"CREATE UNIQUE u-[:IN]->g";
							JsonObject p = new JsonObject()
									.putString("userExternalId", externalId)
									.putString("profileExternalId", profileExternalId)
									.putString("structure", structClass[0])
									.putString("class", structClass[1]);
							transactionHelper.add(query, p);
						}
					}
				}
				if (externalId != null && linkGroups != null) {
					for (String[] structGroup : linkGroups) {
						if (structGroup != null && structGroup[0] != null && structGroup[1] != null) {
							String query =
									"MATCH (s:Structure)" +
									"<-[:DEPENDS]-(g:FunctionalGroup), " +
									"(u:User) " +
									"USING INDEX s:Structure(externalId) " +
									"USING INDEX u:User(externalId) " +
									"WHERE s.externalId : {structure} AND g.externalId = {group} " +
									"AND u.externalId = {userExternalId} " +
									"CREATE UNIQUE u-[:IN]->g";
							JsonObject p = new JsonObject()
									.putString("userExternalId", externalId)
									.putString("structure", structGroup[0])
									.putString("group", structGroup[1]);
							transactionHelper.add(query, p);
						}
					}
				}
				if (externalId != null && module != null) {
					String query =
							"START u=node:node_auto_index(externalId={userExternalId}), " +
							"m=node:node_auto_index(externalId={moduleStudent}) " +
							"CREATE UNIQUE u-[:FOLLOW]->m";
					JsonObject p = new JsonObject()
							.putString("userExternalId", externalId)
							.putString("moduleStudent", module);
					transactionHelper.add(query, p);
				}
				if (externalId != null && fieldOfStudy != null && fieldOfStudy.size() > 0) {
					for (Object o : fieldOfStudy) {
						if (!(o instanceof String)) continue;
						String query =
								"START u=node:node_auto_index(externalId={userExternalId}), " +
								"f=node:node_auto_index(externalId={fieldOfStudyStudent}) " +
								"CREATE UNIQUE u-[:COURSE]->f";
						JsonObject p = new JsonObject()
								.putString("userExternalId", externalId)
								.putString("fieldOfStudyStudent", (String) o);
						transactionHelper.add(query, p);
					}
				}
				if (externalId != null && relative != null && relative.size() > 0) {
					for (Object o : relative) {
						if (!(o instanceof String)) continue;
						String query =
								"START u=node:node_auto_index(externalId={userExternalId}), " +
								"r=node:node_auto_index(externalId={user}) " +
								"CREATE UNIQUE u-[:RELATED]->r";
						JsonObject p = new JsonObject()
								.putString("userExternalId", externalId)
								.putString("user", (String) o);
						transactionHelper.add(query, p);
					}
				}
			}
		}
	}

	public void linkRelativeToStructure(String profileExternalId) {
		JsonObject j = new JsonObject().putString("profileExternalId", profileExternalId);
		String query =
				"MATCH (u:User)<-[:RELATED]-(s:Student)-[:IN]->(scg:ProfileGroup)" +
				"-[:DEPENDS]->(c:Structure)<-[:DEPENDS]-(rcg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile) " +
				"WHERE p.externalId = {profileExternalId} AND NOT((u)-[:IN]->(rcg)) " +
				"CREATE u-[:IN]->rcg";
		transactionHelper.add(query, j);
	}

	public void linkRelativeToClass(String profileExternalId) {
		JsonObject j = new JsonObject().putString("profileExternalId", profileExternalId);
		String query =
				"MATCH (u:User)<-[:RELATED]-(s:Student)-[:IN]->(scg:ProfileGroup)" +
				"-[:DEPENDS]->(c:Class)<-[:DEPENDS]-(rcg:ProfileGroup)" +
				"-[:DEPENDS]->(pg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile) " +
				"WHERE p.externalId = {profileExternalId} AND NOT((u)-[:IN]->(rcg)) " +
				"CREATE u-[:IN]->rcg";
		transactionHelper.add(query, j);
	}

	public Structure getStructure(String externalId) {
		return structures.get(externalId);
	}

}
