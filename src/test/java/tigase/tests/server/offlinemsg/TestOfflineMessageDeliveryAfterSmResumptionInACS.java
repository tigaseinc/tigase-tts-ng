/*
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests.server.offlinemsg;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.connector.ConnectorWrapper;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.streammng.StreamManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;

/**
 * Created by andrzej on 22.06.2016.
 */
public class TestOfflineMessageDeliveryAfterSmResumptionInACS extends AbstractTest {

	private static final String USER_PREFIX = "sm-resumption";

	private Account user1;
	private Account user2;

	private Jaxmpp user1Jaxmpp;
	private Jaxmpp user2Jaxmpp;

	@BeforeMethod
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			return configureJaxmpp(jaxmpp, 0);
		}).setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getConnectionConfiguration().setResource("test-x");
			jaxmpp.getModulesManager().register(new StreamManagementModule(jaxmpp));
			return configureJaxmpp(jaxmpp, 1);
		}).setConnected(true).build();
	}

	private Jaxmpp configureJaxmpp(Jaxmpp jaxmpp, int pos) {
		String[] hostnames = getInstanceHostnames();
		if (hostnames.length > 1) {
			jaxmpp.getSessionObject().setUserProperty("BOSH#SEE_OTHER_HOST_KEY", false);
			jaxmpp.getConnectionConfiguration().setServer(hostnames[pos]);
		}
		return jaxmpp;
	}

	@Test
	public void testFullJid() throws Exception {
		 testMessageDeliveryReliability(true);
	}

	@Test
	public void testBareJid() throws Exception {
		testMessageDeliveryReliability(false);
	}

	public void testMessageDeliveryReliability(boolean fullJid) throws Exception {
		final Mutex mutex = new Mutex();

		log( "\n\n\n===== simulation of connection failure \n" );
		breakConnection(user2Jaxmpp);

		log( "\n\n\n===== reconnecting client (resumption of stream or binding using same resource) \n" );
		user2Jaxmpp.login(true);

		log( "\n\n\n===== disconnecting client (proper disconnection) \n");
		Thread.sleep(2000);
		user2Jaxmpp.disconnect(true);

		Thread.sleep(2000);

		JID destination = fullJid ? JID.jidInstance(user2.getJid(), "test-x") : JID.jidInstance(user2.getJid());
		log( "\n\n\n===== sending dummy message so client will discover it is disconnected (workaround) \n" );
		sendMessage(user1Jaxmpp, destination, StanzaType.chat, "test1");

		String body = UUID.randomUUID().toString();

		log( "\n\n\n===== sending message to look for \n" );
		sendMessage(user1Jaxmpp, destination, StanzaType.chat, body);

		//Thread.sleep(delay + 65000);
		Thread.sleep(1000);

		user2Jaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
				new MessageModule.MessageReceivedHandler() {

					@Override
					public void onMessageReceived(SessionObject sessionObject, Chat chat, Message message) {
						try {
							mutex.notify("message:" + message.getBody());
						} catch (XMLException e) {
							e.printStackTrace();
						}
					}
				});

		user2Jaxmpp.getModule(PresenceModule.class).setInitialPresence(false);

		log( "\n\n\n===== reconnecting client (resumption of stream or binding using same resource) \n" );
		user2Jaxmpp.login(true);

		Thread.sleep(2000);

		log( "\n\n\n===== broadcasting presence \n" );
		user2Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, 5);

		mutex.waitFor(5 * 1000, "message:" + body);
		assertTrue("Message was not delivered!", mutex.isItemNotified("message:" + body));
	}

	public void breakConnection(Jaxmpp jaxmpp) throws IllegalAccessException, NoSuchFieldException {
		SocketConnector connector = (SocketConnector) ((ConnectorWrapper) jaxmpp.getConnector()).getConnector();
		Field socketField = connector.getClass().getDeclaredField("socket");
		socketField.setAccessible(true);
		socketField.set(connector, null);
		Field outputStreamField = connector.getClass().getDeclaredField("writer");
		outputStreamField.setAccessible(true);
		outputStreamField.set(connector, null);
		Field readerField = connector.getClass().getDeclaredField("reader");
		readerField.setAccessible(true);
		readerField.set(connector, null);
		try {
			Method m = connector.getClass().getDeclaredMethod("onStreamTerminate");
			m.setAccessible(true);
			m.invoke(connector);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		assertEquals(Connector.State.disconnected, jaxmpp.getConnector().getState());

	}

	private void sendMessage(Jaxmpp jaxmpp, JID destination, StanzaType type, String body) throws Exception {
		Message m = Message.create();
		m.setBody(body);
		m.setType(type);
		m.setTo(destination);
		jaxmpp.send(m);
	}

}