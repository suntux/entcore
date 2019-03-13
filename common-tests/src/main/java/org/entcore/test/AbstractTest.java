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

package org.entcore.test;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;

import java.io.IOException;
import java.net.ServerSocket;

public class AbstractTest {

	protected Vertx vertx;
	protected Integer port;

	public void setUp(TestContext context, String testVerticle, String pathPrefix) throws IOException {
		vertx = Vertx.vertx();
		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();

		DeploymentOptions options = new DeploymentOptions()
				.setConfig(new JsonObject().put("port", port).put("path-prefix", pathPrefix)
						.put("neo4jConfig", new JsonObject().put("server-uri", "http://localhost:7474/db/data/"))
						.put("sqlasync", true)
						.put("postgresConfig", new JsonObject()
								.put("host", "localhost")
								.put("port", 5432)
								.put("database", "ong")
								.put("username", "web-education")
								.put("password", "We_1234")
						));

		vertx.deployVerticle(testVerticle, options, context.asyncAssertSuccess());

		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;
	}

	public void tearDown(TestContext context) {
		RestAssured.reset();
		vertx.close(context.asyncAssertSuccess());
	}

}
