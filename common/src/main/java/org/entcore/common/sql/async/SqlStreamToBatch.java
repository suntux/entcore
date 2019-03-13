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
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLRowStream;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class SqlStreamToBatch {

	private static final Logger log = LoggerFactory.getLogger(SqlStreamToBatch.class);

	private final AtomicInteger count = new AtomicInteger(0);
	private final StringBuilder batch = new StringBuilder();
	private SQLConnection conn;
	private SQLRowStream stream;
	private final SqlAsync sqlAsync;
	private String streamQuery;
	private String batchQuery;
	private Handler<Either<String, JsonObject>> handler;
	private int pageSize;
	private Function<JsonArray, String> transform;

	public SqlStreamToBatch(SqlAsync sqlAsync) {
		this.sqlAsync = sqlAsync;
	}

	public SqlStreamToBatch streamQuery(String streamQuery) {
		this.streamQuery = streamQuery;
		return this;
	}

	public SqlStreamToBatch batchQuery(String batchQuery) {
		this.batchQuery = batchQuery;
		return this;
	}

	public SqlStreamToBatch endHandler(Handler<Either<String, JsonObject>> endHandler) {
		this.handler = endHandler;
		return this;
	}

	public SqlStreamToBatch pageSize(int pageSize) {
		this.pageSize = pageSize;
		return this;
	}

	public SqlStreamToBatch transform(Function<JsonArray, String> transform) {
		this.transform = transform;
		return this;
	}

	public void execute() {
		sqlAsync.getSqlClient().getConnection(ar -> {
			if (ar.succeeded()) {
				conn = ar.result();
				conn.queryStream(streamQuery, ar2 -> {
					if (ar2.succeeded()) {
						stream = ar2.result();
						stream
								.exceptionHandler(e -> {
									log.error("Sql stream exception handler", e);
									handler.handle(new Either.Left<>(e.getMessage()));
								})
								.handler(processHandler())
								.endHandler(v -> close(false));
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

	private void close(boolean error) {
		log.info("end handler");
		conn.close();
		if (error) {
			handler.handle(new Either.Left<>("batch.error"));
		} else if (count.get() > 0) {
			sqlAsync.execute(batch.toString(), ar -> {
				if (ar.succeeded()) {
					log.info("Sql stream to batch successful");
					handler.handle(new Either.Right<>(new JsonObject()));
				} else {
					log.error("Sql stream to batch error", ar.cause());
					handler.handle(new Either.Left<>("batch.error"));
				}
			});
		} else {
			handler.handle(new Either.Right<>(new JsonObject()));
		}
	}

	private Handler<JsonArray> processHandler() {
		return row -> {
			if (row != null && row.size() > 0) {
				final String batchEntry;
				if (transform != null) {
					batchEntry = transform.apply(row);
				} else {
					batchEntry = String.format(batchQuery, row.getList().toArray());
				}
				batch.append(batchEntry);
				if (count.incrementAndGet() > pageSize) {
					stream.pause();
					sqlAsync.execute(batch.toString(), ar -> {
						if (ar.succeeded()) {
							batch.setLength(0);
							count.set(0);
							stream.resume();
						} else {
							log.error("Sql stream to batch error", ar.cause());
							stream.close();
							close(true);
						}
					});
				}
			}
		};
	}

}
