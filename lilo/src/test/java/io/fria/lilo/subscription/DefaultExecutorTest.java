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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The public stitch pool factory and the constructor that keeps the default pool. */
class DefaultExecutorTest {

  private static final String SCHEMA =
      "type Query { hello: String }\ntype Subscription { ticks: String }";

  private static String subscribe(final String id) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\"subscribe\",\"payload\":{\"query\":\"subscription { ticks }\"}}";
  }

  @Test
  void defaultExecutorIsShapedLikeTheHandlersOwnPool() throws Exception {
    final ThreadPoolExecutor pool =
        assertInstanceOf(ThreadPoolExecutor.class, SubscriptionGatewayHandler.defaultExecutor(3));
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
      assertNotNull(thread);
      assertTrue(thread.isDaemon());
      assertTrue(thread.getName().startsWith("lilo-subscription-"), thread.getName());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void defaultExecutorRejectsNonPositiveSizesAndBoundsTheMaximum() {
    assertThrows(
        IllegalArgumentException.class, () -> SubscriptionGatewayHandler.defaultExecutor(0));
    assertThrows(
        IllegalArgumentException.class, () -> SubscriptionGatewayHandler.defaultExecutor(-1));

    final ThreadPoolExecutor huge =
        assertInstanceOf(
            ThreadPoolExecutor.class,
            SubscriptionGatewayHandler.defaultExecutor(Integer.MAX_VALUE / 4 + 1));
    try {
      assertEquals(Integer.MAX_VALUE, huge.getMaximumPoolSize());
    } finally {
      huge.shutdownNow();
    }
  }

  @Test
  void capOnlyConstructorRejectsNonPositiveCaps() {
    final Lilo lilo = Lilo.builder().build();
    assertThrows(IllegalArgumentException.class, () -> new SubscriptionGatewayHandler(lilo, 0));
  }

  @Test
  void capOnlyConstructorEnforcesTheCap() throws Exception {
    final CountDownLatch stitching = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
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
                    (liloContext, schemaSource, query, publisher, localContext) -> {
                      // Holds the first stitch, so it still counts at the second subscribe
                      stitching.countDown();
                      try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                      } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    }))
            .build();
    final SubscriptionGatewayHandler gateway = new SubscriptionGatewayHandler(lilo, 1);
    final RecordingSession client = RecordingSession.init(gateway);
    try {
      gateway.handleMessage(client, subscribe("a"));
      assertTrue(stitching.await(5, TimeUnit.SECONDS));

      gateway.handle(client, subscribe("b")).get(5, TimeUnit.SECONDS);

      final List<SubscriptionMessage> sent = client.sentMessages();
      assertEquals(2, sent.size(), String.valueOf(client.sent()));
      assertEquals(SubscriptionMessageType.error, sent.get(1).getType());
      assertTrue(
          client.sent().get(1).contains("Too many subscriptions on this connection"),
          client.sent().get(1));
      assertTrue(client.isOpen());
    } finally {
      release.countDown();
    }
  }
}
