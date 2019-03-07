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
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.SQLRowStream;
import org.entcore.communication.services.CommunicationService;
import org.entcore.communication.utils.SqlAsync;
import org.entcore.communication.utils.SqlStreamToBatch;

import java.util.concurrent.atomic.AtomicInteger;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class SqlCommunicationService implements CommunicationService {

	private static final int LIMIT = 1000;
	private final SqlAsync sqlAsync = SqlAsync.getInstance();
	private static final Logger log = LoggerFactory.getLogger(SqlCommunicationService.class);

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
		new SqlStreamToBatch(sqlAsync).streamQuery(query1).transform(this::transform1)
				.pageSize(LIMIT).endHandler(handler).execute();
	}

	private String transform1(JsonArray row) {
		final String batch1 =
				"INSERT INTO communication.communications (user_id,dest_group_id,distance) VALUES ('%s','%s',%d) " +
				"ON CONFLICT (user_id,dest_group_id) DO NOTHING; ";
//		final String batch2 =
//				"INSERT INTO communication.communications (user_id,tmp_group_id,distance) VALUES ('%s','%s',%d) " +
//						"ON CONFLICT (user_id,tmp_group_id) DO NOTHING; ";
		final StringBuilder sb = new StringBuilder();
		if (row.size() == 3) {
			sb.append(String.format(batch1, row.getString(0), row.getString(1), 1));
			final String cw = row.getString(2);
			if (isNotEmpty(cw)) {
				new JsonArray(cw).stream().forEach(g -> sb.append(
						String.format(batch1, row.getString(0), g, 2)));
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
	public void visibleUsers(String userId, String structureId, JsonArray expectedTypes, boolean itSelf, boolean myGroup, boolean profile, String preFilter, String customReturn, JsonObject additionnalParams, Handler<Either<String, JsonArray>> handler) {

	}

	@Override
	public void visibleUsers(String userId, String structureId, JsonArray expectedTypes, boolean itSelf, boolean myGroup, boolean profile, String preFilter, String customReturn, JsonObject additionnalParams, String userProfile, Handler<Either<String, JsonArray>> handler) {

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
