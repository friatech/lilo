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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

/** Attaching an upstream, cancelling the operation, and the race between the two. */
class GatewayOperationTest {

  private static GatewayOperation operation() {
    return new GatewayOperation(
        "op", new RecordingSession(), new GatewaySessionRegistry.SessionState());
  }

  @Test
  void cancelAfterAttachClosesTheUpstreamAndDisposesTheSubscription() {
    final GatewayOperation operation = operation();
    final SubscriptionSourcePublisher upstream = new SubscriptionSourcePublisher();
    final AtomicBoolean disposed = new AtomicBoolean();

    assertTrue(operation.attach(upstream));
    operation.setSubscription(
        new Disposable() {
          @Override
          public void dispose() {
            disposed.set(true);
          }
        });
    operation.cancel();

    assertTrue(disposed.get());
    assertTrue(upstream.getTermination().toFuture().isDone());
  }

  @Test
  void attachAfterCancelFailsAndLeavesTheUpstreamToTheCaller() {
    final GatewayOperation operation = operation();
    final SubscriptionSourcePublisher upstream = new SubscriptionSourcePublisher();

    operation.cancel();

    assertFalse(operation.attach(upstream));
    assertFalse(upstream.getTermination().toFuture().isDone(), "the caller owns it");
  }

  @Test
  void subscriptionSetAfterCancelIsDisposedAtOnce() {
    final GatewayOperation operation = operation();
    final AtomicBoolean disposed = new AtomicBoolean();

    operation.cancel();
    operation.setSubscription(
        new Disposable() {
          @Override
          public void dispose() {
            disposed.set(true);
          }
        });

    assertTrue(disposed.get());
  }

  @Test
  void cancelIsIdempotent() {
    final GatewayOperation operation = operation();
    final AtomicInteger closes = new AtomicInteger();
    final AtomicInteger disposals = new AtomicInteger();
    final SubscriptionSourcePublisher upstream =
        new SubscriptionSourcePublisher() {
          @Override
          public void close() {
            closes.incrementAndGet();
            super.close();
          }
        };
    assertTrue(operation.attach(upstream));
    operation.setSubscription(
        new Disposable() {
          @Override
          public void dispose() {
            disposals.incrementAndGet();
          }
        });

    operation.cancel();
    operation.cancel();

    assertEquals(1, closes.get());
    assertEquals(1, disposals.get());
    assertFalse(operation.attach(new SubscriptionSourcePublisher()));
  }

  @Test
  void aConcurrentAttachAndCancelLeaveTheUpstreamToExactlyOneOwner() throws Exception {
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 200; round++) {
        final GatewayOperation operation = operation();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger disposals = new AtomicInteger();
        final SubscriptionSourcePublisher upstream =
            new SubscriptionSourcePublisher() {
              @Override
              public void close() {
                closes.incrementAndGet();
                super.close();
              }
            };
        final Disposable subscription =
            new Disposable() {
              @Override
              public void dispose() {
                disposals.incrementAndGet();
              }
            };
        final CountDownLatch start = new CountDownLatch(1);

        final Future<Boolean> attacher =
            pool.submit(
                () -> {
                  start.await();
                  final boolean attached = operation.attach(upstream);
                  operation.setSubscription(subscription);
                  return attached;
                });
        final Future<?> canceller =
            pool.submit(
                () -> {
                  start.await();
                  operation.cancel();
                  return null;
                });
        start.countDown();
        final boolean attached = attacher.get(10, TimeUnit.SECONDS);
        canceller.get(10, TimeUnit.SECONDS);

        // Attached before the cancel: the cancel closed it. Otherwise the caller still owns it.
        assertEquals(attached ? 1 : 0, closes.get());
        assertEquals(1, disposals.get());
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
