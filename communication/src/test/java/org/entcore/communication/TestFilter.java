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

package org.entcore.communication;

import fr.wseduc.webutils.request.filter.Filter;
import fr.wseduc.webutils.security.SecureHttpServerRequest;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class TestFilter implements Filter {

	private final JsonObject testSession;

	public TestFilter(JsonObject testSession) {
		this.testSession = testSession;
	}

	@Override
	public void canAccess(HttpServerRequest request, Handler<Boolean> handler) {
		if (request instanceof SecureHttpServerRequest) {
			String testLogin = request.getHeader("Test-Login");
			if (isNotEmpty(testLogin)) {
				String queryFakeSession =
						"MATCH (u:User {login : {login}}) " +
						"RETURN u.id as userId, u.login as login, u.lastName as lastName, " +
						"u.firstName as firstName, u.externalId as externalId, u.displayName as username, " +
						"HEAD(u.profiles) as type";
				request.pause();
				Neo4j.getInstance().execute(queryFakeSession, new JsonObject().put("login", testLogin), m -> {
					final JsonArray res = m.body().getJsonArray("result");
					if ("ok".equals(m.body().getString("status")) && res != null && res.size() == 1) {
						((SecureHttpServerRequest) request).setSession(res.getJsonObject(0));
					}
					request.resume();
					handler.handle(true);
				});
			} else {
				((SecureHttpServerRequest) request).setSession(testSession);
				handler.handle(true);
			}
		} else {
			handler.handle(true);
		}
	}

	@Override
	public void deny(HttpServerRequest httpServerRequest) {

	}

}
