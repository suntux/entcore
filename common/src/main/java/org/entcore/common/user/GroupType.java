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

package org.entcore.common.user;

import java.util.Arrays;
import java.util.Optional;

public enum GroupType {

	ProfileGroup(1),
	FunctionalGroup(2),
	FunctionGroup(3),
	HTGroup(4),
	ManualGroup(5),
	CommunityGroup(6),
	DeleteGroup(7),
	RemoteGroup(8);

	private final int value;

	GroupType(int value) {
		this.value = value;
	}

	public static Optional<GroupType> valueOf(int value) {
		return Arrays.stream(values())
				.filter(type -> type.value == value)
				.findFirst();
	}

}
