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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.SyncQueryRetriever;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
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

  @Test
  void configuredOperationCapReachesTheGateway() throws Exception {
    final CountDownLatch stitching = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    // Holds the first operation's stitch, so it still counts when the second subscribe arrives
    final SubscriptionRetriever retriever =
        (liloContext, schemaSource, query, publisher, localContext) -> {
          stitching.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
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
    final LiloConfigProperties properties = new LiloConfigProperties();
    properties.setMaxOperationsPerSession(1);
    properties.setSubscriptionPoolSize(2);

    final GatewayWebSocketHandler gateway =
        new LiloConfig().gatewayWebSocketHandler(lilo, properties);
    final RecordingWebSocketSession client = new RecordingWebSocketSession();
    try {
      gateway.afterConnectionEstablished(client);
      gateway.handleMessage(client, new TextMessage(INIT));
      gateway.handleMessage(client, new TextMessage(subscribe("a")));
      assertTrue(stitching.await(5, TimeUnit.SECONDS));

      gateway.handleMessage(client, new TextMessage(subscribe("b")));

      assertEquals(List.of("connection_ack", "error"), types(client));
      assertTrue(client.sent().get(1).contains("Too many subscriptions on this connection"));
      assertTrue(client.isOpen());
    } finally {
      release.countDown();
    }
  }

  @Test
  void subscriptionPoolIsShapedLikeTheHandlersOwn() throws Exception {
    final ThreadPoolExecutor pool =
        assertInstanceOf(ThreadPoolExecutor.class, SubscriptionPool.create(3));
    try {
      assertEquals(3, pool.getCorePoolSize());
      assertEquals(12, pool.getMaximumPoolSize());
      assertEquals(60, pool.getKeepAliveTime(TimeUnit.SECONDS));
      assertTrue(pool.allowsCoreThreadTimeOut());
      assertEquals(
          256, assertInstanceOf(ArrayBlockingQueue.class, pool.getQueue()).remainingCapacity());

      final ArrayBlockingQueue<Thread> ran = new ArrayBlockingQueue<>(1);
      pool.execute(() -> ran.add(Thread.currentThread()));
      final Thread thread = ran.poll(5, TimeUnit.SECONDS);
      assertTrue(thread != null && thread.isDaemon(), String.valueOf(thread));
      assertTrue(thread.getName().startsWith("lilo-subscription-"), thread.getName());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void subscriptionPoolRejectsInvalidSizes() {
    assertThrows(IllegalArgumentException.class, () -> SubscriptionPool.create(0));
    assertThrows(
        IllegalArgumentException.class, () -> SubscriptionPool.create(Integer.MAX_VALUE / 4 + 1));
  }
}
