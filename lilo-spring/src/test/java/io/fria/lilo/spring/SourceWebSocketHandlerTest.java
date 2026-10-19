/*
 * Copyright 2022-2024 the original author or authors.
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
package io.fria.lilo.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;

class SourceWebSocketHandlerTest {

  private final AtomicBoolean publisherClosed = new AtomicBoolean();
  private final AtomicBoolean publisherFailed = new AtomicBoolean();
  private final SubscriptionSourcePublisher publisher =
      new SubscriptionSourcePublisher() {
        @Override
        public void close() {
          SourceWebSocketHandlerTest.this.publisherClosed.set(true);
          super.close();
        }

        @Override
        public void error(final Throwable error) {
          SourceWebSocketHandlerTest.this.publisherFailed.set(true);
          super.error(error);
        }
      };

  private static GraphQLQuery query() {
    final GraphQLQuery query = mock(GraphQLQuery.class);
    when(query.getRequest())
        .thenReturn(GraphQLRequest.builder().query("subscription { greeting }").build());
    return query;
  }

  private static Map<String, Object> frame(
      final RecordingWebSocketSession session, final int index) {
    return JsonUtils.toMap(session.sent().get(index)).orElseThrow();
  }

  /** Acknowledges the source connection and returns the id of the operation it subscribed. */
  private static String ack(
      final SourceWebSocketHandler handler, final RecordingWebSocketSession session) {
    handler.afterConnectionEstablished(session);
    handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_ack\"}"));
    return (String) frame(session, 1).get("id");
  }

  /** A next frame of exactly the given number of characters. */
  private static String nextOfLength(final String id, final int length) {
    final String prefix = "{\"id\":\"" + id + "\",\"type\":\"next\",\"payload\":{\"x\":\"";
    final String suffix = "\"}}";
    final String next = prefix + "a".repeat(length - prefix.length() - suffix.length()) + suffix;
    assertEquals(length, next.length());
    return next;
  }

  /** Sends the message whole, or split into three fragments. */
  private static void deliver(
      final SourceWebSocketHandler handler,
      final RecordingWebSocketSession session,
      final String message,
      final boolean fragmented) {
    if (!fragmented) {
      handler.handleTextMessage(session, new TextMessage(message, true));
      return;
    }
    final int third = message.length() / 3;
    handler.handleTextMessage(session, new TextMessage(message.substring(0, third), false));
    handler.handleTextMessage(session, new TextMessage(message.substring(third, 2 * third), false));
    handler.handleTextMessage(session, new TextMessage(message.substring(2 * third), true));
  }

  private List<Object> events() {
    final List<Object> events = new CopyOnWriteArrayList<>();
    this.publisher.getFlux().subscribe(events::add, e -> {});
    return events;
  }

  private String failureMessage() {
    return assertThrows(
            RuntimeException.class, () -> this.publisher.getFlux().blockLast(Duration.ofSeconds(2)))
        .getMessage();
  }

  @Test
  void connectionStartSendsConnectionInit() throws Exception {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();

    handler.afterConnectionEstablished(session);

    assertEquals(1, session.sent().size());
    assertEquals("connection_init", frame(session, 0).get("type"));
    assertEquals(Map.of(), frame(session, 0).get("payload"));
    assertNotNull(session.getAttributes().get(ClientSessionWrapper.SEND_LOCK_ATTRIBUTE));
  }

  @Test
  void connectionInitCarriesTheGivenPayload() throws Exception {
    final SourceWebSocketHandler handler =
        new SourceWebSocketHandler(
            query(), this.publisher, Map.of("Authorization", "Bearer token"));
    final RecordingWebSocketSession session = new RecordingWebSocketSession();

    handler.afterConnectionEstablished(session);

    assertEquals(Map.of("Authorization", "Bearer token"), frame(session, 0).get("payload"));
  }

  @Test
  void connectionAckIsAnsweredWithSubscribe() throws Exception {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);

    handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_ack\"}"));

    assertEquals(2, session.sent().size());
    final Map<String, Object> subscribe = frame(session, 1);
    assertEquals("subscribe", subscribe.get("type"));
    assertNotNull(subscribe.get("id"));
    assertEquals(
        "subscription { greeting }",
        ((Map<?, ?>) subscribe.get("payload")).get("query"),
        "the subscribe carries the query");
  }

  @Test
  void connectionCloseFailsTheActivePublisher() throws Exception {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    // The source went away while the operation was active: that is a failure, not a completion
    assertTrue(this.publisherFailed.get());
    assertFalse(this.publisherClosed.get());
    final RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () -> this.publisher.getFlux().blockLast(Duration.ofSeconds(2)));
    assertEquals("Subscription source closed the connection: 1000", failure.getMessage());
  }

  @Test
  void connectionCloseAfterTheSourceCompletedKeepsTheCompletion() throws Exception {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);
    handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_ack\"}"));
    final String id = (String) frame(session, 1).get("id");

    handler.handleTextMessage(
        session, new TextMessage("{\"id\":\"" + id + "\",\"type\":\"complete\"}"));
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    assertTrue(this.publisherClosed.get());
    assertNull(this.publisher.getFlux().blockLast(Duration.ofSeconds(2)));
  }

  // Issue #26: a jakarta.websocket container delivers a text message larger than its inbound buffer
  // (8 KiB by default on Tomcat) only to a handler that supports partial messages; otherwise it
  // closes the session with 1009 and the operation ends silently. The handler must therefore accept
  // fragments and assemble them.
  @Test
  void supportsPartialMessages() {
    assertTrue(new SourceWebSocketHandler(query(), this.publisher).supportsPartialMessages());
  }

  @Test
  void fragmentedNextIsAssembledBeforeHandling() throws Exception {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);
    handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_ack\"}"));
    final String id = (String) frame(session, 1).get("id");

    final String next =
        "{\"id\":\""
            + id
            + "\",\"type\":\"next\",\"payload\":{\"data\":{\"streamSpectrogram\":{"
            + "\"timestamp\":\"2025-07-18T10:00:00Z\",\"intensities\":["
            + "{\"frequency\":440.0,\"intensity\":0.5},{\"frequency\":880.0,\"intensity\":0.25}"
            + "]}}}}";
    final int cut1 = next.indexOf("intensities");
    final int cut2 = next.indexOf("880.0");
    handler.handleTextMessage(session, new TextMessage(next.substring(0, cut1), false));
    handler.handleTextMessage(session, new TextMessage(next.substring(cut1, cut2), false));
    handler.handleTextMessage(session, new TextMessage(next.substring(cut2), true));

    assertFalse(this.publisherFailed.get(), "fragments must not be handled one by one");
    assertTrue(session.isOpen());
    final Object event = this.publisher.getFlux().blockFirst(Duration.ofSeconds(2));
    assertEquals(
        JsonUtils.toMap(next).orElseThrow().get("payload"), event, "the assembled payload");
  }

  @Test
  void messageBeyondTheSizeLimitFailsTheOperationAndClosesTheSource() throws Exception {
    final SourceWebSocketHandler handler =
        new SourceWebSocketHandler(query(), this.publisher, Map.of(), 64);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);
    handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_ack\"}"));

    final String fragment = "{\"type\":\"next\",\"payload\":{\"x\":\"" + "a".repeat(40) + "\"";
    handler.handleTextMessage(session, new TextMessage(fragment, false));
    handler.handleTextMessage(session, new TextMessage(fragment, false));
    // Fragments of the same message keep arriving after the failure; they are dropped
    handler.handleTextMessage(session, new TextMessage("}}", true));

    assertTrue(this.publisherFailed.get());
    assertFalse(this.publisherClosed.get());
    // The failure is reported first; the core then closes the source session on termination
    assertFalse(session.isOpen());
    final RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () -> this.publisher.getFlux().blockLast(Duration.ofSeconds(2)));
    assertEquals("Subscription source message exceeds 64 characters", failure.getMessage());
  }

  @Test
  void connectionCloseReasonIsPartOfTheFailure() throws Exception {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);

    handler.afterConnectionClosed(
        session, new CloseStatus(1009, "The decoded text message was too big"));

    final RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () -> this.publisher.getFlux().blockLast(Duration.ofSeconds(2)));
    assertEquals(
        "Subscription source closed the connection: 1009 The decoded text message was too big",
        failure.getMessage());
  }

  @Test
  void messageBeforeConnectionEstablishedIsRejected() {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final TextMessage ack = new TextMessage("{\"type\":\"connection_ack\"}");

    assertThrows(IllegalStateException.class, () -> handler.handleTextMessage(session, ack));
    assertThrows(
        IllegalStateException.class,
        () -> handler.afterConnectionClosed(session, CloseStatus.NORMAL));
    assertEquals(List.of(), session.sent());
  }

  @ParameterizedTest(name = "fragmented: {0}")
  @ValueSource(booleans = {false, true})
  void messageExactlyAtTheLimitIsAccepted(final boolean fragmented) {
    final SourceWebSocketHandler handler =
        new SourceWebSocketHandler(query(), this.publisher, Map.of(), 128);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final String id = ack(handler, session);
    final List<Object> events = this.events();

    deliver(handler, session, nextOfLength(id, 128), fragmented);

    assertEquals(1, events.size());
    assertFalse(this.publisherFailed.get());
    assertTrue(session.isOpen());
  }

  // The limit is in characters; the whole message (fragmented: false) takes the fast path
  @ParameterizedTest(name = "fragmented: {0}")
  @ValueSource(booleans = {false, true})
  void messageOneCharacterOverTheLimitFailsTheOperation(final boolean fragmented) {
    final SourceWebSocketHandler handler =
        new SourceWebSocketHandler(query(), this.publisher, Map.of(), 128);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final String id = ack(handler, session);
    final List<Object> events = this.events();

    deliver(handler, session, nextOfLength(id, 129), fragmented);
    // Every later message is dropped, even a valid one
    handler.handleTextMessage(session, new TextMessage(nextOfLength(id, 100), true));

    assertEquals(List.of(), events);
    assertTrue(this.publisherFailed.get());
    assertFalse(session.isOpen());
    assertEquals("Subscription source message exceeds 128 characters", this.failureMessage());
  }

  @Test
  void oversizedMessageBeforeConnectionAckFailsTheOperationAndClosesTheSource() {
    final SourceWebSocketHandler handler =
        new SourceWebSocketHandler(query(), this.publisher, Map.of(), 64);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    handler.afterConnectionEstablished(session);

    handler.handleTextMessage(
        session, new TextMessage("{\"type\":\"connection_ack\"" + " ".repeat(64) + "}"));

    assertTrue(this.publisherFailed.get());
    assertFalse(session.isOpen(), "no operation was subscribed, so the handler closes the source");
    assertEquals(1, session.sent().size(), "nothing but connection_init was sent");
    assertEquals("Subscription source message exceeds 64 characters", this.failureMessage());
  }

  @Test
  void secondFragmentedMessageStartsWithAnEmptyBuffer() {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final String id = ack(handler, session);
    final List<Object> events = this.events();

    final String first = nextOfLength(id, 90);
    final String second = nextOfLength(id, 120);
    deliver(handler, session, first, true);
    deliver(handler, session, second, true);

    assertEquals(
        List.of(
            JsonUtils.toMap(first).orElseThrow().get("payload"),
            JsonUtils.toMap(second).orElseThrow().get("payload")),
        events);
    assertFalse(this.publisherFailed.get());
  }

  @Test
  void emptyFirstAndLastFragmentsAreAssembled() {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final String id = ack(handler, session);
    final List<Object> events = this.events();
    final String next = nextOfLength(id, 80);

    handler.handleTextMessage(session, new TextMessage("", false));
    handler.handleTextMessage(session, new TextMessage(next.substring(0, 40), false));
    handler.handleTextMessage(session, new TextMessage(next.substring(40), false));
    handler.handleTextMessage(session, new TextMessage("", true));

    assertEquals(List.of(JsonUtils.toMap(next).orElseThrow().get("payload")), events);
    assertFalse(this.publisherFailed.get());
    assertTrue(session.isOpen());
  }

  @Test
  void largeFragmentedMessageReleasesTheBufferOnceAssembled() {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final String id = ack(handler, session);
    final List<Object> events = this.events();

    deliver(handler, session, nextOfLength(id, 200_000), true);

    assertEquals(1, events.size());
    assertTrue(
        handler.bufferCapacity() <= 64 * 1024,
        "a 200 000-character message left a buffer of " + handler.bufferCapacity());
  }

  @Test
  void connectionCloseReleasesAPartlyReceivedMessage() {
    final SourceWebSocketHandler handler = new SourceWebSocketHandler(query(), this.publisher);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final String id = ack(handler, session);

    handler.handleTextMessage(
        session, new TextMessage(nextOfLength(id, 10_000).substring(0, 9_000), false));
    handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

    assertEquals(0, handler.bufferCapacity());
    assertEquals("Subscription source closed the connection: 1001", this.failureMessage());
  }

  @Test
  void oversizeFailureIsReportedOutsideTheFragmentLock() throws Exception {
    final AtomicReference<SourceWebSocketHandler> handlerRef = new AtomicReference<>();
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    final AtomicBoolean otherMessageHandled = new AtomicBoolean();
    final SubscriptionSourcePublisher reentrant =
        new SubscriptionSourcePublisher() {
          @Override
          public void error(final Throwable error) {
            // A gateway callback that waits for another container thread must not deadlock
            final Thread other =
                new Thread(
                    () -> {
                      handlerRef.get().handleTextMessage(session, new TextMessage("{}", true));
                      otherMessageHandled.set(true);
                    });
            other.start();
            try {
              other.join(2_000);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            super.error(error);
          }
        };
    final SourceWebSocketHandler handler =
        new SourceWebSocketHandler(query(), reentrant, Map.of(), 100);
    handlerRef.set(handler);
    final String id = ack(handler, session);

    handler.handleTextMessage(session, new TextMessage(nextOfLength(id, 101), true));

    assertTrue(otherMessageHandled.get(), "the failure was reported under the fragment lock");
  }

  @Test
  void oversizeIsNotLoggedAsAWarningByTheHandler() {
    // The gateway logs the failed operation at WARN with this cause; the handler must not repeat it
    final Logger logger = Logger.getLogger(SourceWebSocketHandler.class.getName());
    final List<LogRecord> warnings = new ArrayList<>();
    final Handler capture =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            if (logRecord.getLevel().intValue() >= Level.WARNING.intValue()) {
              warnings.add(logRecord);
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(capture);
    try {
      final SourceWebSocketHandler handler =
          new SourceWebSocketHandler(query(), this.publisher, Map.of(), 100);
      final RecordingWebSocketSession session = new RecordingWebSocketSession();
      final String id = ack(handler, session);

      handler.handleTextMessage(session, new TextMessage(nextOfLength(id, 101), true));

      assertTrue(this.publisherFailed.get());
      assertEquals(List.of(), warnings);
    } finally {
      logger.removeHandler(capture);
    }
  }
}
