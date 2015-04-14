/*
 * ExampleJaxmppTest.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.tests;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.j2se.Jaxmpp;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static tigase.TestLogger.log;

public class ExampleJaxmppTest extends AbstractTest {

	@Test(groups = { "examples" }, description = "Simple test verifying logging in by the user")
	public void SimpleLoginTest() {

		try {
			log("This is test case");

			Jaxmpp contact = createJaxmppAdmin();

			contact.login( true );

			assertTrue(contact.isConnected(), "contact was not connected" );

			if (contact.isConnected()) {
				contact.disconnect();
			}

			BareJID createUserAccount = createUserAccount( "test_user" );
			Jaxmpp createJaxmpp = createJaxmpp( createUserAccount.getLocalpart(), createUserAccount);
			createJaxmpp.login();

			assertTrue(createJaxmpp.isConnected(), "contact was not connected" );

			if (createJaxmpp.isConnected()) {
				removeUserAccount( createJaxmpp);
			}


		} catch (Exception e) {
			fail(e);
		}
	}

}