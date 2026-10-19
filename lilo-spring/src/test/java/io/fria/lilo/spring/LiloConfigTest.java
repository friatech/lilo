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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.SyncQueryRetriever;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

class LiloConfigTest {

  private static final String INIT = "{\"type\":\"connection_init\"}";

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

  private static String subscribe(final String id) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription { ticks }\"}}";
  }

  private static List<String> types(final RecordingWebSocketSession session) {
    return session.sent().stream()
        .map(frame -> JsonUtils.toMap(frame).orElseThrow().get("type"))
        .map(String::valueOf)
        .toList();
  }

  /** A gateway whose stitches block until {@code release}, so every operation stays in flight. */
  private static GatewayWebSocketHandler gateway(
      final LiloConfigProperties properties, final CountDownLatch release) {
    final SubscriptionRetriever retriever =
        (liloContext, schemaSource, query, publisher, localContext) -> {
          try {
            assertTrue(release.await(5, TimeUnit.SECONDS));
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    final SyncIntrospectionRetriever introspection =
        (liloContext, schemaSource, query, localContext) -> TICKER_INTROSPECTION;
    final SyncQueryRetriever queries = (liloContext, schemaSource, query, localContext) -> "{}";
    final Lilo lilo =
        Lilo.builder()
            .addSource(RemoteSchemaSource.create("ticker", introspection, queries, retriever))
            .build();
    return new LiloConfig().gatewayWebSocketHandler(lilo, properties);
  }

  /** Subscribes one operation more than {@code cap} and checks that only the last is rejected. */
  private static void assertCap(final LiloConfigProperties properties, final int cap)
      throws Exception {
    // The pools are not reachable from here: their daemon threads end after 60 idle seconds
    final CountDownLatch release = new CountDownLatch(1);
    try {
      final GatewayWebSocketHandler gateway = gateway(properties, release);
      final RecordingWebSocketSession client = new RecordingWebSocketSession();
      gateway.afterConnectionEstablished(client);
      gateway.handleMessage(client, new TextMessage(INIT));

      // Each stitch is counted when it is submitted, so the cap holds even before any one runs
      for (int i = 0; i <= cap; i++) {
        gateway.handleMessage(client, new TextMessage(subscribe("op" + i)));
      }

      assertEquals(List.of("connection_ack", "error"), types(client));
      assertTrue(client.sent().get(1).contains("\"id\":\"op" + cap + "\""), client.sent().get(1));
      assertTrue(client.sent().get(1).contains("Too many subscriptions on this connection"));
      assertTrue(client.isOpen());
    } finally {
      release.countDown();
    }
  }

  @Test
  void configuredPoolSizeAndOperationCapReachTheGateway() throws Exception {
    final LiloConfigProperties properties = new LiloConfigProperties();
    properties.setMaxOperationsPerSession(1);
    properties.setSubscriptionPoolSize(2);

    assertCap(properties, 1);
  }

  @Test
  void configuredOperationCapAloneKeepsTheDefaultPool() throws Exception {
    final LiloConfigProperties properties = new LiloConfigProperties();
    properties.setMaxOperationsPerSession(2);

    assertCap(properties, 2);
  }

  @Test
  void configuredPoolSizeAloneKeepsTheDefaultCap() throws Exception {
    final LiloConfigProperties properties = new LiloConfigProperties();
    properties.setSubscriptionPoolSize(1);

    assertCap(properties, 50);
  }
}
