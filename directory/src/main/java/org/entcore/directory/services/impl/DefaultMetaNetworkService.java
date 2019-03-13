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

package org.entcore.directory.services.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.async.SqlAsync;
import org.entcore.directory.services.MetaNetworkService;

import static org.entcore.common.sql.async.SqlAsyncResult.validResultHandler;
import static org.entcore.common.sql.async.SqlAsyncResult.validUniqueResultHandler;
import static org.entcore.common.sql.async.SqlAsyncResult.validUpdateHandler;

public class DefaultMetaNetworkService implements MetaNetworkService {

	private final SqlAsync sqlAsync = SqlAsync.getInstance();

	@Override
	public void createNode(JsonObject node, Handler<Either<String, JsonObject>> handler) {
		sqlAsync.insert("directory.remote_nodes", node, "id", validUniqueResultHandler(handler));
	}

	@Override
	public void deleteNode(int nodeId, Handler<Either<String, JsonObject>> handler) {
		sqlAsync.updateWithParams("DELETE FROM directory.remote_nodes WHERE id = ?",
				new JsonArray().add(nodeId), validUpdateHandler(handler));
	}

	@Override
	public void listNodes(Handler<Either<String, JsonArray>> handler) {
		sqlAsync.query("SELECT * FROM directory.remote_nodes", validResultHandler(handler));
	}

}
