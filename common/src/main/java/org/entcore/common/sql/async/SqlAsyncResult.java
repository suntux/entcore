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

package org.entcore.common.sql.async;

import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

import java.util.List;

public class SqlAsyncResult {

	public static Handler<AsyncResult<UpdateResult>> validUpdateHandler(final Handler<Either<String, JsonObject>> handler) {
		return ar -> {
			if (ar.succeeded()) {
				handler.handle(new Either.Right<>(ar.result().toJson()));
			} else {
				handler.handle(new Either.Left<>(ar.cause().getMessage()));
			}
		};
	}

	public static Handler<AsyncResult<ResultSet>> validResultHandler(final Handler<Either<String, JsonArray>> handler) {
		return ar -> {
			if (ar.succeeded()) {
				handler.handle(new Either.Right<>(new JsonArray(ar.result().getRows())));
			} else {
				handler.handle(new Either.Left<>(ar.cause().getMessage()));
			}
		};
	}

	public static Handler<AsyncResult<ResultSet>> validUniqueResultHandler(final Handler<Either<String, JsonObject>> handler) {
		return ar -> {
			if (ar.succeeded()) {
				List<JsonObject> rows = ar.result().getRows();
				if (rows.size() != 1) {
					handler.handle(new Either.Left<>("non.unique.result"));
				} else {
					handler.handle(new Either.Right<>(rows.get(0)));
				}
			} else {
				handler.handle(new Either.Left<>(ar.cause().getMessage()));
			}
		};
	}

}
