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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

/** The races between attaching an upstream and cancelling the operation. */
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
    final SubscriptionSourcePublisher upstream = new SubscriptionSourcePublisher();
    operation.attach(upstream);

    operation.cancel();
    operation.cancel();

    assertFalse(operation.attach(new SubscriptionSourcePublisher()));
  }
}
