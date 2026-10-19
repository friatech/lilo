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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
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
    assertEquals("Subscription source closed the connection", failure.getMessage());
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
}
