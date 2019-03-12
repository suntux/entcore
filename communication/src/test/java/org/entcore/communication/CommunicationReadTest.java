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

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ServerSocket;

import static io.restassured.RestAssured.*;
import static io.restassured.matcher.RestAssuredMatchers.*;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class CommunicationReadTest {

	private Vertx vertx;
	private Integer port;

	@Before
	public void setUp(TestContext context) throws IOException {
		vertx = Vertx.vertx();
		//vertx.fileSystem().mkdir("securedaction", null);
		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();

		DeploymentOptions options = new DeploymentOptions()
				.setConfig(new JsonObject().put("port", port).put("path-prefix", "communication")
						.put("test-session", new JsonObject().put("userId", "91c22b66-ba1b-4fde-a3fe-95219cc18d4a"))
						.put("postgres-conf", new JsonObject()
								.put("host", "localhost")
								.put("port", 5432)
								.put("database", "ong")
								.put("username", "web-education")
								.put("password", "We_1234")
				));

		vertx.deployVerticle(TestCommunicationVerticle.class.getName(), options, context.asyncAssertSuccess());

		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;
	}

	@After
	public void tearDown(TestContext context) {
		RestAssured.reset();
		vertx.close(context.asyncAssertSuccess());
	}

	@Test
	public void checkComRules(TestContext context) {
		Async async = context.async();

		vertx.createHttpClient().post(port, "localhost", "/communication/visible")
				.putHeader("content-type", "application/json")
				.handler(response -> {
					context.assertEquals(response.statusCode(), 200);
					context.assertTrue(response.headers().get("content-type").contains("application/json"));
					response.bodyHandler(body -> {
						System.out.println(new JsonObject(body).encode());
						async.complete();
					});
				})
				.end("{\"search\":\"\",\"types\":[\"Group\"],\"structures\":[],\"classes\":[],\"profiles\":[],\"functions\":[],\"nbUsersInGroups\":true,\"groupType\":true}");
	}

	@Test
	public void checkComRulesRestAssured() {
		given()
				.body("{\"search\":\"\",\"types\":[\"Group\"],\"structures\":[],\"classes\":[],\"profiles\":[],\"functions\":[],\"nbUsersInGroups\":true,\"groupType\":true}")
		.when()
				.post("/communication/visible")
		.then()
				.contentType(ContentType.JSON)
				.statusCode(200)
				.body("groups.size()", is(233))
				.body("users.size()", is(0));
	}

}
