/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.tests.jaxmpp;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.utils.Account;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.testng.Assert.*;

/**
 * Created by andrzej on 17.09.2016.
 */
public class TestHandlingOfConnectionIssues
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	@Test
	public void testStateAfterAuthTimeoutWebSocket() throws Exception {
		testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType.websocket);
	}

	@Test
	public void testStateAfterAuthTimeoutBosh() throws Exception {
		testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType.bosh);
	}

	@Test
	public void testStateAfterAuthTimeoutSocket() throws Exception {
		testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType.socket);
	}

	@Test
	public void testStateAfterConnectionFailureWebSocket() throws Exception {
		testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType.websocket);
	}

	@Test
	public void testStateAfterConnectionFailureBosh() throws Exception {
		testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType.bosh);
	}

	@Test
	public void testStateAfterConnectionFailureSocket() throws Exception {
		testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType.socket);
	}

	@BeforeMethod
	protected void setUp() throws Exception {
		user = createAccount().setLogPrefix("jaxmpp_").build();
		jaxmpp = user.createJaxmpp().build();
	}

	private void testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType connectionType) throws Exception {
		jaxmpp.getConnectionConfiguration().setConnectionType(connectionType);

		AuthModule authModule = jaxmpp.getModulesManager().getModule(AuthModule.class);
		jaxmpp.getModulesManager().unregister(authModule);

		AuthModule dummyAuthModule = new AuthModule() {
			@Override
			public void login() throws JaxmppException {

			}
		};
		jaxmpp.getModulesManager().register(dummyAuthModule);
		Field f = jaxmpp.getModulesManager().getClass().getDeclaredField("modulesByClasses");
		f.setAccessible(true);
		((HashMap) f.get(jaxmpp.getModulesManager())).put(AuthModule.class, dummyAuthModule);

		switch (connectionType) {
			case websocket:
				jaxmpp.getConnectionConfiguration().setBoshService(getWebSocketURI());
				break;
			case bosh:
				jaxmpp.getConnectionConfiguration().setBoshService(getBoshURI());
				break;
			case socket:
				jaxmpp.getConnectionConfiguration().setServer(getInstanceHostname());
				break;
		}

		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		assertEquals(jaxmpp.getConnector().getState(), Connector.State.disconnected);

		jaxmpp.getModulesManager().unregister(dummyAuthModule);
		jaxmpp.getModulesManager().register(authModule);

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());

		jaxmpp.disconnect();
	}

	private void testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType connectionType)
			throws Exception {
		assertNull(jaxmpp.getSessionObject().getProperty(Jaxmpp.EXCEPTION_KEY));

		jaxmpp.getConnectionConfiguration().setConnectionType(connectionType);

		switch (connectionType) {
			case websocket:
				jaxmpp.getConnectionConfiguration().setBoshService("ws://missing/");
				break;
			case bosh:
				jaxmpp.getConnectionConfiguration().setBoshService("http://missing/");
				break;
			case socket:
				jaxmpp.getConnectionConfiguration().setServer("missing");
				break;
		}

		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
			assertNotNull(ex);
		}

		assertNull(jaxmpp.getSessionObject().getProperty(Jaxmpp.EXCEPTION_KEY));

		assertEquals(jaxmpp.getConnector().getState(), Connector.State.disconnected);

		switch (connectionType) {
			case websocket:
				jaxmpp.getConnectionConfiguration().setBoshService(getWebSocketURI());
				break;
			case bosh:
				jaxmpp.getConnectionConfiguration().setBoshService(getBoshURI());
				break;
			case socket:
				jaxmpp.getConnectionConfiguration().setServer(getInstanceHostname());
				break;
		}

		jaxmpp.getSessionObject().clear();

		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
			assertNull(ex);
		}

		assertTrue(jaxmpp.isConnected());

		jaxmpp.disconnect(true);
	}

}
