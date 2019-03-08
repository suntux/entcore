/*
 * Copyright © "Open Digital Education", 2019
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.communication.services.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.entcore.communication.services.CommunicationService;
import org.entcore.communication.utils.SqlAsync;
import org.entcore.communication.utils.SqlStreamToBatch;


import java.util.List;
import java.util.stream.Collectors;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class SqlCommunicationService implements CommunicationService {

	private static final int LIMIT = 1000;
	private final SqlAsync sqlAsync = SqlAsync.getInstance();
	private static final Logger log = LoggerFactory.getLogger(SqlCommunicationService.class);
	private static final String WRITE_COM_GROUPS =
			"INSERT INTO communication.communications (user_id,dest_group_id,distance) VALUES ('%s','%s',%d) " +
			"ON CONFLICT (user_id,dest_group_id) DO NOTHING; ";
	private static final String WRITE_COM_USERS =
			"INSERT INTO communication.communications (user_id,dest_user_id,distance) VALUES ('%s','%s',1) " +
			"ON CONFLICT (user_id,dest_user_id) DO NOTHING; ";

	@Override
	public void addLink(String startGroupId, String endGroupId, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void removeLink(String startGroupId, String endGroupId, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void addLinkWithUsers(String groupId, Direction direction, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void removeLinkWithUsers(String groupId, Direction direction, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void communiqueWith(String groupId, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void addLinkBetweenRelativeAndStudent(String groupId, Direction direction, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void removeLinkBetweenRelativeAndStudent(String groupId, Direction direction, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void initDefaultRules(JsonArray structureIds, JsonObject defaultRules, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void applyDefaultRules(JsonArray structureIds, Handler<Either<String, JsonObject>> handler) {
		final String query1 =
				"SELECT gu.user_id, gu.group_id, g.communique_with " +
				"FROM directory.groups_users gu " +
				"JOIN directory.groups g ON gu.group_id = g.id " +
				"WHERE g.communique_user IN ('B','I') ";
		new SqlStreamToBatch(sqlAsync).streamQuery(query1).transform(this::groupComBatch).pageSize(LIMIT).endHandler(r -> {
			if (r.isRight()) {
				sqlAsync.execute(
						"DELETE FROM communication.communications " +
						"WHERE dest_group_id NOT IN (SELECT g.id FROM directory.groups g);",
						ar -> {
							if (ar.succeeded()) {
								//handler.handle(new Either.Right<>(new JsonObject()));
								final String query2 =
										"SELECT sr.student_id, sr.relative_id, g.communique_relative_student " +
										"FROM directory.students_relatives sr " +
										"JOIN directory.groups_users gu ON sr.relative_id = gu.user_id " +
										"JOIN directory.groups g ON gu.group_id = g.id " +
										"WHERE g.communique_relative_student IS NOT NULL ";
								new SqlStreamToBatch(sqlAsync).streamQuery(query2).transform(this::studentsRelativeComBatch)
										.pageSize(LIMIT).endHandler(handler).execute();
							} else {
								log.error("Error when delete not exist groups", ar.cause());
								handler.handle(new Either.Left<>("error.delete.not.exists.groups"));
							}
						});
			} else {
				handler.handle(r);
			}
		}).execute();
	}

	private String studentsRelativeComBatch(JsonArray row) {
		final StringBuilder sb = new StringBuilder();
		if (row.size() == 3) {
			final String direction = row.getString(2);
			if ("B".equals(direction) || "I".equals(direction)) {
				sb.append(String.format(WRITE_COM_USERS, row.getString(0), row.getString(1)));
			}
			if ("B".equals(direction) || "O".equals(direction)) {
				sb.append(String.format(WRITE_COM_USERS, row.getString(1), row.getString(0)));
			}
		}
		return sb.toString();
	}

	private String groupComBatch(JsonArray row) {
		final StringBuilder sb = new StringBuilder();
		if (row.size() == 3) {
			sb.append(String.format(WRITE_COM_GROUPS, row.getString(0), row.getString(1), 1));
			final String cw = row.getString(2);
			if (isNotEmpty(cw)) {
				new JsonArray(cw).stream().forEach(g -> sb.append(
						String.format(WRITE_COM_GROUPS, row.getString(0), g, 2)));
			}
		}
		return sb.toString();
	}

	@Override
	public void applyRules(String groupId, Handler<Either<String, JsonObject>> responseHandler) {

	}

	@Override
	public void removeRules(String structureId, Handler<Either<String, JsonObject>> handler) {

	}

	@Override
	public void visibleUsers(String userId, String structureId, JsonArray expectedTypes, boolean itSelf, boolean myGroup,
			boolean profile, String preFilter, String customReturn, JsonObject additionnalParams,
			Handler<Either<String, JsonArray>> handler) {
		visibleUsers(userId, structureId, expectedTypes, itSelf, myGroup, profile, preFilter, customReturn,
				additionnalParams, null, handler);
	}

	@Override
	public void visibleUsers(String userId, String structureId, JsonArray expectedTypes, boolean itSelf, boolean myGroup,
			boolean profile, String preFilter, String customReturn, JsonObject additionnalParams,
			String userProfile, Handler<Either<String, JsonArray>> handler) {
		final String queryUsers =
				"SELECT u.id, u.display_name as \"displayName\", u.profile " +
				"FROM communication.communications c " +
				"JOIN directory.groups g ON c.dest_group_id = g.id " +
				"JOIN directory.groups_users gu ON g.id = gu.group_id " +
				"JOIN directory.users u ON gu.user_id = u.id " +
				"WHERE c.user_id = ? AND g.communique_user IN ('B','O') " +
				"UNION " +
				"SELECT u.id, u.display_name as \"displayName\", u.profile " +
				"FROM communication.communications c " +
				"JOIN directory.users u ON c.dest_user_id = u.id " +
				"WHERE c.user_id = ? ";
		final Future<ResultSet> fu = sqlAsync.query(queryUsers, new JsonArray().add(userId).add(userId));
		final String queryGroups =
				"SELECT g.id, g.name " +
				"FROM communication.communications c " +
				"JOIN directory.groups g ON c.dest_group_id = g.id " +
				"WHERE c.user_id = ? ";
		final Future<ResultSet> fg = sqlAsync.query(queryGroups, new JsonArray().add(userId));
		CompositeFuture.all(fu, fg).setHandler(ar -> {
			if (ar.succeeded()) {
				final List<JsonObject> results = ar.result().<ResultSet>list().stream()
						.flatMap(r -> r.getRows().stream())
						.collect(Collectors.toList());
				handler.handle(new Either.Right<>(new JsonArray(results)));
//				final List<ResultSet> results = ar.result().list();
//				if (results.size() == 2) {
//					final JsonObject r = new JsonObject();
//							.put("groups", new JsonArray(results.get(1).getRows()))
//							.put("users", new JsonArray(results.get(0).getRows()));
//					handler.handle(new Either.Right<>(r));
//				} else {
//					handler.handle(new Either.Left<>("get.com.result.error"));
//				}
			} else {
				log.error("Get communications rules error", ar.cause());
				handler.handle(new Either.Left<>("get.com.error"));
			}
		});
		//Future<List<ResultSet>> results = CompositeFuture.all(fu, fg).map(CompositeFuture::list);

	}

	@Override
	public void usersCanSeeMe(String userId, Handler<Either<String, JsonArray>> handler) {

	}

	@Override
	public void visibleProfilsGroups(String userId, String customReturn, JsonObject additionnalParams, String preFilter, Handler<Either<String, JsonArray>> handler) {

	}

	@Override
	public void visibleManualGroups(String userId, String customReturn, JsonObject additionnalParams, Handler<Either<String, JsonArray>> handler) {

	}

}
