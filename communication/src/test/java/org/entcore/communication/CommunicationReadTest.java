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

import io.restassured.http.ContentType;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.test.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class CommunicationReadTest extends AbstractTest {

	@Before
	public void setUp(TestContext context) throws IOException {
		setUp(context, TestCommunicationVerticle.class.getName(), "communication");
	}

	@After
	public void tearDown(TestContext context) {
		super.tearDown(context);
	}


	@Test
	public void checkComRulesRestAssured() {
		given()
				.header("Test-Login", "isabelle.polonio")
				.body("{\"search\":\"\",\"types\":[\"Group\"],\"structures\":[],\"classes\":[],\"profiles\":[],\"functions\":[],\"nbUsersInGroups\":true,\"groupType\":true}")
		.when()
				.post("/communication/visible")
		.then()
				.contentType(ContentType.JSON)
				.statusCode(200)
				.body("groups.size()", is(233))
				.body("users.size()", is(0));
	}

	@Test
	public void checkComRulesRestAssuredLoginHeader() {
		given()
				.header("Test-Login", "severine.alexandre")
				.body("{\"search\":\"\",\"types\":[],\"structures\":[],\"classes\":[],\"profiles\":[],\"functions\":[],\"nbUsersInGroups\":true,\"groupType\":true}")
				.when()
				.post("/communication/visible")
				.then()
				.contentType(ContentType.JSON)
				.statusCode(200)
				.body("groups.size()", is(234))
				.body("users.size()", is(3279));
	}

}
