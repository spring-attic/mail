/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.mail.source;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.integration.mail.MailHeaders;
import org.springframework.integration.mail.transformer.MailToStringTransformer;
import org.springframework.integration.test.mail.TestMailServer;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Tests for Mail Source Configuration.
 *
 * @author Amol
 * @author Artem Bilan
 * @author Chris Schaefer
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"mail.mark-as-read=true",
				"mail.delete=false",
				"mail.user-flag=testSIUserFlag",
				"mail.java-mail-properties=mail.imap.socketFactory.fallback=true\\n mail.store.protocol=imap\\n mail.debug=true" })
public abstract class MailSourceConfigurationTests {

	@Autowired
	protected Source source;

	@Autowired
	protected MessageCollector messageCollector;

	@Autowired
	protected MailSourceProperties properties;

	@Autowired
	protected BeanFactory beanFactory;

	private static TestMailServer.MailServer MAIL_SERVER;

	protected static void startMailServer(TestMailServer.MailServer mailServer)
			throws InterruptedException {
		MAIL_SERVER = mailServer;
		System.setProperty("test.mail.server.port", "" + MAIL_SERVER.getPort());
		int n = 0;
		while (n++ < 100 && (!MAIL_SERVER.isListening())) {
			Thread.sleep(100);
		}
		assertTrue(n < 100);
	}

	@AfterClass
	public static void cleanup() {
		System.clearProperty("test.mail.server.port");
		MAIL_SERVER.stop();
	}

	@TestPropertySource(properties = "mail.url=imap://user:pw@localhost:${test.mail.server.port}/INBOX")
	public static class ImapPassTests extends MailSourceConfigurationTests {

		@BeforeClass
		public static void startImapServer() throws Throwable {
			startMailServer(TestMailServer.imap(0));
		}

		@Test
		public void testSimpleTest() throws Exception {

			Message<?> received = this.messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), instanceOf(String.class));
			assertTrue(((String) received.getPayload()).endsWith("\r\n\r\nfoo\r\n\r\n"));
			MessageHeaders headers = received.getHeaders();
			assertThat(headers.get(MailHeaders.TO), instanceOf(List.class));
			assertThat(headers.get(MailHeaders.CC), instanceOf(List.class));
			assertThat(headers.get(MailHeaders.BCC), instanceOf(List.class));
			assertThat(headers.get(MailHeaders.TO).toString(), equalTo("[Foo <foo@bar>]"));
			assertThat(headers.get(MailHeaders.CC).toString(), equalTo("[a@b, c@d]"));
			assertThat(headers.get(MailHeaders.BCC).toString(), equalTo("[e@f, g@h]"));
		}

	}

	@TestPropertySource(properties = {
			"mail.url=imap://user:pw@localhost:${test.mail.server.port}/INBOX",
			"mail.charset=cp1251" })
	public static class ImapFailTests extends MailSourceConfigurationTests {

		@BeforeClass
		public static void startImapServer() throws Throwable {
			startMailServer(TestMailServer.imap(0));
		}

		@Test
		public void testSimpleTest() throws Exception {

			Message<?> received = this.messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertTrue(!received.getPayload().equals("Test Mail"));

			MailToStringTransformer mailToStringTransformer = this.beanFactory.getBean(MailToStringTransformer.class);
			assertEquals("cp1251", TestUtils.getPropertyValue(mailToStringTransformer, "charset"));
		}

	}

	@TestPropertySource(properties = "mail.url=pop3://user:pw@localhost:${test.mail.server.port}/INBOX")
	public static class Pop3PassTests extends MailSourceConfigurationTests {

		@BeforeClass
		public static void startPop3Server() throws Throwable {
			startMailServer(TestMailServer.pop3(0));
		}

		@Test
		public void testSimpleTest() throws Exception {

			Message<?> received = this.messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("foo\r\n\r\n", received.getPayload());
		}

	}

	@TestPropertySource(properties = "mail.url=pop3://user:pw@localhost:${test.mail.server.port}/INBOX")
	public static class Pop3FailTests extends MailSourceConfigurationTests {

		@BeforeClass
		public static void startPop3Server() throws Throwable {
			startMailServer(TestMailServer.pop3(0));
		}

		@Test
		public void testSimpleTest() throws Exception {

			Message<?> received = this.messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertTrue(!received.getPayload().equals("Test Mail"));
		}

	}

	@TestPropertySource(properties = {
			"mail.idle-imap=true",
			"mail.url=imap://user:pw@localhost:${test.mail.server.port}/INBOX" })
	public static class ImapIdlePassTests extends MailSourceConfigurationTests {

		@BeforeClass
		public static void startImapServer() throws Throwable {
			startMailServer(TestMailServer.imap(0));
		}

		@Test
		public void testSimpleTest() throws Exception {

			Message<?> received = this.messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertTrue(((String) received.getPayload()).endsWith("\r\n\r\nfoo\r\n\r\n"));
		}

	}

	@TestPropertySource(properties = {
			"mail.idle-imap=true",
			"mail.url=imap://user:pw@localhost:${test.mail.server.port}/INBOX" })
	public static class ImapIdleFailTests extends MailSourceConfigurationTests {

		@BeforeClass
		public static void startImapServer() throws Throwable {
			startMailServer(TestMailServer.imap(0));
		}

		@Test
		public void testSimpleTest() throws Exception {

			Message<?> received = this.messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertTrue(!received.getPayload().equals("Test Mail"));
		}

	}

	@SpringBootApplication
	public static class MailSourceApplication {

	}

}
