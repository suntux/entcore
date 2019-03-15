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

import com.google.testing.compile.JavaFileObjects;
import fr.wseduc.webutils.Controller;
import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import org.entcore.common.http.BaseServer;
import org.entcore.common.processor.ControllerAnnotationProcessor;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AbstractTest {

	protected Vertx vertx;
	protected Integer port;

	public void setUp(TestContext context, Class<? extends BaseServer> testVerticle, String pathPrefix,
			String testResourceDirectory, Class<? extends Controller>... controllerClasses) throws IOException {
		List<JavaFileObject> compilationUnits = new ArrayList<>();
		for (Class<? extends Controller> clazz: controllerClasses) {
			String dir = clazz.getProtectionDomain().getCodeSource().getLocation().getPath()
					.replaceAll("out/production/classes/", "");
			compilationUnits.add(JavaFileObjects.forResource(new URL("file://" + dir + "src/main/java/" +
					clazz.getName().replaceAll("\\.", "/") + ".java"
					)));
		}
		if (compilationUnits.size() > 0) {
			compile(new ControllerAnnotationProcessor(), testResourceDirectory, compilationUnits);
		}
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

	protected void compile(Processor processor, String testResourceDirectory,
			List<JavaFileObject> compilationUnits) throws IOException {
		new File(testResourceDirectory).mkdirs();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
		fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(testResourceDirectory)));

		JavaCompiler.CompilationTask task = compiler.getTask(
				null, fileManager, diagnostics, null, null, compilationUnits);
		task.setProcessors(Arrays.asList(processor));
		task.call();
		for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
			System.err.println(diagnostic);
		}
	}

}
