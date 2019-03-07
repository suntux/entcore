/* Copyright © "Open Digital Education", 2014
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
 */

package org.entcore.communication;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLClient;
import org.entcore.common.http.BaseServer;
import org.entcore.communication.controllers.CommunicationController;
import org.entcore.communication.filters.CommunicationFilter;
import org.entcore.communication.services.CommunicationService;
import org.entcore.communication.services.impl.DefaultCommunicationService;
import org.entcore.communication.services.impl.SqlCommunicationService;
import org.entcore.communication.utils.SqlAsync;

public class Communication extends BaseServer {

	@Override
	public void start() throws Exception {
		super.start();

		// TODO move this conf to load in server map from starter
		final JsonObject postgresConf = config.getJsonObject("postgres-conf");


		CommunicationController communicationController = new CommunicationController();
		CommunicationService communicationService;
		if (postgresConf != null && !postgresConf.isEmpty()) {
			SQLClient postgresSQLClient = PostgreSQLClient.createShared(vertx, postgresConf);
			SqlAsync.getInstance().init(postgresSQLClient);
			communicationService = new SqlCommunicationService();
		} else {
			communicationService = new DefaultCommunicationService();
		}
		communicationController.setCommunicationService(communicationService);
		addController(communicationController);
		setDefaultResourceFilter(new CommunicationFilter());
	}

}
