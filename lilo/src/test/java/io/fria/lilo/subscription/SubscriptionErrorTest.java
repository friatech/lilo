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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SubscriptionErrorTest {

  private static final String SCHEMA =
      "type Query { hello: String }\ntype Subscription { ticks: String }";
  private static final String GENERIC_SOURCE_ERROR = "Subscription source reported an error";

  private final List<CountingPublisher> publishers = new CopyOnWriteArrayList<>();
  private Lilo lilo;
  private SubscriptionGatewayHandler gateway;

  /** Runs inside the data fetcher, i.e. while the gateway is stitching a subscribe. */
  private volatile @NotNull Runnable onStitch = () -> {};

  private static void assertClosed(final @NotNull SubscriptionSourcePublisher publisher) {
    // a completed multicast sink completes late subscribers immediately; an open one would time out
    assertNull(publisher.getFlux().blockLast(Duration.ofSeconds(2)));
  }

  private static void await(final @NotNull CompletableFuture<?> future) {
    assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
  }

  private static void await(final @NotNull CountDownLatch latch) {
    assertDoesNotThrow(() -> assertTrue(latch.await(5, TimeUnit.SECONDS)));
  }

  private static @NotNull List<SubscriptionMessage> messagesFor(
      final @NotNull RecordingSession session, final @NotNull String id) {
    return session.sentMessages().stream()
        .filter(m -> id.equals(m.getId()))
        .collect(Collectors.toList());
  }

  private static @NotNull List<SubscriptionMessage> messagesOfType(
      final @NotNull RecordingSession session, final @NotNull SubscriptionMessageType type) {
    return session.sentMessages().stream()
        .filter(m -> type == m.getType())
        .collect(Collectors.toList());
  }

  private static @NotNull String subscribeMessage(
      final @NotNull String id, final @NotNull String query) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\"subscribe\",\"payload\":{\"query\":\""
        + query
        + "\"}}";
  }

  @SuppressWarnings("unchecked")
  private static @NotNull List<Map<String, Object>> errorsOf(
      final @NotNull SubscriptionMessage message) {
    assertEquals(SubscriptionMessageType.error, message.getType());
    return (List<Map<String, Object>>) assertInstanceOf(List.class, message.getPayload());
  }

  /** Acknowledges the source connection and returns the operation id the handler subscribed. */
  private static @NotNull String ack(
      final @NotNull SubscriptionSourceHandler handler, final @NotNull RecordingSession source) {
    handler.handleSessionStart(source);
    handler.handleMessage(source, "{\"type\":\"connection_ack\"}");
    final List<SubscriptionMessage> subscribes =
        messagesOfType(source, SubscriptionMessageType.subscribe);
    assertEquals(1, subscribes.size());
    return subscribes.get(0).getId();
  }

  private static @NotNull SubscriptionSourceHandler sourceHandler(
      final @NotNull SubscriptionSourcePublisher publisher) {
    final GraphQLQuery query = Mockito.mock(GraphQLQuery.class);
    Mockito.when(query.getRequest())
        .thenReturn(GraphQLRequest.builder().query("subscription { ticks }").build());
    return new SubscriptionSourceHandler(query, publisher);
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
                          final CountingPublisher publisher = new CountingPublisher();
                          this.publishers.add(publisher);
                          this.onStitch.run();
                          return publisher;
                        }))
            .build();

    this.lilo =
        Lilo.builder().addSource(DefinedSchemaSource.create("ticker", SCHEMA, wiring)).build();
    this.gateway = new SubscriptionGatewayHandler(this.lilo);
  }

  // Gateway: invalid subscribe requests ------------------------------------------------------

  @Test
  void missingQueryProducesErrorFrame() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.send(session, "{\"id\":\"1\",\"type\":\"subscribe\",\"payload\":{\"variables\":{}}}");

    final List<SubscriptionMessage> frames = messagesFor(session, "1");
    assertEquals(1, frames.size());
    final List<Map<String, Object>> errors = errorsOf(frames.get(0));
    assertEquals(1, errors.size());
    assertInstanceOf(String.class, errors.get(0).get("message"));
    assertTrue(session.isOpen());
    assertTrue(this.publishers.isEmpty());
  }

  @Test
  void nullOrUnparseablePayloadProducesErrorFrame() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.send(session, "{\"id\":\"1\",\"type\":\"subscribe\"}");
    this.send(session, "{\"id\":\"2\",\"type\":\"subscribe\",\"payload\":\"not a request\"}");
    this.send(session, "{\"id\":\"3\",\"type\":\"subscribe\",\"payload\":[1,2]}");

    for (final String id : List.of("1", "2", "3")) {
      final List<SubscriptionMessage> frames = messagesFor(session, id);
      assertEquals(1, frames.size(), id);
      assertEquals(1, errorsOf(frames.get(0)).size(), id);
    }
    assertTrue(session.isOpen());
    assertTrue(this.publishers.isEmpty());
  }

  @Test
  void invalidQueryProducesErrorFrameWithGraphQLErrors() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.send(session, subscribeMessage("1", "subscription { nope }"));

    final List<SubscriptionMessage> frames = messagesFor(session, "1");
    assertEquals(1, frames.size());
    assertEquals("1", frames.get(0).getId());
    final List<Map<String, Object>> errors = errorsOf(frames.get(0));
    assertFalse(errors.isEmpty());
    final String message = (String) errors.get(0).get("message");
    assertTrue(message.contains("nope"), message);
    assertTrue(session.isOpen());

    // The failed id is not left registered: it may be used again
    this.send(session, subscribeMessage("1", "subscription { ticks }"));
    assertTrue(session.isOpen());
    this.publishers.get(0).send(Map.of("data", Map.of("ticks", "t1")));
    assertEquals(SubscriptionMessageType.next, messagesFor(session, "1").get(1).getType());
  }

  @Test
  void nonSubscriptionOperationProducesErrorFrame() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.send(session, subscribeMessage("1", "{ hello }"));

    final List<SubscriptionMessage> frames = messagesFor(session, "1");
    assertEquals(1, frames.size());
    assertEquals(1, errorsOf(frames.get(0)).size());
    assertTrue(session.isOpen());
  }

  @Test
  void subscribeWithoutIdClosesSession() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    assertDoesNotThrow(
        () ->
            this.gateway.handleMessage(
                session,
                "{\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription { ticks }\"}}"));

    assertFalse(session.isOpen());
    assertTrue(this.publishers.isEmpty());
    assertFalse(this.gateway.tracksSession(session.getId()));
  }

  @Test
  void rejectedSubscribeLeavesNoSessionState() {
    final RecordingSession session = RecordingSession.init(this.gateway);

    this.send(session, "{\"id\":\"1\",\"type\":\"subscribe\",\"payload\":{}}");
    assertFalse(this.gateway.tracksSession(session.getId()));

    // A subscribe that arrives after the socket closed starts nothing and leaves nothing behind
    this.gateway.handleSessionClose(session);
    this.send(session, "{\"id\":\"2\",\"type\":\"subscribe\",\"payload\":{}}");
    this.send(session, subscribeMessage("3", "subscription { ticks }"));

    assertFalse(this.gateway.tracksSession(session.getId()));
    assertTrue(this.publishers.isEmpty());
  }

  // Gateway: upstream failures and races with the asynchronous stitch -----------------------

  @Test
  void upstreamErrorProducesErrorFrameAndCleansUp() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    final SubscriptionSourceHandler handler = sourceHandler(this.publishers.get(0));
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);

    handler.handleMessage(
        source,
        "{\"id\":\""
            + sourceId
            + "\",\"type\":\"error\",\"payload\":[{\"message\":\"boom\",\"path\":[\"ticks\"]}]}");

    final List<SubscriptionMessage> frames = messagesFor(session, "a");
    assertEquals(1, frames.size());
    assertEquals(
        List.of(Map.of("message", "boom", "path", List.of("ticks"))), errorsOf(frames.get(0)));
    assertTrue(session.isOpen());

    // The failed operation is gone: its id can be subscribed again without a duplicate-id close
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    assertTrue(session.isOpen());
    assertEquals(2, this.publishers.size());
  }

  @Test
  void unexpectedSourceErrorPayloadIsReportedGenerically() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    this.send(session, subscribeMessage("b", "subscription { ticks }"));
    final SubscriptionSourceHandler handlerA = sourceHandler(this.publishers.get(0));
    final SubscriptionSourceHandler handlerB = sourceHandler(this.publishers.get(1));
    final RecordingSession sourceA = new RecordingSession();
    final RecordingSession sourceB = new RecordingSession();

    handlerA.handleMessage(
        sourceA, "{\"id\":\"x\",\"type\":\"error\",\"payload\":{\"secret\":\"internal\"}}");
    handlerB.handleMessage(
        sourceB, "{\"id\":\"x\",\"type\":\"error\",\"payload\":[{\"message\":5}]}");

    for (final String id : List.of("a", "b")) {
      final List<SubscriptionMessage> frames = messagesFor(session, id);
      assertEquals(1, frames.size(), id);
      assertEquals(List.of(Map.of("message", GENERIC_SOURCE_ERROR)), errorsOf(frames.get(0)), id);
    }
    assertTrue(session.isOpen());
  }

  @Test
  void sourceErrorMessageIsBoundedInTheException() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = sourceHandler(publisher);
    // Escaped in the JSON text; the parsed message starts with a newline and a bell character
    final String longMessage = "a\\nb\\u0007" + "x".repeat(1000);

    handler.handleMessage(
        new RecordingSession(),
        "{\"id\":\"1\",\"type\":\"error\",\"payload\":[{\"message\":\"" + longMessage + "\"}]}");

    final Throwable error =
        assertThrows(
            RuntimeException.class, () -> publisher.getFlux().blockLast(Duration.ofSeconds(2)));
    final String message = error.getMessage();
    // 200 characters of the source text are kept: "a\nb\u0007" plus 196 x
    assertTrue(message.contains("a b " + "x".repeat(196)), message);
    assertFalse(message.contains("x".repeat(197)), message);
    assertTrue(message.chars().noneMatch(Character::isISOControl), message);
  }

  @Test
  void completeBeforeStitchFinishesCancelsTheOperation() {
    final CountDownLatch stitching = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    this.onStitch =
        () -> {
          stitching.countDown();
          await(release);
        };
    final RecordingSession session = RecordingSession.init(this.gateway);

    final CompletableFuture<Void> subscribed =
        this.gateway.handle(session, subscribeMessage("a", "subscription { ticks }"));
    await(stitching);
    this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");
    release.countDown();
    await(subscribed);

    final CountingPublisher publisher = this.publishers.get(0);
    assertClosed(publisher);
    assertEquals(1, publisher.closeCalls.get());
    publisher.send(Map.of("data", Map.of("ticks", "late")));
    assertTrue(messagesFor(session, "a").isEmpty());
    assertTrue(session.isOpen());

    // The cancelled id is free again
    this.onStitch = () -> {};
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    assertTrue(session.isOpen());
    this.publishers.get(1).send(Map.of("data", Map.of("ticks", "a1")));
    assertEquals(1, messagesFor(session, "a").size());
  }

  @Test
  void sessionCloseBeforeStitchFinishesCancelsTheOperation() {
    final CountDownLatch stitching = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    this.onStitch =
        () -> {
          stitching.countDown();
          await(release);
        };
    final RecordingSession session = RecordingSession.init(this.gateway);

    final CompletableFuture<Void> subscribed =
        this.gateway.handle(session, subscribeMessage("a", "subscription { ticks }"));
    await(stitching);
    this.gateway.handleSessionClose(session);
    final int sentAtClose = session.sent().size();
    release.countDown();
    await(subscribed);

    final CountingPublisher publisher = this.publishers.get(0);
    assertClosed(publisher);
    assertEquals(1, publisher.closeCalls.get());
    assertEquals(sentAtClose, session.sent().size());
    assertFalse(this.gateway.tracksSession(session.getId()));
  }

  // Source handler lifecycle -----------------------------------------------------------------

  @Test
  void sourceHandlerSubscribesOnceForManyNextMessages() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = sourceHandler(publisher);
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);

    for (int i = 0; i < 3; i++) {
      handler.handleMessage(
          source,
          "{\"id\":\""
              + sourceId
              + "\",\"type\":\"next\",\"payload\":{\"data\":{\"ticks\":\""
              + i
              + "\"}}}");
    }
    publisher.close();

    final List<SubscriptionMessage> completes =
        messagesOfType(source, SubscriptionMessageType.complete);
    assertEquals(1, completes.size());
    assertEquals(sourceId, completes.get(0).getId());
    assertFalse(source.isOpen());
  }

  @Test
  void eventsSentBeforeTheGatewaySubscribesAreNotLost() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = sourceHandler(publisher);
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);

    handler.handleMessage(
        source,
        "{\"id\":\"" + sourceId + "\",\"type\":\"next\",\"payload\":{\"data\":{\"ticks\":\"1\"}}}");

    // The gateway subscribes only once its asynchronous stitch completes
    assertEquals(
        Map.of("data", Map.of("ticks", "1")),
        publisher.getFlux().blockFirst(Duration.ofSeconds(2)));
  }

  @Test
  void sourceCompleteCompletesTheGatewayOperationOnce() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    final SubscriptionSourceHandler handler = sourceHandler(this.publishers.get(0));
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);

    handler.handleMessage(source, "{\"id\":\"" + sourceId + "\",\"type\":\"next\",\"payload\":{}}");
    handler.handleMessage(source, "{\"id\":\"" + sourceId + "\",\"type\":\"complete\"}");

    final List<SubscriptionMessage> frames = messagesFor(session, "a");
    assertEquals(2, frames.size());
    assertEquals(SubscriptionMessageType.next, frames.get(0).getType());
    assertEquals(SubscriptionMessageType.complete, frames.get(1).getType());
    assertTrue(session.isOpen());
    // The source ended the operation itself: it is not told to complete it again
    assertTrue(messagesOfType(source, SubscriptionMessageType.complete).isEmpty());
    assertFalse(source.isOpen());
  }

  @Test
  void clientCompleteTellsTheSourceToCompleteOnce() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    final SubscriptionSourceHandler handler = sourceHandler(this.publishers.get(0));
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);

    this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");

    final List<SubscriptionMessage> completes =
        messagesOfType(source, SubscriptionMessageType.complete);
    assertEquals(1, completes.size());
    assertEquals(sourceId, completes.get(0).getId());
    assertFalse(source.isOpen());
    assertTrue(messagesFor(session, "a").isEmpty());
  }

  @Test
  void sourcePingIsAnsweredWithPongEchoingPayload() {
    final RecordingSession source = new RecordingSession();
    final SubscriptionSourceHandler handler = sourceHandler(new SubscriptionSourcePublisher());

    handler.handleMessage(source, "{\"type\":\"ping\",\"payload\":{\"a\":1}}");

    final List<SubscriptionMessage> sent = source.sentMessages();
    assertEquals(1, sent.size());
    assertEquals(SubscriptionMessageType.pong, sent.get(0).getType());
    assertEquals(Map.of("a", 1), sent.get(0).getPayload());
    assertTrue(source.isOpen());
  }

  @Test
  void malformedSourceMessageClosesSessionAndFailsPublisher() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = sourceHandler(publisher);
    final RecordingSession source = new RecordingSession();

    handler.handleMessage(source, "{not json");

    assertFalse(source.isOpen());
    assertThrows(
        RuntimeException.class, () -> publisher.getFlux().blockLast(Duration.ofSeconds(2)));
  }

  @Test
  void sourceNextWithoutPayloadClosesSessionAndFailsPublisher() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler = sourceHandler(publisher);
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);

    assertDoesNotThrow(
        () -> handler.handleMessage(source, "{\"id\":\"" + sourceId + "\",\"type\":\"next\"}"));

    assertFalse(source.isOpen());
    assertThrows(
        RuntimeException.class, () -> publisher.getFlux().blockLast(Duration.ofSeconds(2)));
  }

  // Cancellation racing deliveries, failing client sends, and where stitching runs ------------

  @Test
  void cancelWhileTheSourceIsDeliveringReleasesEverySource() throws InterruptedException {
    final RecordingSession session = RecordingSession.init(this.gateway);
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    this.send(session, subscribeMessage("b", "subscription { ticks }"));
    final CountingPublisher publisherA = this.publishers.get(0);
    final CountingPublisher publisherB = this.publishers.get(1);
    final SubscriptionSourceHandler handlerA = sourceHandler(publisherA);
    final SubscriptionSourceHandler handlerB = sourceHandler(publisherB);
    final RecordingSession sourceA = new RecordingSession();
    final RecordingSession sourceB = new RecordingSession();
    final String sourceIdA = ack(handlerA, sourceA);
    ack(handlerB, sourceB);

    // The source thread delivers a next for "a" and is held inside the client send
    final CountDownLatch delivering = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    session.onSend(
        message -> {
          if (message.contains("\"next\"")) {
            delivering.countDown();
            await(release);
          }
        });
    final Thread sourceThread =
        new Thread(
            () ->
                handlerA.handleMessage(
                    sourceA, "{\"id\":\"" + sourceIdA + "\",\"type\":\"next\",\"payload\":{}}"));
    sourceThread.start();
    await(delivering);

    // Meanwhile the client stops "a" and then the socket closes, both on another thread
    await(
        CompletableFuture.runAsync(
            () -> {
              this.gateway.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");
              this.gateway.handleSessionClose(session);
            }));

    release.countDown();
    sourceThread.join(5000);
    assertFalse(sourceThread.isAlive());

    assertNull(publisherA.getTermination().block(Duration.ofSeconds(2)));
    assertClosed(publisherA);
    assertEquals(1, messagesOfType(sourceA, SubscriptionMessageType.complete).size());
    assertFalse(sourceA.isOpen());
    assertClosed(publisherB);
    assertEquals(1, messagesOfType(sourceB, SubscriptionMessageType.complete).size());
    assertFalse(sourceB.isOpen());
    assertFalse(this.gateway.tracksSession(session.getId()));
  }

  @Test
  void failingClientSendReleasesTheSource() {
    final RecordingSession session = RecordingSession.init(this.gateway);
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    final SubscriptionSourceHandler handler = sourceHandler(this.publishers.get(0));
    final RecordingSession source = new RecordingSession();
    final String sourceId = ack(handler, source);
    session.onSend(
        message -> {
          throw new IllegalStateException("client socket is broken");
        });

    assertDoesNotThrow(
        () ->
            handler.handleMessage(
                source, "{\"id\":\"" + sourceId + "\",\"type\":\"next\",\"payload\":{}}"));

    assertClosed(this.publishers.get(0));
    assertEquals(1, messagesOfType(source, SubscriptionMessageType.complete).size());
    assertFalse(source.isOpen());

    // The broken operation is gone, so its id is free again
    session.onSend(message -> {});
    this.send(session, subscribeMessage("a", "subscription { ticks }"));
    assertTrue(session.isOpen());
    assertEquals(2, this.publishers.size());
  }

  @Test
  void stitchDoesNotRunOnTheCallingThread() {
    final List<Thread> fetcherThreads = new CopyOnWriteArrayList<>();
    final SubscriptionGatewayHandler remoteGateway =
        new SubscriptionGatewayHandler(remoteLilo(fetcherThreads));
    final RecordingSession session = RecordingSession.init(remoteGateway);

    // The first subscribe also loads the schema; the second runs against the cached schema
    await(remoteGateway.handle(session, subscribeMessage("a", "subscription { ticks }")));
    await(remoteGateway.handle(session, subscribeMessage("b", "subscription { ticks }")));

    assertEquals(2, fetcherThreads.size());
    for (final Thread thread : fetcherThreads) {
      assertNotSame(Thread.currentThread(), thread, thread.getName());
    }
    // With the schema cached nothing else is asynchronous: the handler's own pool ran the stitch
    final Thread cached = fetcherThreads.get(1);
    assertTrue(cached.getName().startsWith("lilo-subscription-"), cached.getName());
    assertTrue(cached.isDaemon(), cached.getName());
  }

  @Test
  void stitchRunsOnTheGivenExecutor() {
    final List<Thread> fetcherThreads = new CopyOnWriteArrayList<>();
    final AtomicInteger executions = new AtomicInteger();
    final Executor executor =
        command -> {
          executions.incrementAndGet();
          new Thread(command, "custom-executor").start();
        };
    final SubscriptionGatewayHandler remoteGateway =
        new SubscriptionGatewayHandler(remoteLilo(fetcherThreads), executor);
    final RecordingSession session = RecordingSession.init(remoteGateway);

    await(remoteGateway.handle(session, subscribeMessage("a", "subscription { ticks }")));
    await(remoteGateway.handle(session, subscribeMessage("b", "subscription { ticks }")));

    assertEquals(2, executions.get());
    assertEquals("custom-executor", fetcherThreads.get(1).getName());
  }

  @Test
  void closeDuringAThrowingDeliveryStillFiresTermination() {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    // A raw subscriber that closes the publisher from inside onNext and then throws
    publisher.subscribe(
        new org.reactivestreams.Subscriber<>() {
          @Override
          public void onSubscribe(final org.reactivestreams.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(final Object item) {
            publisher.close();
            throw new IllegalStateException("delivery failed");
          }

          @Override
          public void onError(final Throwable throwable) {}

          @Override
          public void onComplete() {}
        });

    assertThrows(IllegalStateException.class, () -> publisher.send("event"));

    // The termination signal, which releases the source, fires regardless. The data flux itself
    // cannot be checked: a subscriber that throws from onNext leaves reactor's sink unable to
    // deliver its terminal signal, whatever the publisher does afterwards.
    assertNull(publisher.getTermination().block(Duration.ofSeconds(2)));
  }

  @Test
  void subscribeCompleteLoopCannotBypassTheSessionCap() {
    final CountDownLatch release = new CountDownLatch(1);
    this.onStitch = () -> await(release);
    final SubscriptionGatewayHandler capped =
        new SubscriptionGatewayHandler(this.lilo, command -> new Thread(command).start(), 2);
    final RecordingSession session = RecordingSession.init(capped);
    final List<CompletableFuture<Void>> pending = new java.util.ArrayList<>();

    try {
      // Each complete frees the operation's slot, but its stitch still holds a pool thread
      for (int i = 0; i < 4; i++) {
        pending.add(capped.handle(session, subscribeMessage("a" + i, "subscription { ticks }")));
        capped.handleMessage(session, "{\"id\":\"a" + i + "\",\"type\":\"complete\"}");
      }

      for (int i = 0; i < 4; i++) {
        final List<SubscriptionMessage> frames = messagesFor(session, "a" + i);
        if (i < 2) {
          assertTrue(frames.isEmpty(), "a" + i);
        } else {
          assertEquals(1, frames.size(), "a" + i);
          assertEquals(
              List.of(Map.of("message", "Too many subscriptions on this connection")),
              errorsOf(frames.get(0)));
        }
      }
      assertTrue(session.isOpen());
    } finally {
      release.countDown();
    }
    for (final CompletableFuture<Void> future : pending) {
      await(future);
    }

    // The finished stitches no longer count
    this.onStitch = () -> {};
    await(capped.handle(session, subscribeMessage("b", "subscription { ticks }")));
    await(capped.handle(session, subscribeMessage("c", "subscription { ticks }")));
    assertTrue(messagesFor(session, "b").isEmpty());
    assertTrue(messagesFor(session, "c").isEmpty());
    assertTrue(session.isOpen());
  }

  @Test
  void executorFailureProducesErrorFrameAndFreesTheSlot() {
    final AtomicBoolean broken = new AtomicBoolean(true);
    final Executor executor =
        command -> {
          if (broken.get()) {
            throw new IllegalStateException("executor is shut down");
          }
          new Thread(command).start();
        };
    final SubscriptionGatewayHandler capped =
        new SubscriptionGatewayHandler(this.lilo, executor, 1);
    final RecordingSession session = RecordingSession.init(capped);

    assertDoesNotThrow(
        () -> capped.handleMessage(session, subscribeMessage("a", "subscription { ticks }")));

    final List<SubscriptionMessage> frames = messagesFor(session, "a");
    assertEquals(1, frames.size());
    assertEquals(1, errorsOf(frames.get(0)).size());
    assertTrue(session.isOpen());

    // Neither the operation nor its stitch holds the only slot any more
    broken.set(false);
    await(capped.handle(session, subscribeMessage("a", "subscription { ticks }")));
    assertEquals(1, messagesFor(session, "a").size());
    assertEquals(1, this.publishers.size());
  }

  @Test
  void errorFramesOnABrokenSocketDoNotThrow() {
    final SubscriptionGatewayHandler capped =
        new SubscriptionGatewayHandler(this.lilo, Runnable::run, 1);
    final RecordingSession session = RecordingSession.init(capped);
    await(capped.handle(session, subscribeMessage("a", "subscription { ticks }")));
    session.onSend(
        message -> {
          throw new IllegalStateException("client socket is broken");
        });

    // Over the cap
    assertDoesNotThrow(
        () -> capped.handleMessage(session, subscribeMessage("b", "subscription { ticks }")));
    // Invalid payload
    assertDoesNotThrow(
        () -> capped.handleMessage(session, "{\"id\":\"c\",\"type\":\"subscribe\"}"));

    // Rejected by the executor
    final SubscriptionGatewayHandler rejecting =
        new SubscriptionGatewayHandler(
            this.lilo,
            command -> {
              throw new RejectedExecutionException("full");
            });
    final RecordingSession other = RecordingSession.init(rejecting, "other-session");
    other.onSend(
        message -> {
          throw new IllegalStateException("client socket is broken");
        });
    assertDoesNotThrow(
        () -> rejecting.handleMessage(other, subscribeMessage("x", "subscription { ticks }")));
  }

  @Test
  void sanitiserBoundsTextAndStripsLineAndControlCharacters() {
    final String text = "a\r\nb\u0085c\u2028d\u2029e\u0007" + "x".repeat(300);

    final String bounded = SubscriptionSourceException.bound(text);

    assertEquals(200, bounded.length());
    assertEquals("a  b c d e " + "x".repeat(189), bounded);
    assertEquals("plain", SubscriptionSourceException.bound("plain"));
  }

  // Resource bounds -------------------------------------------------------------------------

  @Test
  void rejectedStitchProducesErrorFrameAndLeavesNoOperation() {
    final AtomicBoolean reject = new AtomicBoolean(true);
    final Executor executor =
        command -> {
          if (reject.get()) {
            throw new RejectedExecutionException("full");
          }
          new Thread(command).start();
        };
    final SubscriptionGatewayHandler bounded = new SubscriptionGatewayHandler(this.lilo, executor);
    final RecordingSession session = RecordingSession.init(bounded);

    await(bounded.handle(session, subscribeMessage("a", "subscription { ticks }")));

    final List<SubscriptionMessage> frames = messagesFor(session, "a");
    assertEquals(1, frames.size());
    assertEquals(
        List.of(Map.of("message", "Too many concurrent subscription requests")),
        errorsOf(frames.get(0)));
    assertTrue(session.isOpen());
    assertTrue(this.publishers.isEmpty());

    // The rejected id was not left registered
    reject.set(false);
    await(bounded.handle(session, subscribeMessage("a", "subscription { ticks }")));
    assertTrue(session.isOpen());
    assertEquals(1, this.publishers.size());
  }

  @Test
  void perSessionOperationCapProducesErrorFrameAndFreedSlotIsReusable() {
    final SubscriptionGatewayHandler capped =
        new SubscriptionGatewayHandler(this.lilo, Runnable::run, 2);
    final RecordingSession session = RecordingSession.init(capped);
    final RecordingSession other = RecordingSession.init(capped, "other-session");

    await(capped.handle(session, subscribeMessage("a", "subscription { ticks }")));
    await(capped.handle(session, subscribeMessage("b", "subscription { ticks }")));
    await(capped.handle(session, subscribeMessage("c", "subscription { ticks }")));

    final List<SubscriptionMessage> frames = messagesFor(session, "c");
    assertEquals(1, frames.size());
    assertEquals(
        List.of(Map.of("message", "Too many subscriptions on this connection")),
        errorsOf(frames.get(0)));
    assertTrue(session.isOpen());
    assertEquals(2, this.publishers.size());

    // The cap is per session
    await(capped.handle(other, subscribeMessage("a", "subscription { ticks }")));
    assertEquals(3, this.publishers.size());

    // A completed operation frees its slot
    capped.handleMessage(session, "{\"id\":\"a\",\"type\":\"complete\"}");
    await(capped.handle(session, subscribeMessage("c", "subscription { ticks }")));
    assertEquals(4, this.publishers.size());
    assertEquals(1, messagesFor(session, "c").size());
    assertTrue(session.isOpen());
  }

  @Test
  void operationCapMustBePositive() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SubscriptionGatewayHandler(this.lilo, Runnable::run, 0));
  }

  @Test
  void defaultStitchPoolIsBounded() {
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicBoolean block = new AtomicBoolean(false);
    final SubscriptionGatewayHandler pooled =
        new SubscriptionGatewayHandler(
            remoteLilo(
                (liloContext, schemaSource, query, publisher, localContext) -> {
                  if (block.get()) {
                    await(release);
                  }
                }));
    // Loads the schema, so that later retrievers run on (and block) the pool threads
    final RecordingSession warmUp = RecordingSession.init(pooled, "warm-up");
    await(pooled.handle(warmUp, subscribeMessage("w", "subscription { ticks }")));
    block.set(true);

    // Every pool thread and every queue slot is taken by a blocked retriever; one more is refused
    final int core = Math.max(4, Runtime.getRuntime().availableProcessors());
    final int capacity = 4 * core + 256;
    final List<CompletableFuture<Void>> pending = new java.util.ArrayList<>();
    final List<RecordingSession> sessions = new java.util.ArrayList<>();
    try {
      for (int i = 0; i <= capacity; i++) {
        // Spread over sessions to stay below the per-session cap
        if (i % 40 == 0) {
          sessions.add(RecordingSession.init(pooled, "session-" + sessions.size()));
        }
        pending.add(
            pooled.handle(
                sessions.get(sessions.size() - 1),
                subscribeMessage("op-" + i, "subscription { ticks }")));
      }

      final List<SubscriptionMessage> errorFrames =
          sessions.stream()
              .flatMap(session -> messagesOfType(session, SubscriptionMessageType.error).stream())
              .collect(Collectors.toList());
      assertEquals(1, errorFrames.size());
      assertEquals("op-" + capacity, errorFrames.get(0).getId());
    } finally {
      release.countDown();
    }
    for (final CompletableFuture<Void> future : pending) {
      await(future);
    }
  }

  /** A remote source whose subscription retriever runs inside the gateway's stitch. */
  private static @NotNull Lilo remoteLilo(final @NotNull List<Thread> fetcherThreads) {
    return remoteLilo(
        (liloContext, schemaSource, query, publisher, localContext) ->
            fetcherThreads.add(Thread.currentThread()));
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

  private void send(final @NotNull RecordingSession session, final @NotNull String message) {
    await(this.gateway.handle(session, message));
  }

  /** Upstream that counts close() calls. */
  private static final class CountingPublisher extends SubscriptionSourcePublisher {

    private final AtomicInteger closeCalls = new AtomicInteger();

    @Override
    public void close() {
      this.closeCalls.incrementAndGet();
      super.close();
    }
  }
}
