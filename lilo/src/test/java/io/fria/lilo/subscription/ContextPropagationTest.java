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
package io.fria.lilo.subscription;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ContextPropagationTest {

  private static final String SCHEMA =
      "type Query { hello: String }\ntype Subscription { ticks: String }";
  private static final String SUBSCRIBE =
      "{\"id\":\"1\",\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription { ticks }\"}}";
  private static final String HEADERS_ATTRIBUTE = "lilo.handshake.headers";

  /** The localContext each subscription retriever call received. */
  private final List<Object> localContexts = new CopyOnWriteArrayList<>();

  private SubscriptionGatewayHandler gateway;

  private static void await(final @NotNull CompletableFuture<?> future) {
    assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
  }

  private static @NotNull Lilo remoteLilo(final @NotNull SubscriptionRetriever retriever) {
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator()
                    .makeExecutableSchema(
                        new SchemaParser().parse(SCHEMA), RuntimeWiring.newRuntimeWiring().build()))
            .build();

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "remote",
                new TestUtils.TestIntrospectionRetriever(remote),
                new TestUtils.TestQueryRetriever(remote),
                retriever))
        .build();
  }

  @BeforeEach
  void setUp() {
    this.gateway =
        new SubscriptionGatewayHandler(
            remoteLilo(
                (liloContext, schemaSource, query, publisher, localContext) ->
                    this.localContexts.add(
                        localContext == null ? "no local context" : localContext)));
  }

  @Test
  void connectionInitPayloadReachesRetrieverAsLocalContext() {
    final RecordingSession session = new RecordingSession();
    this.send(
        session,
        "{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer t0k3n\",\"n\":1}}");
    assertEquals(SubscriptionMessageType.connection_ack, session.sentMessages().get(0).getType());

    this.send(session, SUBSCRIBE);

    assertEquals(1, this.localContexts.size());
    final SubscriptionConnectionContext context =
        assertInstanceOf(SubscriptionConnectionContext.class, this.localContexts.get(0));
    assertEquals("Bearer t0k3n", context.getConnectionInitPayload().get("Authorization"));
    assertEquals(1, context.getConnectionInitPayload().get("n"));
    assertTrue(context.getHandshakeHeaders().isEmpty());
    assertThrows(
        UnsupportedOperationException.class,
        () -> context.getConnectionInitPayload().put("x", "y"));
    assertTrue(session.isOpen());
  }

  @Test
  void handshakeHeadersReachRetriever() {
    final RecordingSession session = new RecordingSession();
    session.attributes(
        Map.of(HEADERS_ATTRIBUTE, Map.of("authorization", "Bearer h34d3r", "x-tenant", "acme")));
    this.send(session, "{\"type\":\"connection_init\"}");

    this.send(session, SUBSCRIBE);

    assertEquals(1, this.localContexts.size());
    final SubscriptionConnectionContext context =
        assertInstanceOf(SubscriptionConnectionContext.class, this.localContexts.get(0));
    assertEquals(
        Map.of("authorization", "Bearer h34d3r", "x-tenant", "acme"),
        context.getHandshakeHeaders());
    assertTrue(context.getConnectionInitPayload().isEmpty());
    assertThrows(
        UnsupportedOperationException.class, () -> context.getHandshakeHeaders().put("x", "y"));
  }

  @Test
  void malformedPayloadAndHeadersAreIgnored() {
    final Map<Object, Object> headers = new HashMap<>();
    headers.put("x-ok", "kept");
    headers.put("x-number", 42);
    headers.put(7, "non-string name");
    headers.put("x-null", null);

    final RecordingSession session = new RecordingSession();
    session.attributes(Map.of(HEADERS_ATTRIBUTE, headers));
    this.send(session, "{\"type\":\"connection_init\",\"payload\":[\"not\",\"an\",\"object\"]}");
    this.send(session, SUBSCRIBE);

    final RecordingSession other = new RecordingSession("other-session");
    other.attributes(Map.of(HEADERS_ATTRIBUTE, "not a map"));
    this.send(other, "{\"type\":\"connection_init\",\"payload\":{\"nested\":{\"a\":null}}}");
    this.send(other, SUBSCRIBE);

    assertEquals(2, this.localContexts.size());
    final SubscriptionConnectionContext first =
        assertInstanceOf(SubscriptionConnectionContext.class, this.localContexts.get(0));
    assertTrue(first.getConnectionInitPayload().isEmpty());
    assertEquals(Map.of("x-ok", "kept"), first.getHandshakeHeaders());

    final SubscriptionConnectionContext second =
        assertInstanceOf(SubscriptionConnectionContext.class, this.localContexts.get(1));
    assertTrue(second.getHandshakeHeaders().isEmpty());
    assertInstanceOf(Map.class, second.getConnectionInitPayload().get("nested"));
    assertTrue(session.isOpen());
    assertTrue(other.isOpen());
  }

  @Test
  void eachSessionHasItsOwnContext() {
    final RecordingSession first = new RecordingSession("first");
    final RecordingSession second = new RecordingSession("second");
    this.send(first, "{\"type\":\"connection_init\",\"payload\":{\"user\":\"one\"}}");
    this.send(second, "{\"type\":\"connection_init\",\"payload\":{\"user\":\"two\"}}");

    this.send(second, SUBSCRIBE);
    this.send(first, SUBSCRIBE);

    assertEquals(2, this.localContexts.size());
    assertEquals("two", userOf(this.localContexts.get(0)));
    assertEquals("one", userOf(this.localContexts.get(1)));
  }

  @Test
  void subscribeBeforeInitClosesSession() {
    final RecordingSession session = new RecordingSession();

    this.send(session, SUBSCRIBE);

    assertFalse(session.isOpen());
    assertTrue(session.sent().isEmpty());
    assertTrue(this.localContexts.isEmpty());
    assertFalse(this.gateway.tracksSession(session.getId()));
    assertFalse(this.gateway.tracksConnection(session.getId()));
  }

  @Test
  void secondInitClosesSession() {
    final RecordingSession session = new RecordingSession();
    this.send(session, "{\"type\":\"connection_init\"}");
    assertTrue(session.isOpen());

    this.send(session, "{\"type\":\"connection_init\"}");

    assertFalse(session.isOpen());
    assertEquals(1, session.sent().size());
    assertFalse(this.gateway.tracksConnection(session.getId()));
  }

  @Test
  void sessionCloseDropsTheConnectionContext() {
    final RecordingSession session = new RecordingSession();
    this.send(session, "{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"x\"}}");
    assertTrue(this.gateway.tracksConnection(session.getId()));

    this.gateway.handleSessionClose(session);

    assertFalse(this.gateway.tracksConnection(session.getId()));

    // A reconnect under the same id must initialise again
    final RecordingSession reconnected = new RecordingSession();
    this.send(reconnected, SUBSCRIBE);
    assertFalse(reconnected.isOpen());
    assertTrue(this.localContexts.isEmpty());
  }

  @Test
  void sourceHandlerSendsConfiguredInitPayload() {
    final Map<String, Object> payload = new HashMap<>();
    payload.put("Authorization", "Bearer s0urc3");
    final SubscriptionSourceHandler handler =
        new SubscriptionSourceHandler(
            Mockito.mock(GraphQLQuery.class), new SubscriptionSourcePublisher(), payload);
    // Later changes by the caller do not leak into the handshake
    payload.put("late", "ignored");

    final RecordingSession source = new RecordingSession();
    handler.handleSessionStart(source);

    final List<SubscriptionMessage> sent = source.sentMessages();
    assertEquals(1, sent.size());
    assertEquals(SubscriptionMessageType.connection_init, sent.get(0).getType());
    assertEquals(Map.of("Authorization", "Bearer s0urc3"), sent.get(0).getPayload());
  }

  @Test
  void sourceHandlerSendsEmptyInitPayloadByDefault() {
    final GraphQLQuery query = Mockito.mock(GraphQLQuery.class);
    Mockito.when(query.getRequest())
        .thenReturn(GraphQLRequest.builder().query("subscription { ticks }").build());
    final SubscriptionSourceHandler handler =
        new SubscriptionSourceHandler(query, new SubscriptionSourcePublisher());

    final RecordingSession source = new RecordingSession();
    handler.handleSessionStart(source);

    final List<SubscriptionMessage> sent = source.sentMessages();
    assertEquals(1, sent.size());
    assertEquals(SubscriptionMessageType.connection_init, sent.get(0).getType());
    assertEquals(Map.of(), sent.get(0).getPayload());
  }

  private static @Nullable Object userOf(final @NotNull Object localContext) {
    return assertInstanceOf(SubscriptionConnectionContext.class, localContext)
        .getConnectionInitPayload()
        .get("user");
  }

  private void send(final @NotNull RecordingSession session, final @NotNull String message) {
    await(this.gateway.handle(session, message));
  }
}
