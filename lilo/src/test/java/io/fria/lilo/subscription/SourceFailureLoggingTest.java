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

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * A failed operation is logged once: at WARN when the gateway or the transport saw something go
 * wrong, at DEBUG when the source itself reported the error (e.g. an authorization failure).
 */
class SourceFailureLoggingTest {

  private static final String SCHEMA =
      "type Query { hello: String }\ntype Subscription { ticks: String }";

  /** Fails the operation with the given error and returns the gateway's WARN lines about it. */
  private static @NotNull List<String> warningsFor(final @NotNull Throwable failure)
      throws Exception {

    final AtomicReference<SubscriptionSourcePublisher> upstream = new AtomicReference<>();
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator()
                    .makeExecutableSchema(
                        new SchemaParser().parse(SCHEMA), RuntimeWiring.newRuntimeWiring().build()))
            .build();
    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "remote",
                    new TestUtils.TestIntrospectionRetriever(remote),
                    new TestUtils.TestQueryRetriever(remote),
                    (liloContext, schemaSource, query, publisher, localContext) ->
                        upstream.set(publisher)))
            .build();
    // A direct executor: the operation is attached once handle() completes
    final SubscriptionGatewayHandler gateway = new SubscriptionGatewayHandler(lilo, Runnable::run);
    final RecordingSession client = RecordingSession.init(gateway);
    gateway
        .handle(
            client,
            "{\"id\":\"log-op\",\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription {"
                + " ticks }\"}}")
        .get(5, TimeUnit.SECONDS);
    assertNotNull(upstream.get(), "the subscription did not reach the source");

    final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    final PrintStream original = System.err;
    // slf4j-simple writes to the current System.err; the failure is logged on this thread
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      upstream.get().error(failure);
    } finally {
      System.setErr(original);
    }

    assertEquals(
        SubscriptionMessageType.error,
        client.sentMessages().get(client.sentMessages().size() - 1).getType(),
        "the client is told in every case");
    return captured
        .toString(StandardCharsets.UTF_8)
        .lines()
        .filter(line -> line.contains(" WARN ") && line.contains("log-op"))
        .collect(Collectors.toList());
  }

  @Test
  void errorReportedBySourceIsNotAWarning() throws Exception {
    final List<String> warnings =
        warningsFor(
            SubscriptionSourceException.fromPayload(List.of(Map.of("message", "Not authorized"))));

    assertEquals(List.of(), warnings);
  }

  @Test
  void protocolViolationIsLoggedOnceAsAWarning() throws Exception {
    final List<String> warnings =
        warningsFor(
            SubscriptionSourceException.protocolViolation(
                "Subscription source closed the connection: 1006"));

    assertEquals(1, warnings.size(), String.valueOf(warnings));
  }

  @Test
  void failureOutsideTheSourceIsLoggedOnceAsAWarning() throws Exception {
    // e.g. lilo-spring's SourceWebSocketHandler failing an oversized message
    final List<String> warnings =
        warningsFor(new IllegalStateException("Subscription source message exceeds 64 characters"));

    assertEquals(1, warnings.size(), String.valueOf(warnings));
  }
}
