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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.Lilo;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SubscriptionProtocolTest {

  private SubscriptionGatewayHandler gateway;

  @BeforeEach
  void setUp() {
    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "trivial",
                    "type Query { hello: String }",
                    RuntimeWiring.newRuntimeWiring().build()))
            .build();
    this.gateway = new SubscriptionGatewayHandler(lilo);
  }

  @Test
  void pingIsAnsweredWithPong() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.gateway.handleMessage(session, "{\"type\":\"ping\",\"payload\":{\"a\":1}}");

    final List<SubscriptionMessage> sent = session.sentMessages();
    assertEquals(2, sent.size());
    assertEquals(SubscriptionMessageType.pong, sent.get(1).getType());
    assertEquals(java.util.Map.of("a", 1), sent.get(1).getPayload());
    assertTrue(session.isOpen());
  }

  @Test
  void pongIsIgnored() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.gateway.handleMessage(session, "{\"type\":\"pong\"}");

    assertEquals(1, session.sent().size());
    assertTrue(session.isOpen());
  }

  @Test
  void unknownMessageTypeClosesSession() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.gateway.handleMessage(session, "{\"type\":\"bogus\"}");

    assertFalse(session.isOpen());
  }

  @Test
  void serverOnlyMessageFromClientClosesSession() {
    final RecordingSession ackSession = RecordingSession.init(this.gateway);
    this.gateway.handleMessage(ackSession, "{\"type\":\"connection_ack\"}");
    assertFalse(ackSession.isOpen());

    final RecordingSession nextSession = RecordingSession.init(this.gateway);
    this.gateway.handleMessage(nextSession, "{\"id\":\"1\",\"type\":\"next\",\"payload\":{}}");
    assertFalse(nextSession.isOpen());
  }

  @Test
  void errorMessageFromClientClosesSession() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.gateway.handleMessage(session, "{\"id\":\"1\",\"type\":\"error\",\"payload\":[]}");

    assertFalse(session.isOpen());
  }

  @Test
  void messageWithoutTypeClosesSession() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.gateway.handleMessage(session, "{\"id\":\"1\"}");

    assertFalse(session.isOpen());
  }

  @Test
  void sourceHandlerIgnoresClientOnlyMessages() {
    final RecordingSession session = new RecordingSession();
    final SubscriptionSourceHandler handler = this.sourceHandler(new SubscriptionSourcePublisher());

    handler.handleMessage(session, "{\"type\":\"connection_init\"}");
    handler.handleMessage(session, "{\"id\":\"1\",\"type\":\"subscribe\",\"payload\":{}}");
    handler.handleMessage(session, "{\"type\":\"pong\"}");

    assertEquals(0, session.sent().size());
    assertTrue(session.isOpen());
  }

  @Test
  void sourceHandlerClosesOnMessageWithoutType() {
    final RecordingSession session = new RecordingSession();
    final SubscriptionSourceHandler handler = this.sourceHandler(new SubscriptionSourcePublisher());

    handler.handleMessage(session, "{\"id\":\"1\"}");

    assertFalse(session.isOpen());
  }

  @Test
  void malformedJsonClosesSession() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.gateway.handleMessage(session, "{not json");

    assertFalse(session.isOpen());
  }

  @Test
  void sourceHandlerAnswersPingWithPong() {
    final RecordingSession session = new RecordingSession();
    final SubscriptionSourceHandler handler = this.sourceHandler(new SubscriptionSourcePublisher());

    handler.handleMessage(session, "{\"type\":\"ping\"}");

    assertEquals(1, session.sent().size());
    assertEquals(SubscriptionMessageType.pong, session.sentMessages().get(0).getType());
  }

  @Test
  void sourceCompleteClosesPublisher() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = this.sourceHandler(publisher);

    handler.handleMessage(new RecordingSession(), "{\"id\":\"1\",\"type\":\"complete\"}");

    // blockLast returns (null for an empty flux) only if the flux completes; an error would throw
    assertNull(publisher.getFlux().blockLast(Duration.ofSeconds(2)));
  }

  @Test
  void sourceErrorFailsPublisher() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = this.sourceHandler(publisher);

    handler.handleMessage(
        new RecordingSession(),
        "{\"id\":\"1\",\"type\":\"error\",\"payload\":[{\"message\":\"upstream boom\"}]}");

    final Throwable error =
        assertThrows(
            RuntimeException.class, () -> publisher.getFlux().blockLast(Duration.ofSeconds(2)));
    assertTrue(error.getMessage().contains("upstream boom"), error.getMessage());
  }

  private SubscriptionSourceHandler sourceHandler(final SubscriptionSourcePublisher publisher) {
    return new SubscriptionSourceHandler(Mockito.mock(GraphQLQuery.class), publisher);
  }
}
