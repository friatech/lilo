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

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiplexingTest {

  private static final String SCHEMA =
      "type Query { hello: String }\ntype Subscription { ticks: String }";

  private final List<ProbePublisher> publishers = new CopyOnWriteArrayList<>();
  private SubscriptionGatewayHandler gateway;

  /** Runs inside the data fetcher, i.e. while the gateway is stitching a subscribe. */
  private volatile @NotNull Runnable onStitch = () -> {};

  /** When set, new publishers record close() without completing, like a slow upstream. */
  private volatile boolean deferClose;

  private static void assertClosed(final @NotNull SubscriptionSourcePublisher publisher) {
    // a completed multicast sink completes late subscribers immediately; an open one would time out
    assertNull(publisher.getFlux().blockLast(Duration.ofSeconds(2)));
  }

  private static void assertOpen(final @NotNull SubscriptionSourcePublisher publisher) {
    assertThrows(
        IllegalStateException.class, () -> publisher.getFlux().blockLast(Duration.ofMillis(200)));
  }

  private static @NotNull List<SubscriptionMessage> messagesFor(
      final @NotNull RecordingSession session, final @NotNull String id) {
    return session.sentMessages().stream()
        .filter(m -> id.equals(m.getId()))
        .collect(Collectors.toList());
  }

  private static void subscribe(
      final @NotNull SubscriptionGatewayHandler gateway,
      final @NotNull RecordingSession session,
      final @NotNull String id) {
    // Stitching is asynchronous; wait until the operation is registered (or rejected)
    assertDoesNotThrow(
        () ->
            gateway
                .handle(
                    session,
                    "{\"id\":\""
                        + id
                        + "\",\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription { ticks }\"}}")
                .get(5, TimeUnit.SECONDS));
  }

  @BeforeEach
  void setUp() {
    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Subscription")
                    .dataFetcher(
                        "ticks",
                        env -> {
                          final ProbePublisher publisher = new ProbePublisher(this.deferClose);
                          this.publishers.add(publisher);
                          this.onStitch.run();
                          return publisher;
                        }))
            .build();

    final Lilo lilo =
        Lilo.builder().addSource(DefinedSchemaSource.create("ticker", SCHEMA, wiring)).build();
    this.gateway = new SubscriptionGatewayHandler(lilo);
  }

  @Test
  void twoSubscriptionsDeliverIndependently() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    subscribe(this.gateway, session, "b");
    assertEquals(2, this.publishers.size());

    this.publishers.get(0).send(Map.of("data", Map.of("ticks", "a1")));
    this.publishers.get(1).send(Map.of("data", Map.of("ticks", "b1")));

    final List<SubscriptionMessage> forA = messagesFor(session, "a");
    final List<SubscriptionMessage> forB = messagesFor(session, "b");
    assertEquals(1, forA.size());
    assertEquals(SubscriptionMessageType.next, forA.get(0).getType());
    assertEquals(Map.of("data", Map.of("ticks", "a1")), forA.get(0).getPayload());
    assertEquals(1, forB.size());
    assertEquals(SubscriptionMessageType.next, forB.get(0).getType());
    assertEquals(Map.of("data", Map.of("ticks", "b1")), forB.get(0).getPayload());
    assertTrue(session.isOpen());
  }

  @Test
  void completeForOneIdLeavesTheOtherRunning() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    subscribe(this.gateway, session, "b");
    final SubscriptionSourcePublisher publisherA = this.publishers.get(0);
    final SubscriptionSourcePublisher publisherB = this.publishers.get(1);

    this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");

    publisherA.send(Map.of("data", Map.of("ticks", "a1")));
    publisherB.send(Map.of("data", Map.of("ticks", "b1")));

    // The client asked to stop "a": nothing further (not even a complete) is sent for it
    assertTrue(messagesFor(session, "a").isEmpty());
    final List<SubscriptionMessage> forB = messagesFor(session, "b");
    assertEquals(1, forB.size());
    assertEquals(SubscriptionMessageType.next, forB.get(0).getType());
    assertClosed(publisherA);
    assertOpen(publisherB);
    assertTrue(session.isOpen());

    // A late duplicate complete for an already finished id is harmless
    assertDoesNotThrow(
        () -> this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}"));
    assertTrue(session.isOpen());
  }

  @Test
  void completeStopsForwardingEvenIfTheUpstreamIsSlowToClose() {
    this.deferClose = true;
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    subscribe(this.gateway, session, "b");
    final ProbePublisher publisherA = this.publishers.get(0);
    final ProbePublisher publisherB = this.publishers.get(1);

    this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");

    // The source was told to stop "a", but its flux is still live: only disposing the gateway's
    // forwarding subscription keeps these events away from the client
    assertEquals(1, publisherA.closeCalls.get());
    publisherA.send(Map.of("data", Map.of("ticks", "a1")));
    publisherA.finishClose();
    publisherB.send(Map.of("data", Map.of("ticks", "b1")));

    assertTrue(messagesFor(session, "a").isEmpty());
    assertEquals(1, messagesFor(session, "b").size());
    assertEquals(0, publisherB.closeCalls.get());
    assertTrue(session.isOpen());
  }

  @Test
  void sessionCloseDuringStitchingCancelsTheNewOperation() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    // The socket closes after the subscribe for "b" was accepted but before it is registered
    this.onStitch = () -> this.gateway.handleSessionClose(session);

    subscribe(this.gateway, session, "b");

    assertFalse(session.isOpen());
    assertEquals(2, this.publishers.size());
    assertClosed(this.publishers.get(0));
    assertClosed(this.publishers.get(1));
    assertEquals(1, this.publishers.get(1).closeCalls.get());

    // Nothing of the closed session remains: a fresh session (same id) may reuse "b"
    this.onStitch = () -> {};
    final RecordingSession next = RecordingSession.init(this.gateway);
    subscribe(this.gateway, next, "b");
    assertTrue(next.isOpen());
  }

  @Test
  void serverCompletionCompletesOnlyThatOperation() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    subscribe(this.gateway, session, "b");

    this.publishers.get(0).close();
    this.publishers.get(1).send(Map.of("data", Map.of("ticks", "b1")));

    final List<SubscriptionMessage> forA = messagesFor(session, "a");
    assertEquals(1, forA.size());
    assertEquals(SubscriptionMessageType.complete, forA.get(0).getType());
    assertEquals(1, messagesFor(session, "b").size());
    assertTrue(session.isOpen());

    // When the last operation completes the connection still belongs to the client
    this.publishers.get(1).close();
    assertEquals(SubscriptionMessageType.complete, messagesFor(session, "b").get(1).getType());
    assertTrue(session.isOpen());
  }

  @Test
  void sessionCloseCancelsAllOperations() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    subscribe(this.gateway, session, "b");
    final int sentBeforeClose = session.sent().size();

    this.gateway.handleSessionClose(session);

    assertClosed(this.publishers.get(0));
    assertClosed(this.publishers.get(1));
    // Cancelled operations are not reported back to a closed socket
    assertEquals(sentBeforeClose, session.sent().size());
    assertDoesNotThrow(() -> this.gateway.handleSessionClose(session));
    assertDoesNotThrow(
        () -> this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}"));
  }

  @Test
  void closedSessionLeavesNoOperationsBehind() {
    final RecordingSession first = RecordingSession.init(this.gateway);
    subscribe(this.gateway, first, "a");
    this.gateway.handleSessionClose(first);

    // RecordingSession reuses its id, so a leaked entry would make "a" look like a duplicate
    final RecordingSession second = RecordingSession.init(this.gateway);
    subscribe(this.gateway, second, "a");

    assertTrue(second.isOpen());
    assertEquals(2, this.publishers.size());
    assertOpen(this.publishers.get(1));
  }

  @Test
  void duplicateOperationIdClosesSession() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    subscribe(this.gateway, session, "a");

    assertFalse(session.isOpen());
    // The duplicate is rejected before stitching, so no second upstream is ever created
    assertEquals(1, this.publishers.size());
    for (final ProbePublisher publisher : this.publishers) {
      assertClosed(publisher);
      assertEquals(1, publisher.closeCalls.get());
    }
  }

  @Test
  void completedIdCanBeReused() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    subscribe(this.gateway, session, "a");
    this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");
    subscribe(this.gateway, session, "a");

    assertTrue(session.isOpen());
    this.publishers.get(1).send(Map.of("data", Map.of("ticks", "a2")));
    assertEquals(1, messagesFor(session, "a").size());
  }

  /** Upstream double that counts close() calls and can defer the actual completion. */
  private static final class ProbePublisher extends SubscriptionSourcePublisher {

    private final AtomicInteger closeCalls = new AtomicInteger();
    private final boolean deferClose;

    private ProbePublisher(final boolean deferClose) {
      this.deferClose = deferClose;
    }

    @Override
    public void close() {
      this.closeCalls.incrementAndGet();
      if (!this.deferClose) {
        super.close();
      }
    }

    void finishClose() {
      super.close();
    }
  }
}
