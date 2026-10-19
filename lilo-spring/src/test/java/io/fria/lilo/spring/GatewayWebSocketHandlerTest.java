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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.SyncQueryRetriever;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class GatewayWebSocketHandlerTest {

  private static final String INIT = "{\"type\":\"connection_init\"}";
  private static final String ACK = "{\"type\":\"connection_ack\"}";

  /**
   * Introspection result of {@code type Query { hello: String } type Subscription { ticks: String
   * }}.
   */
  private static final String TICKER_INTROSPECTION =
      """
      {"data":{"__schema":{
        "queryType":{"name":"Query"},"mutationType":null,"subscriptionType":{"name":"Subscription"},
        "types":[
          {"kind":"OBJECT","name":"Query","description":null,"interfaces":[],"inputFields":null,
           "enumValues":null,"possibleTypes":null,"fields":[
            {"name":"hello","description":null,"args":[],"isDeprecated":false,
             "deprecationReason":null,"type":{"kind":"SCALAR","name":"String","ofType":null}}]},
          {"kind":"OBJECT","name":"Subscription","description":null,"interfaces":[],
           "inputFields":null,"enumValues":null,"possibleTypes":null,"fields":[
            {"name":"ticks","description":null,"args":[],"isDeprecated":false,
             "deprecationReason":null,"type":{"kind":"SCALAR","name":"String","ofType":null}}]},
          {"kind":"SCALAR","name":"String","description":null,"fields":null,"interfaces":null,
           "inputFields":null,"enumValues":null,"possibleTypes":null}],
        "directives":[]}}}
      """;

  private final GatewayWebSocketHandler handler =
      new GatewayWebSocketHandler(Lilo.builder().build(), "/graphql");

  private static List<String> types(final RecordingWebSocketSession session) {
    return session.sent().stream()
        .map(frame -> JsonUtils.toMap(frame).orElseThrow().get("type"))
        .map(String::valueOf)
        .toList();
  }

  @Test
  void exposesSubscriptionPath() {
    assertEquals("/graphql", this.handler.getSubscriptionPath());
  }

  @Test
  void connectionInitIsAcknowledged() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    this.handler.afterConnectionEstablished(session);

    this.handler.handleMessage(session, new TextMessage(INIT));

    assertEquals(List.of("connection_ack"), types(session));
    assertTrue(session.isOpen());
  }

  @Test
  void pingIsAnsweredWithPong() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    this.handler.afterConnectionEstablished(session);

    this.handler.handleMessage(session, new TextMessage(INIT));
    this.handler.handleMessage(session, new TextMessage("{\"type\":\"ping\"}"));

    assertEquals(List.of("connection_ack", "pong"), types(session));
  }

  @Test
  void connectionCloseReleasesTheSession() throws Exception {
    final RecordingWebSocketSession first = new RecordingWebSocketSession("same-id");
    this.handler.afterConnectionEstablished(first);
    this.handler.handleMessage(first, new TextMessage(INIT));

    this.handler.afterConnectionClosed(first, CloseStatus.NORMAL);

    // Had the close not been delegated, a second connection_init for the id would close it
    final RecordingWebSocketSession second = new RecordingWebSocketSession("same-id");
    this.handler.afterConnectionEstablished(second);
    this.handler.handleMessage(second, new TextMessage(INIT));
    assertEquals(List.of("connection_ack"), types(second));
    assertTrue(second.isOpen());
  }

  @Test
  void invalidMessageClosesTheSession() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    this.handler.afterConnectionEstablished(session);

    this.handler.handleMessage(session, new TextMessage("not json"));

    assertFalse(session.isOpen());
    assertEquals(List.of(), types(session));
  }

  @Test
  void closeOfUnknownSessionIsIgnored() {
    assertDoesNotThrow(() -> this.handler.afterConnectionClosed(null, CloseStatus.NORMAL));
  }

  @Test
  void rejectsInvalidSendLimits() {
    final Lilo lilo = Lilo.builder().build();
    assertThrows(
        IllegalArgumentException.class,
        () -> new GatewayWebSocketHandler(lilo, "/graphql", Duration.ZERO, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GatewayWebSocketHandler(lilo, "/graphql", Duration.ofDays(30), 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GatewayWebSocketHandler(lilo, "/graphql", Duration.ofSeconds(1), 0));
    assertEquals(GatewayWebSocketHandler.DEFAULT_SEND_TIME_LIMIT, this.handler.getSendTimeLimit());
    assertEquals(
        GatewayWebSocketHandler.DEFAULT_SEND_BUFFER_SIZE_LIMIT,
        this.handler.getSendBufferSizeLimit());
  }

  @Test
  void connectionEstablishedDecoratesTheSessionWithTheLimits() throws Exception {
    final GatewayWebSocketHandler limited =
        new GatewayWebSocketHandler(Lilo.builder().build(), "/graphql", Duration.ofMillis(250), 99);
    final RecordingWebSocketSession session = new RecordingWebSocketSession();

    limited.afterConnectionEstablished(session);

    final ConcurrentWebSocketSessionDecorator decorator =
        assertInstanceOf(
            ConcurrentWebSocketSessionDecorator.class,
            session.getAttributes().get(ClientSessionWrapper.DECORATOR_ATTRIBUTE));
    assertEquals(250, decorator.getSendTimeLimit());
    assertEquals(99, decorator.getBufferSizeLimit());
  }

  @Test
  void slowClientIsClosedAndItsOperationReleased() throws Exception {
    final AtomicReference<SourceWebSocketHandler> source = new AtomicReference<>();
    final RecordingWebSocketSession sourceSession = new RecordingWebSocketSession("source");
    final CountDownLatch sourceSubscribed = new CountDownLatch(1);
    final SubscriptionRetriever retriever =
        (liloContext, schemaSource, query, publisher, localContext) -> {
          // Connects the operation to a source socket, as a real retriever would
          final SourceWebSocketHandler sourceHandler = new SourceWebSocketHandler(query, publisher);
          sourceHandler.afterConnectionEstablished(sourceSession);
          sourceHandler.handleTextMessage(sourceSession, new TextMessage(ACK));
          source.set(sourceHandler);
          sourceSubscribed.countDown();
        };
    final SyncIntrospectionRetriever introspection =
        (liloContext, schemaSource, query, localContext) -> TICKER_INTROSPECTION;
    final SyncQueryRetriever queries = (liloContext, schemaSource, query, localContext) -> "{}";
    final Lilo lilo =
        Lilo.builder()
            .addSource(RemoteSchemaSource.create("ticker", introspection, queries, retriever))
            .build();
    // Smaller than the next frame that queues behind the blocked pong (the frame in flight does
    // not count)
    final GatewayWebSocketHandler gateway =
        new GatewayWebSocketHandler(lilo, "/graphql", Duration.ofSeconds(10), 32);
    final RecordingWebSocketSession client = new RecordingWebSocketSession();
    gateway.afterConnectionEstablished(client);
    gateway.handleMessage(client, new TextMessage(INIT));
    gateway.handleMessage(
        client,
        new TextMessage(
            "{\"id\":\"op\",\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription { ticks }\"}}"));
    assertTrue(sourceSubscribed.await(5, TimeUnit.SECONDS));

    final CountDownLatch release = new CountDownLatch(1);
    client.blockSendsUntil(release);
    final ExecutorService socketThread = Executors.newSingleThreadExecutor();
    try {
      // The client stops reading while the gateway answers a ping
      final Future<?> blocked =
          socketThread.submit(
              () -> {
                gateway.handleMessage(client, new TextMessage("{\"type\":\"ping\"}"));
                return null;
              });
      assertTrue(client.awaitFirstSend(5, TimeUnit.SECONDS));

      // The source delivers an event: its next exceeds the client's buffer, without blocking
      source
          .get()
          .handleTextMessage(
              sourceSession,
              new TextMessage(
                  "{\"id\":\"x\",\"type\":\"next\",\"payload\":{\"data\":{\"ticks\":\"tick\"}}}"));
      assertTrue(client.awaitClosed(5, TimeUnit.SECONDS));
      assertEquals(CloseStatus.SESSION_NOT_RELIABLE, client.closeStatus());

      // The container reports the close; the gateway releases the operation and its source
      gateway.afterConnectionClosed(client, CloseStatus.SESSION_NOT_RELIABLE);
      assertTrue(sourceSession.awaitClosed(5, TimeUnit.SECONDS));
      assertTrue(
          types(sourceSession).contains("complete"),
          "the source is told to complete: " + types(sourceSession));

      release.countDown();
      blocked.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      socketThread.shutdownNow();
    }
  }
}
