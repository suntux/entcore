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

import fr.wseduc.webutils.DefaultAsyncResult;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.*;
import org.entcore.common.validation.ValidationException;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fr.wseduc.webutils.Utils.isEmpty;

public class SqlAsync {

	private SQLClient sqlClient;
	private static final Logger log = LoggerFactory.getLogger(SqlAsync.class);

	private SqlAsync() {}

	private static class SqlAsyncHolder {
		private static final SqlAsync instance = new SqlAsync();
	}

	public static SqlAsync getInstance() {
		return SqlAsyncHolder.instance;
	}

	public void init(SQLClient sqlClient) {
		this.sqlClient = sqlClient;
	}

	public void queryStream(String query, Function<SQLRowStream, Handler<JsonArray>> rowFunction,
			Handler<Either<String, JsonObject>> handler) {
		queryStream(query, rowFunction, v -> handler.handle(new Either.Right<>(new JsonObject())), handler);
	}

	public void queryStream(String query, Function<SQLRowStream, Handler<JsonArray>> rowFunction,
			Handler<Void> endHandler, Handler<Either<String, JsonObject>> handler) {
		sqlClient.getConnection(ar -> {
			if (ar.succeeded()) {
				final SQLConnection conn = ar.result();
				conn.queryStream(query, ar2 -> {
					if (ar2.succeeded()) {
						final SQLRowStream stream = ar2.result();
						stream
								.exceptionHandler(e -> {
									log.error("Sql stream exception handler", e);
									handler.handle(new Either.Left<>(e.getMessage()));
								})
								.handler(rowFunction.apply(stream))
								.endHandler(v -> {
									conn.close();
									if (endHandler != null) {
										endHandler.handle(v);
									}
								});
					} else {
						log.error("Sql row stream error", ar2.cause());
						handler.handle(new Either.Left<>(ar2.cause().getMessage()));
					}
				});
			} else {
				log.error("Sql connection error", ar.cause());
				handler.handle(new Either.Left<>(ar.cause().getMessage()));
			}
		});
	}

	public void execute(String query, Handler<AsyncResult<Void>> handler) {
		sqlClient.getConnection(ar -> {
			if (ar.succeeded()) {
				final SQLConnection conn = ar.result();
				conn.execute(query, v -> {
					conn.close();
					if (handler != null) {
						handler.handle(v);
					}
				});
			} else {
				handler.handle(new DefaultAsyncResult<>(ar.cause()));
			}
		});
	}

	public void query(String query, Handler<AsyncResult<ResultSet>> handler) {
		sqlClient.getConnection(ar -> {
			if (ar.succeeded()) {
				final SQLConnection conn = ar.result();
				conn.query(query, ar2 -> {
					conn.close();
					handler.handle(ar2);
				});
			} else {
				handler.handle(new DefaultAsyncResult<>(ar.cause()));
			}
		});
	}

	public Future<ResultSet> query(String query) {
		final Future<ResultSet> future = Future.future();
		query(query, ar -> {
			if (ar.succeeded()) {
				future.complete(ar.result());
			} else {
				future.fail(ar.cause());
			}
		});
		return future;
	}

	public void query(String query, JsonArray params, Handler<AsyncResult<ResultSet>> handler) {
		sqlClient.getConnection(ar -> {
			if (ar.succeeded()) {
				final SQLConnection conn = ar.result();
				conn.queryWithParams(query, params, ar2 -> {
					conn.close();
					handler.handle(ar2);
				});
			} else {
				handler.handle(new DefaultAsyncResult<>(ar.cause()));
			}
		});
	}

	public Future<ResultSet> query(String query, JsonArray params) {
		final Future<ResultSet> future = Future.future();
		query(query, params, ar -> {
			if (ar.succeeded()) {
				future.complete(ar.result());
			} else {
				future.fail(ar.cause());
			}
		});
		return future;
	}

	public void insert(String table, JsonObject object, String returning, Handler<AsyncResult<ResultSet>> handler) {
		if (isEmpty(table) || object == null || object.isEmpty()) {
			handler.handle(new DefaultAsyncResult<>(new ValidationException("empty.fields")));
			return;
		}
		final JsonArray params = new JsonArray(object.stream().map(Map.Entry::getValue).collect(Collectors.toList()));
		final StringBuilder sb = new StringBuilder("INSERT INTO ")
				.append(table)
				.append(" (");
		for (Object o : object.fieldNames()) {
			if (!(o instanceof String)) continue;
			sb.append(escapeField((String) o)).append(",");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append(") VALUES (");
		for (int i = 0; i < params.size(); i++) {
			sb.append("?,");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append(")");
		if (returning != null) {
			sb.append(" RETURNING ").append(returning);
		}
		sqlClient.queryWithParams(sb.toString(), params, handler);
	}

	private String escapeField(String str) {
		return "\"" + str.replace("\"", "\"\"") + "\"";
	}

	public void updateWithParams(String sql, JsonArray params, Handler<AsyncResult<UpdateResult>> handler) {
		sqlClient.updateWithParams(sql, params, handler);
	}

	public void queryWithParams(String sql, JsonArray arguments, Handler<AsyncResult<ResultSet>> handler) {
		sqlClient.queryWithParams(sql, arguments, handler);
	}

	public SQLClient getSqlClient() {
		return sqlClient;
	}

}
