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
package tigase.tests.archive;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.*;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

/**
 * Created by andrzej on 25.07.2016.
 */
public class TestMessageArchivingTagsAndContains
		extends AbstractTest {

	final Mutex mutex = new Mutex();
	String expect;
	String id;
	Date testStartDate;
	Account user1;
	Account user2;
	Jaxmpp userJaxmpp1;
	Jaxmpp userJaxmpp2;

	@BeforeMethod
	public void setUpTest() throws JaxmppException, InterruptedException {
		testStartDate = new Date();

		user1 = createAccount().setLogPrefix("user1").build();
		userJaxmpp1 = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			return jaxmpp;
		}).setConnected(true).build();

		user2 = createAccount().setLogPrefix("user2").build();
		userJaxmpp2 = user2.createJaxmpp().setConnected(true).build();

		// enable message archiving to be sure it works
		id = nextRnd().toLowerCase();
		setArchiveSettings(userJaxmpp1, id, true);
		expect = "setArchiveSettings:" + id + ":success";
		mutex.waitFor(20 * 1000, expect);
		assertTrue(mutex.isItemNotified(expect), "Set archive for user error.");
	}

	@Test(description = "Support for storage of messages with tags and searching by tags")
	public void testStorageAndQueryWithTags() throws Exception {
		id = nextRnd().toLowerCase();

		List<String> forbiddenMessages = new ArrayList<String>();
		for (int i = 0; i < 10; i++) {
			String msg = nextRnd();
			forbiddenMessages.add(msg);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);
		String tagName = "#Test123";
		List<String> expectedMessages = new ArrayList<String>();
		Date start = new Date();
		for (int i = 0; i < 10; i++) {
			String msg = nextRnd() + " " + tagName;
			expectedMessages.add(msg);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);
		for (int i = 0; i < 10; i++) {
			String msg = nextRnd();
			forbiddenMessages.add(msg);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);

		Criteria crit = new Criteria().setWith(JID.jidInstance(userJaxmpp2.getSessionObject().getUserBareJid()))
				.setStart(testStartDate)
				.addTags(tagName);

		retrieveArchivedCollections(userJaxmpp1, id, crit);
		assertTrue(mutex.isItemNotified("1:" + id + ":retriveCollection:received:" + user2.getJid().toString()),
				   "Retrieval of list of collections failed");
		retrieveArchivedMessages(userJaxmpp1, id, crit, expectedMessages.size());
		for (String msg : expectedMessages) {
			assertTrue(mutex.isItemNotified("2:" + id + ":retriveCollection:success:" + msg),
					   "Not retrieved message which was marked by hashtag " + tagName);
		}
		for (String msg : forbiddenMessages) {
			assertFalse(mutex.isItemNotified("2:" + id + ":retriveCollection:success:" + msg),
						"Retrieved message which was not marked by hashtag " + tagName);
		}
	}

	@Test(groups = {"Phase 1"}, description = "Support for searching of messages where body matches query")
	public void testQueryWithContains() throws Exception {
		id = nextRnd().toLowerCase();

		List<String> forbiddenMessages = new ArrayList<String>();
		for (int i = 0; i < 10; i++) {
			String msg = nextRnd();
			forbiddenMessages.add(msg);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);
		String tagName = "Test 123";
		List<String> expectedMessages = new ArrayList<String>();
		Date start = new Date();
		for (int i = 0; i < 10; i++) {
			String msg = nextRnd() + " " + tagName;
			expectedMessages.add(msg);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);
		for (int i = 0; i < 10; i++) {
			String msg = nextRnd();
			forbiddenMessages.add(msg);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);

		Criteria crit = new Criteria().setWith(JID.jidInstance(userJaxmpp2.getSessionObject().getUserBareJid()))
				.setStart(testStartDate)
				.addContains(tagName);

		retrieveArchivedCollections(userJaxmpp1, id, crit);
		assertTrue(mutex.isItemNotified("1:" + id + ":retriveCollection:received:" + user2.getJid().toString()),
				   "Retrieval of list of collections failed");
		retrieveArchivedMessages(userJaxmpp1, id, crit, expectedMessages.size());
		for (String msg : expectedMessages) {
			assertTrue(mutex.isItemNotified("2:" + id + ":retriveCollection:success:" + msg),
					   "Not retrieved message which was marked by hashtag " + tagName);
		}
		for (String msg : forbiddenMessages) {
			assertFalse(mutex.isItemNotified("2:" + id + ":retriveCollection:success:" + msg),
						"Retrieved message which was not marked by hashtag " + tagName);
		}
	}

	@Test(groups = {"Phase 1"}, description = "Support for searching of similar tags")
	public void testTagsSuggestions() throws Exception {
		id = nextRnd().toLowerCase();

		List<String> goodTags = new ArrayList<String>();
		for (int i = 0; i < 10; i++) {
			String tag = "#Good" + nextRnd();
			String msg = nextRnd() + " " + tag;
			goodTags.add(tag);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}
		Thread.sleep(2000);

		List<String> badTags = new ArrayList<String>();
		for (int i = 0; i < 10; i++) {
			String tag = "#Bad" + nextRnd();
			String msg = nextRnd() + " " + tag;
			badTags.add(tag);
			sendAndWait(userJaxmpp1, userJaxmpp2, msg);
		}

		queryTags(userJaxmpp1, id, goodTags.size());

		for (String tag : goodTags) {
			assertTrue(mutex.isItemNotified("3:" + id + ":queryTags:success:" + tag),
					   "Not returned tag '" + tag + "' which should be suggested");
		}
		for (String tag : badTags) {
			assertFalse(mutex.isItemNotified("3:" + id + ":queryTags:success:" + tag),
						"Returned tag '" + tag + "' which should not be suggested");
		}
	}

	private void queryTags(Jaxmpp userJaxmpp, String id, int expected) throws InterruptedException, JaxmppException {
		int retry = 0;
		int count = 0;
		while ((count = queryTagsInternal(userJaxmpp, id, count)) < expected) {
			System.out.println("got " + count + " while expected " + expected);
			if (retry >= 5) {
				assertEquals(expected, count, "retry " + retry + " still failing...");
				return;
			}
			retry++;
			Thread.sleep(2000);
		}
	}

	private int queryTagsInternal(Jaxmpp userJaxmpp, String id, int retry) throws InterruptedException, JaxmppException {
		AtomicInteger count = new AtomicInteger(0);
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);
		Element tagsEl = ElementFactory.create("tags", null, "http://tigase.org/protocol/archive#query");
		iq.addChild(tagsEl);
		tagsEl.setAttribute("like", "#Good");

		userJaxmpp.send(iq, new AsyncCallback() {

			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("3:" + id + ":queryTags:error");
			}

			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				Element tagsEl = responseStanza.getChildrenNS("tags", "http://tigase.org/protocol/archive#query");
				List<Element> tagElems = tagsEl.getChildren("tag");
				if (tagElems != null) {
					for (Element tagEl : tagElems) {
						mutex.notify("3:" + id + ":queryTags:success:" + tagEl.getValue());
					}
					count.set(tagElems.size());
				}
				mutex.notify("3:" + retry + ":" + id + ":queryTags:success");
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify("3:" + id + ":queryTags:timeout");
			}
		});
		mutex.waitFor(20 * 1000, "3:" + retry + ":" + id + ":queryTags:success");
		return count.get();
	}

	private void setArchiveSettings(Jaxmpp user, final String id, boolean enable)
			throws JaxmppException, InterruptedException {
		user.getModule(MessageArchivingModule.class).setAutoArchive(enable, new AsyncCallback() {

			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("setArchiveSettings:" + id + ":error");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws XMLException {
				mutex.notify("setArchiveSettings:" + id + ":success");
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify("setArchiveSettings:" + id + ":timeout");
			}
		});

		Thread.sleep(2 * 1000);
	}

	private void retrieveArchivedCollections(final Jaxmpp jaxmppUser1, final String id, final Criteria crit)
			throws JaxmppException, InterruptedException {

		jaxmppUser1.getModule(MessageArchivingModule.class)
				.listCollections(crit, new MessageArchivingModule.CollectionAsyncCallback() {

					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("1:" + id + ":retriveCollection:error");
					}

					public void onTimeout() throws JaxmppException {
						mutex.notify("1:" + id + ":retriveCollection:timeout");
					}

					@Override
					protected void onCollectionReceived(ResultSet<Chat> chats) throws XMLException {
						for (Chat item : chats.getItems()) {
							mutex.notify("1:" + id + ":retriveCollection:received:" + item.getWithJid());
						}
						try {
							Thread.sleep(100);
						} catch (Exception ex) {
						}
						mutex.notify("1:" + id + ":retriveCollection:received");
					}

				});

		mutex.waitFor(20 * 1000, "1:" + id + ":retriveCollection:received");
	}

	private void retrieveArchivedMessages(final Jaxmpp jaxmppUser1, final String id, final Criteria crit, final int expected)
			throws JaxmppException, InterruptedException {
		int retry = 0;
		int count = 0;
	    while ((count = retrieveArchivedMessagesInternal(jaxmppUser1, id, crit, count)) < expected) {
	    	System.out.println("got " + count + " while expected " + expected);
	    	if (retry >= 5) {
	    		assertEquals(expected, count, "retry " + retry + " still failing...");
			}
			retry++;
	    	Thread.sleep(2000);
		}
	}

	private int retrieveArchivedMessagesInternal(final Jaxmpp jaxmppUser1, final String id, final Criteria crit, int retry)
			throws JaxmppException, InterruptedException {
		AtomicInteger count = new AtomicInteger(0);
		jaxmppUser1.getModule(MessageArchivingModule.class)
				.retrieveCollection(crit, new MessageArchivingModule.ItemsAsyncCallback() {

					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("2:" + id + ":retriveCollection:error");
					}

					public void onTimeout() throws JaxmppException {
						mutex.notify("2:" + id + ":retriveCollection:timeout");
					}

					@Override
					protected void onItemsReceived(ChatResultSet chat) throws XMLException {
						count.set(chat.getCount());
						for (ChatItem item : chat.getItems()) {
							mutex.notify("2:" + id + ":retriveCollection:success:" + item.getBody());
						}
						mutex.notify("2:" + retry + ":" + id + ":retriveCollection:received");
					}

				});
		mutex.waitFor(20 * 1000, "2:" + retry + ":" + id + ":retriveCollection:received");
		return count.get();
	}

}
