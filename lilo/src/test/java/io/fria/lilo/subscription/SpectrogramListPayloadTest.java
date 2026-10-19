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
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guard for issue #26: a subscription event that holds a list (the reporter's {@code intensities:
 * [Intensity]}) is forwarded to the client exactly as the source sent it, whatever the shape of the
 * list. The stitched path never maps such events, so lists cannot break it.
 */
class SpectrogramListPayloadTest {

  private static final String INTENSITY = "type Intensity { frequency: Float, intensity: Float }";
  private static final String QUERY_TYPE = "type Query { q: String }\n";
  private static final String SUBSCRIPTION_TYPE =
      "type Subscription { streamSpectrogram: Spectrogram }\n";
  private static final String FULL_SELECTION =
      "subscription { streamSpectrogram { timestamp intensities { frequency intensity } } }";
  private static final String TWO_INTENSITIES =
      "[{\"frequency\":440.0,\"intensity\":0.5},{\"frequency\":880.0,\"intensity\":0.25}]";

  private static String schema(final String intensities) {
    return QUERY_TYPE
        + SUBSCRIPTION_TYPE
        + "type Spectrogram { timestamp: String, intensities: "
        + intensities
        + " }\n"
        + INTENSITY;
  }

  private static String event(final String intensities) {
    return "{\"data\":{\"streamSpectrogram\":{\"timestamp\":\"2025-07-18T10:00:00Z\","
        + "\"intensities\":"
        + intensities
        + "}}}";
  }

  static List<Arguments> variants() {
    return List.of(
        Arguments.of(
            "list of objects", schema("[Intensity]"), FULL_SELECTION, event(TWO_INTENSITIES)),
        Arguments.of("empty list", schema("[Intensity]"), FULL_SELECTION, event("[]")),
        Arguments.of("null list", schema("[Intensity]"), FULL_SELECTION, event("null")),
        Arguments.of(
            "list of scalars",
            schema("[Float]"),
            "subscription { streamSpectrogram { timestamp intensities } }",
            event("[1.5,2.5]")),
        Arguments.of(
            "list of lists",
            schema("[[Intensity]]"),
            FULL_SELECTION,
            event("[[{\"frequency\":1.0,\"intensity\":2.0}],[]]")),
        Arguments.of(
            "non-null list [Intensity!]!",
            QUERY_TYPE
                + "type Subscription { streamSpectrogram: Spectrogram! }\n"
                + "type Spectrogram { timestamp: String!, intensities: [Intensity!]! }\n"
                + "type Intensity { frequency: Float!, intensity: Float! }",
            FULL_SELECTION,
            event(TWO_INTENSITIES)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("variants")
  void listPayloadIsForwardedUnchanged(
      final String name, final String schema, final String clientQuery, final String payload)
      throws Exception {

    final RecordingSession source = new RecordingSession("source");
    final AtomicReference<SubscriptionSourceHandler> sourceHandler = new AtomicReference<>();
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator()
                    .makeExecutableSchema(
                        new SchemaParser().parse(schema), RuntimeWiring.newRuntimeWiring().build()))
            .build();
    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "spectro",
                    new TestUtils.TestIntrospectionRetriever(remote),
                    new TestUtils.TestQueryRetriever(remote),
                    (liloContext, schemaSource, query, publisher, localContext) -> {
                      final SubscriptionSourceHandler handler =
                          new SubscriptionSourceHandler(query, publisher);
                      handler.handleSessionStart(source);
                      sourceHandler.set(handler);
                    }))
            .build();
    // A direct executor: the operation is attached once handle() completes
    final SubscriptionGatewayHandler gateway = new SubscriptionGatewayHandler(lilo, Runnable::run);
    final RecordingSession client = RecordingSession.init(gateway);

    gateway
        .handle(
            client,
            "{\"id\":\"op-1\",\"type\":\"subscribe\",\"payload\":"
                + JsonUtils.toStr(Map.of("query", clientQuery))
                + "}")
        .get(5, TimeUnit.SECONDS);
    final SubscriptionSourceHandler handler = sourceHandler.get();
    assertNotNull(handler, "the subscription did not reach the source");
    handler.handleMessage(source, "{\"type\":\"connection_ack\"}");
    final String sourceId = source.sentMessages().get(1).getId();
    handler.handleMessage(
        source, "{\"id\":\"" + sourceId + "\",\"type\":\"next\",\"payload\":" + payload + "}");

    final List<SubscriptionMessage> nexts =
        client.sentMessages().stream()
            .filter(m -> m.getType() == SubscriptionMessageType.next)
            .collect(Collectors.toList());
    assertEquals(1, nexts.size(), name + ": " + client.sent());
    assertEquals("op-1", nexts.get(0).getId());
    assertEquals(JsonUtils.toMap(payload).orElseThrow(), nexts.get(0).getPayload());
    assertTrue(client.isOpen());
  }
}
