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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.reactive.SubscriptionPublisher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Reproduction scenario for issue #26 (delaneytech, 2025-07-18): a subscription whose payload holds
 * a list of objects ({@code intensities: [Intensity]}) is reported to close the gateway websocket
 * silently, while the same subscription without the list works.
 *
 * <p>The test drives the exact graphql-transport-ws frames through the framework-agnostic core:
 * client -> {@link SubscriptionGatewayHandler} -> stitch -> {@link SubscriptionRetriever} -> {@link
 * SubscriptionSourceHandler} <- source frames. It records what the client receives.
 */
class SpectrogramListPayloadTest {

  private static final String ISSUE_SCHEMA =
      "type Query { spectrogramSchema: String }\n"
          + "type Subscription { streamSpectrogram : Spectrogram }\n"
          + "type Spectrogram { timestamp: String, intensities: [Intensity] }\n"
          + "type Intensity { frequency: Float, intensity: Float }";

  private static final String FULL_SELECTION =
      "subscription { streamSpectrogram { timestamp intensities { frequency intensity } } }";
  private static final String TIMESTAMP_ONLY = "subscription { streamSpectrogram { timestamp } }";

  private static final String SPECTROGRAM_WITH_LIST =
      "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"2025-07-18T10:00:00Z\","
          + "\"intensities\":[{\"frequency\":440.0,\"intensity\":0.5},"
          + "{\"frequency\":880.0,\"intensity\":0.25}]}}}";

