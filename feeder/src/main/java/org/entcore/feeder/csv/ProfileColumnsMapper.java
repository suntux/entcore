/*
 * Copyright © WebServices pour l'Éducation, 2017
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

package org.entcore.feeder.csv;

import org.entcore.feeder.utils.CSVUtil;
import org.entcore.feeder.utils.ResultMessage;
import org.entcore.feeder.utils.Validator;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static fr.wseduc.webutils.Utils.isEmpty;

public class ProfileColumnsMapper {

	private final Map<String, Map<String, Object>> profilesNamesMapping = new HashMap<>();

	public ProfileColumnsMapper() {
		defaultInit();
	}

	private void defaultInit() {
		JsonObject baseMappings = new JsonObject()
				.put("id", "externalId")
				.put("externalid", "externalId")
				.put("nom", "lastName")
				.put("nomusage", "username")
				.put("nomdusage", "username")
				.put("nomusage", "surname")
				.put("prenom", "firstName")
				.put("classe", "classes")
				.put("libelleclasse", "classes")
				.put("classeouregroupement", "classes")
				.put("idenfant", "childExternalId")
				.put("datedenaissance", "birthDate")
				.put("datenaissance", "birthDate")
				.put("neele", "birthDate")
				.put("ne(e)le", "birthDate")
				.put("childid", "childExternalId")
				.put("childexternalid", "childExternalId")
				.put("nomenfant", "childLastName")
				.put("prenomenfant", "childFirstName")
				.put("classeenfant", "childClasses")
				.put("nomdusageenfant", "childUsername")
				.put("nomdefamilleenfant", "childLastName")
				.put("classesenfants", "childClasses")
				.put("presencedevanteleves", "teaches")
				.put("fonction", "functions")
				.put("niveau", "level")
				.put("regime", "accommodation")
				.put("filiere", "sector")
				.put("cycle", "sector")
				.put("mef", "module")
				.put("libellemef", "moduleName")
				.put("boursier", "scholarshipHolder")
				.put("transport", "transport")
				.put("statut", "status")
				.put("codematiere", "fieldOfStudy")
				.put("matiere", "fieldOfStudyLabels")
				.put("persreleleve", "relative")
				.put("datedenaissance", "birthDate")
				.put("datenaissance", "birthDate")
				.put("civilite", "title")
				.put("telephone", "homePhone")
				.put("telephonedomicile", "homePhone")
				.put("telephonetravail", "workPhone")
				.put("telephoneportable", "mobile")
				.put("adresse", "address")
				.put("adresse1", "address")
				.put("adresse2", "address2")
				.put("cp", "zipCode")
				.put("cp1", "zipCode")
				.put("cp2", "zipCode2")
				.put("ville", "city")
				.put("commune", "city")
				.put("commune1", "city")
				.put("commune2", "city2")
				.put("pays", "country")
				.put("pays1", "country")
				.put("pays2", "country2")
				.put("email", "email")
				.put("courriel", "email")
				.put("sexe", "gender")
				.put("ine", "ine")
				.put("identifiantclasse", "ignore")
				.put("dateinscription", "ignore")
				.put("deuxiemeprenom", "ignore")
				.put("troisiemeprenom", "ignore")
				.put("communenaissance", "ignore")
				.put("deptnaissance", "ignore")
				.put("paysnaissance", "ignore")
				.put("etat", "ignore")
				.put("intervenant", "ignore")
				.put("ignore", "ignore");
		JsonObject studentMappings = baseMappings.copy()
				.put("nomeleve", "lastName")
				.put("nomdusageeleve", "surname")
				.put("prenomeleve", "firstName")
				.put("niveau", "level")
				.put("regime", "accommodation")
				.put("filiere", "sector")
				.put("cycle", "sector")
				.put("attestationfournie", "ignore")
				.put("autorisationsassociations", "ignore")
				.put("autorisationassociations", "ignore")
				.put("autorisationsphotos", "ignore")
				.put("autorisationphoto", "ignore")
				.put("decisiondepassage", "ignore")
				.put("boursier", "scholarshipHolder")
				.put("transport", "transport")
				.put("statut", "status")
				.put("persreleleve", "relative");
		JsonObject relativeMapping = baseMappings.copy()
				.put("nomresponsable", "lastName")
				.put("nomusageresponsable", "surname")
				.put("prenomresponsable", "firstName")
				.put("idenfant", "childExternalId")
				.put("childid", "childExternalId")
				.put("childexternalid", "childExternalId")
				.put("nomenfant", "childLastName")
				.put("prenomenfant", "childFirstName")
				.put("classeenfant", "childClasses")
				.put("nomdusageenfant", "childUsername")
				.put("nomdefamilleenfant", "childLastName")
				.put("classesenfants", "childClasses")
				.put("civiliteresponsable", "title")
				.put("adresseresponsable", "address")
				.put("cpresponsable", "zipCode")
				.put("communeresponsable", "city");
		JsonObject teacherMapping = baseMappings.copy()
				.put("presencedevanteleves", "teaches")
				.put("fonction", "functions")
				.put("mef", "module")
				.put("libellemef", "moduleName")
				.put("codematiere", "fieldOfStudy")
				.put("matiere", "fieldOfStudyLabels")
				.put("professeurprincipal", "headTeacher")
				.put("discipline", "classCategories")
				.put("matiereenseignee", "subjectTaught")
				.put("directeur", "ignore");
		profilesNamesMapping.put("Teacher", teacherMapping.toMap());
		profilesNamesMapping.put("Personnel", teacherMapping.toMap());
		profilesNamesMapping.put("Student", studentMappings.toMap());
		profilesNamesMapping.put("Relative", relativeMapping.toMap());
		profilesNamesMapping.put("Guest", baseMappings.toMap());
	}

	public ProfileColumnsMapper(JsonObject mapping) {
		if (mapping == null || mapping.size() == 0) {
			defaultInit();
		} else {
			for (String profile: mapping.getFieldNames()) {
				final JsonObject m = mapping.getObject(profile);
				if (m != null) {
					JsonObject j = new JsonObject()
							.put("externalid", "externalId")
							.put("childexternalid", "childExternalId");
					for (String attr : m.getFieldNames()) {
						j.put(cleanKey(attr), m.getString(attr));
					}
					profilesNamesMapping.put(profile, j.toMap());
				}
			}
		}
	}

	void getColumsNames(String profile, String[] strings, List<String> columns, Handler<Message<JsonObject>> handler) {
		for (int j = 0; j < strings.length; j++) {
			String cm = columnsNameMapping(profile, strings[j]);
			if (profilesNamesMapping.get(profile).containsValue(cm)) {
				try {
					columns.add(j, cm);
				} catch (ArrayIndexOutOfBoundsException e) {
					columns.clear();
					handler.handle(new ResultMessage().error("invalid.column " + cm));
					return;
				}
			} else {
				columns.clear();
				handler.handle(new ResultMessage().error("invalid.column " + cm));
				return;
			}
		}
	}

	JsonArray getColumsNames(String profile, String[] strings, List<String> columns) {
		JsonArray errors = new fr.wseduc.webutils.collections.JsonArray();
		for (int j = 0; j < strings.length; j++) {
			String cm = columnsNameMapping(profile, strings[j]);
			if (profilesNamesMapping.get(profile).containsValue(cm)) {
				columns.add(j, cm);
			} else {
				errors.add(cm);
				return errors;
			}
		}
		return errors;
	}

	String columnsNameMapping(String profile, String columnName) {
		final String key = cleanKey(columnName);
		final Object attr = profilesNamesMapping.get(profile).get(key);
		return attr != null ? attr.toString() : key;
	}

	private static String cleanKey(String columnName) {
		return Validator.removeAccents(columnName.trim().toLowerCase())
				.replaceAll("\\s+", "").replaceAll("\\*", "").replaceAll("'", "").replaceFirst(CSVUtil.UTF8_BOM, "");
	}

	public JsonObject getColumsMapping(String profile, String[] strings) {
		JsonObject mapping = new JsonObject();
		for (String key : strings) {
			if (isEmpty(key)) {
				mapping.putString("", "ignore");
				continue;
			}
			String cm = columnsNameMapping(profile, key);
			if (profilesNamesMapping.get(profile).containsValue(cm)) {
				mapping.put(key, cm);
			} else {
				mapping.put(key, "");
			}
		}
		return mapping;
	}

	public JsonObject availableFields() {
		JsonObject j = new JsonObject();
		for (String profile : profilesNamesMapping.keySet()) {
			j.putArray(profile, new JsonArray(new HashSet<>(profilesNamesMapping.get(profile).values()).toArray()));
		}
		return j;
	}

}
