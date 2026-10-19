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

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Defaults and checks of the gateway's limits, shared by {@link GatewayWebSocketHandler} and {@link
 * LiloConfigProperties} so the properties do not depend on the handler.
 */
final class GatewayLimits {

  /** Default of the send time limit of a client session. */
  static final Duration DEFAULT_SEND_TIME_LIMIT = Duration.ofSeconds(10);

  /** Default of the send buffer size limit of a client session, in bytes. */
  static final int DEFAULT_SEND_BUFFER_SIZE_LIMIT = 512 * 1024;

  /**
   * Default cap on the operations of one client session, as in {@code SubscriptionGatewayHandler}.
   */
  static final int DEFAULT_MAX_OPERATIONS_PER_SESSION = 50;

  /** Largest stitch pool core size: the pool grows to four times its core size. */
  static final int MAX_SUBSCRIPTION_POOL_SIZE = Integer.MAX_VALUE / 4;

  private GatewayLimits() {}

  /**
   * Default core size of the stitch pool, as in {@code SubscriptionGatewayHandler}: max(4,
   * available processors).
   */
  static int defaultSubscriptionPoolSize() {
    return Math.max(4, Runtime.getRuntime().availableProcessors());
  }

  static int validSendTimeLimitMillis(final @NotNull Duration sendTimeLimit) {
    final long millis = Objects.requireNonNull(sendTimeLimit, "sendTimeLimit").toMillis();
    if (millis <= 0 || millis > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Send time limit must be positive and at most " + Integer.MAX_VALUE + " ms");
    }
    return (int) millis;
  }

  static int validSendBufferSizeLimit(final int sendBufferSizeLimit) {
    if (sendBufferSizeLimit <= 0) {
      throw new IllegalArgumentException("Send buffer size limit must be positive");
    }
    return sendBufferSizeLimit;
  }

  static int validSubscriptionPoolSize(final int subscriptionPoolSize) {
    if (subscriptionPoolSize <= 0 || subscriptionPoolSize > MAX_SUBSCRIPTION_POOL_SIZE) {
      throw new IllegalArgumentException(
          "Subscription pool size must be positive and at most " + MAX_SUBSCRIPTION_POOL_SIZE);
    }
    return subscriptionPoolSize;
  }

  static int validMaxOperationsPerSession(final int maxOperationsPerSession) {
    if (maxOperationsPerSession <= 0) {
      throw new IllegalArgumentException("Max operations per session must be positive");
    }
    return maxOperationsPerSession;
  }
}