  static List<Arguments> variants() {
    return List.of(
        Arguments.of(
            "list of objects (issue)", ISSUE_SCHEMA, FULL_SELECTION, SPECTROGRAM_WITH_LIST),
        Arguments.of(
            "timestamp only (reporter: works)",
            ISSUE_SCHEMA,
            TIMESTAMP_ONLY,
            "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"2025-07-18T10:00:00Z\"}}}"),
        Arguments.of(
            "integer literals for Float",
            ISSUE_SCHEMA,
            FULL_SELECTION,
            "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"t\","
                + "\"intensities\":[{\"frequency\":440,\"intensity\":1}]}}}"),
        Arguments.of(
            "empty list",
            ISSUE_SCHEMA,
            FULL_SELECTION,
            "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"t\",\"intensities\":[]}}}"),
        Arguments.of(
            "null list",
            ISSUE_SCHEMA,
            FULL_SELECTION,
            "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"t\",\"intensities\":null}}}"),
        Arguments.of(
            "list of scalars",
            "type Query { q: String }\n"
                + "type Subscription { streamSpectrogram : Spectrogram }\n"
                + "type Spectrogram { timestamp: String, intensities: [Float] }",
            "subscription { streamSpectrogram { timestamp intensities } }",
            "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"t\",\"intensities\":[1.5,2.5]}}}"),
        Arguments.of(
            "list of lists",
            "type Query { q: String }\n"
                + "type Subscription { streamSpectrogram : Spectrogram }\n"
                + "type Spectrogram { timestamp: String, intensities: [[Intensity]] }\n"
                + "type Intensity { frequency: Float, intensity: Float }",
            FULL_SELECTION,
            "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"t\","
                + "\"intensities\":[[{\"frequency\":1.0,\"intensity\":2.0}],[]]}}}"),
        Arguments.of(
            "non-null list [Intensity!]!",
            "type Query { q: String }\n"
                + "type Subscription { streamSpectrogram : Spectrogram! }\n"
                + "type Spectrogram { timestamp: String!, intensities: [Intensity!]! }\n"
                + "type Intensity { frequency: Float!, intensity: Float! }",
            FULL_SELECTION,
            SPECTROGRAM_WITH_LIST));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("variants")
  void payloadIsForwardedToClientUnchanged(
      final String name, final String schema, final String clientQuery, final String nextPayload)
      throws Exception {

    final Scenario scenario = new Scenario(schema);
    final Outcome outcome = scenario.run(clientQuery, nextPayload);

    System.out.println("=== variant: " + name);
    System.out.println("  stitched query sent to source: " + outcome.sourceQuery);
    System.out.println("  thrown by gateway handler    : " + outcome.thrown);
    System.out.println("  source open                  : " + scenario.source.isOpen());
    System.out.println("  client open                  : " + scenario.client.isOpen());
    for (final String frame : scenario.client.sent) {
      System.out.println("  client <- " + frame);
    }

    assertNull(outcome.thrown, "gateway handler threw");
    assertTrue(scenario.client.isOpen(), "client session was closed");
    final SubscriptionMessage next = outcome.clientNext;
    assertNotNull(next, "client got no next frame; frames: " + scenario.client.sent);
    assertEquals("op-1", next.getId());
    // The payload is forwarded exactly as the source sent it (JSON round trip)
    assertEquals(JsonUtils.toMap(nextPayload).orElseThrow(), next.getPayload());
  }

  /** How big is one real spectrogram frame on the wire? Relevant for transport buffer limits. */
  @Test
  void spectrogramFrameSize() throws Exception {
    final StringBuilder intensities = new StringBuilder();
    for (int i = 0; i < 4096; i++) {
      if (i > 0) {
        intensities.append(',');
      }
      intensities
          .append("{\"frequency\":")
          .append(i * 5.3833)
          .append(",\"intensity\":")
          .append(-37.1234567 - i * 0.001)
          .append('}');
    }
    final String payload =
        "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"2025-07-18T10:00:00.000Z\","
            + "\"intensities\":["
            + intensities
            + "]}}}";

    final Scenario scenario = new Scenario(ISSUE_SCHEMA);
    final Outcome outcome = scenario.run(FULL_SELECTION, payload);

    assertNull(outcome.thrown);
    assertNotNull(outcome.clientNext);
    final int frameBytes =
        scenario
            .client
            .sent
            .get(scenario.client.sent.size() - 1)
            .getBytes(StandardCharsets.UTF_8)
            .length;
    System.out.println(
        "=== 4096-bin spectrogram: next frame to client = "
            + frameBytes
            + " bytes ("
            + (frameBytes / 1024)
            + " KiB); Tomcat default inbound text buffer is 8192 bytes");
    assertTrue(frameBytes > 8192);
  }

  /**
   * Diagnostic: what graphql-java itself would yield if a gateway subscribed to the mapped {@link
   * SubscriptionPublisher} instead of its upstream (the samples subscribe to the upstream).
   */
  @Test
  void graphqlJavaMappedPathResolvesNothingFromRawPayload() throws Exception {
    final Scenario scenario = new Scenario(ISSUE_SCHEMA);
    final ExecutionResult result =
        scenario.lilo.stitch(ExecutionInput.newExecutionInput().query(FULL_SELECTION).build());
    final SubscriptionPublisher mapped = result.getData();
    assertNotNull(mapped);

    final List<Object> events = new CopyOnWriteArrayList<>();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);
    mapped.subscribe(
        new Subscriber<ExecutionResult>() {
          @Override
          public void onSubscribe(final Subscription s) {
            s.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(final ExecutionResult executionResult) {
            events.add(executionResult.toSpecification());
            latch.countDown();
          }

          @Override
          public void onError(final Throwable t) {
            error.set(t);
            latch.countDown();
          }

          @Override
          public void onComplete() {}
        });

    final SourceSide source = await(scenario.retriever.sources, 5000);
    source.handler.handleMessage(scenario.source, "{\"type\":\"connection_ack\"}");
    source.handler.handleMessage(
        scenario.source,
        "{\"id\":\"src-1\",\"type\":\"next\",\"payload\":" + SPECTROGRAM_WITH_LIST + "}");

    assertTrue(latch.await(5, TimeUnit.SECONDS));
    System.out.println("=== graphql-java mapped path yields: " + events + " error=" + error.get());
  }

  // ---------------------------------------------------------------------------------------------

  private static final class Outcome {
    String sourceQuery;
    Throwable thrown;
    SubscriptionMessage clientNext;
  }

  private static final class SourceSide {
    final SubscriptionSourceHandler handler;
    final GraphQLQuery query;

    SourceSide(final SubscriptionSourceHandler handler, final GraphQLQuery query) {
      this.handler = handler;
      this.query = query;
    }
  }

  /** A SubscriptionRetriever that binds the gateway's publisher to a fake source session. */
  private static final class FakeSourceRetriever implements SubscriptionRetriever {
    final FakeSession sourceSession;
    final List<SourceSide> sources = new CopyOnWriteArrayList<>();

    FakeSourceRetriever(final FakeSession sourceSession) {
      this.sourceSession = sourceSession;
    }

    @Override
    public void sendQuery(
        final @NotNull io.fria.lilo.LiloContext liloContext,
        final @NotNull io.fria.lilo.SchemaSource schemaSource,
        final @NotNull GraphQLQuery query,
        final @NotNull SubscriptionSourcePublisher publisher,
        final Object localContext) {
      final SubscriptionSourceHandler handler = new SubscriptionSourceHandler(query, publisher);
      handler.handleSessionStart(this.sourceSession);
      this.sources.add(new SourceSide(handler, query));
    }
  }

  private static final class Scenario {
    final FakeSession client = new FakeSession("client");
    final FakeSession source = new FakeSession("source");
    final FakeSourceRetriever retriever = new FakeSourceRetriever(this.source);
    final Lilo lilo;
    final SubscriptionGatewayHandler gateway;

    Scenario(final String schema) {
      final GraphQL remote =
          GraphQL.newGraphQL(
                  new SchemaGenerator()
                      .makeExecutableSchema(
                          new SchemaParser().parse(schema),
                          RuntimeWiring.newRuntimeWiring().build()))
              .build();
      this.lilo =
          Lilo.builder()
              .addSource(
                  RemoteSchemaSource.create(
                      "spectro",
                      new TestUtils.TestIntrospectionRetriever(remote),
                      new TestUtils.TestQueryRetriever(remote),
                      this.retriever))
              .build();
      this.gateway = new SubscriptionGatewayHandler(this.lilo);
    }

    Outcome run(final String clientQuery, final String nextPayload) throws Exception {
      final Outcome outcome = new Outcome();

      this.gateway.handleMessage(this.client, "{\"type\":\"connection_init\",\"payload\":{}}");
      await(() -> !this.client.sent.isEmpty(), 5000);
      assertEquals(
          SubscriptionMessageType.connection_ack, this.client.lastMessage().getType(), "no ack");

      final String subscribe =
          "{\"id\":\"op-1\",\"type\":\"subscribe\",\"payload\":"
              + JsonUtils.toStr(Map.of("query", clientQuery))
              + "}";
      try {
        this.gateway.handleMessage(this.client, subscribe);
      } catch (final RuntimeException e) {
        outcome.thrown = e;
        return outcome;
      }

      final SourceSide sourceSide = await(this.retriever.sources, 5000);
      // Source acknowledges, handler sends subscribe with the stitched query
      sourceSide.handler.handleMessage(this.source, "{\"type\":\"connection_ack\"}");
      await(() -> this.source.sent.size() >= 2, 5000);
      final SubscriptionMessage sourceSubscribe = this.source.lastMessage();
      assertEquals(SubscriptionMessageType.subscribe, sourceSubscribe.getType());
      @SuppressWarnings("unchecked")
      final Map<String, Object> request = (Map<String, Object>) sourceSubscribe.getPayload();
      outcome.sourceQuery = String.valueOf(request.get("query"));

      // Source emits one event
      final String nextFrame =
          "{\"id\":\""
              + sourceSubscribe.getId()
              + "\",\"type\":\"next\",\"payload\":"
              + nextPayload
              + "}";
      try {
        sourceSide.handler.handleMessage(this.source, nextFrame);
      } catch (final RuntimeException e) {
        outcome.thrown = e;
        return outcome;
      }

      await(() -> this.client.firstOfType(SubscriptionMessageType.next).isPresent(), 5000);
      outcome.clientNext = this.client.firstOfType(SubscriptionMessageType.next).orElse(null);
      return outcome;
    }
  }

  private static <T> T await(final List<T> list, final long millis) throws InterruptedException {
    await(() -> !list.isEmpty(), millis);
    assertTrue(!list.isEmpty(), "nothing arrived within " + millis + " ms");
    return list.get(0);
  }

  private static void await(final BooleanSupplier condition, final long millis)
      throws InterruptedException {
    final long deadline = System.currentTimeMillis() + millis;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }

  /** Minimal recording session; compiles against HEAD and origin/main session wrapper APIs. */
  static final class FakeSession implements WebSocketSessionWrapper {
    final String id;
    final List<String> sent = new CopyOnWriteArrayList<>();
    final AtomicBoolean open = new AtomicBoolean(true);

    FakeSession(final String id) {
      this.id = id;
    }

    @Override
    public void close() {
      this.open.set(false);
    }

    public @NotNull Map<String, Object> getAttributes() {
      return Map.of();
    }

    @Override
    public String getId() {
      return this.id;
    }

    @Override
    public boolean isOpen() {
      return this.open.get();
    }

    @Override
    public void send(final @NotNull String message) {
      this.sent.add(message);
    }

    SubscriptionMessage lastMessage() {
      return JsonUtils.toObj(this.sent.get(this.sent.size() - 1), SubscriptionMessage.class)
          .orElseThrow();
    }

    Optional<SubscriptionMessage> firstOfType(final SubscriptionMessageType type) {
      final List<SubscriptionMessage> messages = new ArrayList<>();
      for (final String raw : this.sent) {
        messages.add(JsonUtils.toObj(raw, SubscriptionMessage.class).orElseThrow());
      }
      return messages.stream().filter(m -> m.getType() == type).findFirst();
    }
  }
}
