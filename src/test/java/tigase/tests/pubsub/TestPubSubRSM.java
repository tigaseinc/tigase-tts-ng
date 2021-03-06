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
package tigase.tests.pubsub;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class TestPubSubRSM
		extends AbstractJaxmppTest {

	private static final String PUBSUB_XMLNS = "http://jabber.org/protocol/pubsub";
	private static final String PUBSUB_OWNER_XMLNS = "http://jabber.org/protocol/pubsub#owner";

	protected Account account;
	protected Jaxmpp jaxmpp;
	protected JID pubsubJid;
	String parentNodeId;

	private List<String> allNodes = new ArrayList<>();
	private List<String> nodes = new ArrayList<>();

	@BeforeClass
	public void setUp() throws Exception {
		Mutex mutex = new Mutex();
		pubsubJid = JID.jidInstance("pubsub." + getDomain(0));
		account = createAccount().setLogPrefix("pubsub_rsm").setRegister(true).build();
		jaxmpp = account.createJaxmpp().setConfigurator(jaxmpp1 -> {
			jaxmpp1.getModulesManager().register(new DiscoveryModule());
			return jaxmpp1;
		}).setConnected(true).build();

		parentNodeId = "parent-node_" + UUID.randomUUID().toString();
		createNode(mutex, jaxmpp, parentNodeId, nodeCfg -> {
			setNodeConfiguration(nodeCfg, "pubsub#node_type", "collection");
		});

		final int nodesToCreate = 30;
		for (int i = 1; i <= nodesToCreate; i++) {
			String id = String.format("node-%1$03d__", i) + UUID.randomUUID().toString();
			createNode(mutex, jaxmpp, id, jabberDataElement -> {
				setNodeConfiguration(jabberDataElement, "pubsub#collection", parentNodeId);
			});
			nodes.add(id);
		}

		Thread.sleep(500);

		DiscoveryModule discoveryModule = jaxmpp.getModule(DiscoveryModule.class);
		discoveryModule.getItems(pubsubJid, parentNodeId, new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				TestLogger.log("Received node items: " + items.size());
				assertEquals("Received incorrect number of items", nodesToCreate, items.size());
				items.stream().map(DiscoveryModule.Item::getNode).forEach(allNodes::add);
				mutex.notify("disco:items:success", "disco:items");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:items:error", "disco:items");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:items:timeout", "disco:items");
			}
		});
		mutex.waitFor(10 * 1000, "disco:items");
		assertTrue(mutex.isItemNotified("disco:items:success"));
	}

	@AfterClass
	public void tearDown() throws Exception {
		Mutex mutex = new Mutex();
		nodes.forEach(id -> {
			try {
				deleteNode(mutex, jaxmpp, id);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		deleteNode(mutex, jaxmpp, parentNodeId);
	}

	@Test
	public void testRSM_noRSM() throws InterruptedException, JaxmppException {
		Mutex mutex = new Mutex();
		DiscoveryModule discoModule = jaxmpp.getModule(DiscoveryModule.class);
		discoModule.getItems(pubsubJid, parentNodeId, new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items, RSM rsm)
					throws XMLException {
				mutex.notify("disco:items:" + items.size() + ":rsm:" + rsm, "disco:items");
			}

			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				throw new RuntimeException();
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:items:error:" + error, "disco:items");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:items:error:timeout", "disco:items");
			}
		});

		mutex.waitFor(10 * 1000, "disco:items");
		assertTrue(mutex.isItemNotified("disco:items:" + allNodes.size() + ":rsm:" + null));
	}

	@Test
	public void testRSM_RSM() throws InterruptedException, JaxmppException {
		Mutex mutex = new Mutex();
		DiscoveryModule discoModule = jaxmpp.getModule(DiscoveryModule.class);
		RSM queryRsm = new RSM();
		queryRsm.setMax(10);
		AtomicReference<String> last = new AtomicReference<>();
		discoModule.getItems(pubsubJid, parentNodeId, queryRsm, new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items, RSM rsm)
					throws XMLException {
				ListIterator<DiscoveryModule.Item> it = items.listIterator();
				while (it.hasNext()) {
					int index = it.nextIndex();
					DiscoveryModule.Item item = it.next();
					mutex.notify("disco:item:1:" + index + ":" + item.getNode());
				}
				last.set(rsm.getLast());
				mutex.notify(
						"disco:items:1:" + items.size() + ":rsm:count:" + rsm.getCount() + ":index:" + rsm.getIndex(),
						"disco:items:1");
			}

			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				throw new RuntimeException();
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:items:1:error:" + error, "disco:items:1");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:items:1:error:timeout", "disco:items:1");
			}
		});

		mutex.waitFor(10 * 1000, "disco:items:1");
		assertTrue(mutex.isItemNotified(
				"disco:items:1:" + queryRsm.getMax() + ":rsm:count:" + allNodes.size() + ":index:0"));

		ListIterator<String> allNodesIter = allNodes.subList(0, 10).listIterator();
		while (allNodesIter.hasNext()) {
			int index = allNodesIter.nextIndex();
			assertTrue(mutex.isItemNotified("disco:item:1:" + index + ":" + allNodesIter.next()));
		}

		queryRsm = new RSM();
		queryRsm.setAfter(last.get());
		queryRsm.setMax(15);
		discoModule.getItems(pubsubJid, parentNodeId, queryRsm, new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items, RSM rsm)
					throws XMLException {
				ListIterator<DiscoveryModule.Item> it = items.listIterator();
				while (it.hasNext()) {
					int index = it.nextIndex();
					DiscoveryModule.Item item = it.next();
					mutex.notify("disco:item:2:" + index + ":" + item.getNode());
				}
				last.set(rsm.getLast());
				mutex.notify(
						"disco:items:2:" + items.size() + ":rsm:count:" + rsm.getCount() + ":index:" + rsm.getIndex(),
						"disco:items:2");
			}

			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				throw new RuntimeException();
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:items:2:error:" + error, "disco:items:2");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:items:2:error:timeout", "disco:items:2");
			}
		});

		mutex.waitFor(10 * 1000, "disco:items:2");
		assertTrue(mutex.isItemNotified(
				"disco:items:2:" + queryRsm.getMax() + ":rsm:count:" + allNodes.size() + ":index:10"));

		allNodesIter = allNodes.subList(10, 15).listIterator();
		while (allNodesIter.hasNext()) {
			int index = allNodesIter.nextIndex();
			assertTrue(mutex.isItemNotified("disco:item:2:" + index + ":" + allNodesIter.next()));
		}

		queryRsm = new RSM();
		queryRsm.setBefore(last.get());
		queryRsm.setMax(10);
		discoModule.getItems(pubsubJid, parentNodeId, queryRsm, new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items, RSM rsm)
					throws XMLException {
				ListIterator<DiscoveryModule.Item> it = items.listIterator();
				while (it.hasNext()) {
					int index = it.nextIndex();
					DiscoveryModule.Item item = it.next();
					mutex.notify("disco:item:3:" + index + ":" + item.getNode());
				}
				last.set(rsm.getLast());
				mutex.notify(
						"disco:items:3:" + items.size() + ":rsm:count:" + rsm.getCount() + ":index:" + rsm.getIndex(),
						"disco:items:3");
			}

			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				throw new RuntimeException();
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:items:3:error:" + error, "disco:items:3");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:items:3:error:timeout", "disco:items:3");
			}
		});

		mutex.waitFor(10 * 1000, "disco:items:3");
		assertTrue(mutex.isItemNotified(
				"disco:items:3:" + queryRsm.getMax() + ":rsm:count:" + allNodes.size() + ":index:14"));

		allNodesIter = allNodes.subList(14, 24).listIterator();
		while (allNodesIter.hasNext()) {
			int index = allNodesIter.nextIndex();
			assertTrue(mutex.isItemNotified("disco:item:3:" + index + ":" + allNodesIter.next()));
		}
	}

	public void createNode(Mutex mutex, Jaxmpp jaxmpp, String id, Consumer<JabberDataElement> consumer)
			throws JaxmppException, InterruptedException {
		String name = "Node " + id;
		TestLogger.log("Creating node: " + id);
		JabberDataElement nodeCfg = new JabberDataElement(XDataType.submit);
		nodeCfg.addTextSingleField("pubsub#title", name);

		if (consumer != null) {
			consumer.accept(nodeCfg);
		}
		jaxmpp.getModule(PubSubModule.class).createNode(pubsubJid.getBareJid(), id, nodeCfg, new PubSubAsyncCallback() {
			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("created:node:" + id + ":" + name);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "created:node:" + id + ":" + name);

		assertTrue("Creation of node " + id + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") +
						   " failed", mutex.isItemNotified("created:node:" + id + ":" + name));
	}

	public void deleteNode(Mutex mutex, Jaxmpp jaxmpp, String id) throws JaxmppException, InterruptedException {
		jaxmpp.getModule(PubSubModule.class).deleteNode(pubsubJid.getBareJid(), id, new PubSubAsyncCallback() {
			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("deleted:node:" + id);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "deleted:node:" + id);
		assertTrue("Removal of node " + id + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") +
						   " failed", mutex.isItemNotified("deleted:node:" + id));
	}

	private void setNodeConfiguration(JabberDataElement nodeCfg, String var, String val) {
		try {
			nodeCfg.addTextSingleField(var, val);
		} catch (XMLException e) {
			TestLogger.log("Failed to set node configuration: " + e.getLocalizedMessage());
			fail(e);
		}
	}
}
