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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/** The default executor {@link SubscriptionGatewayHandler} stitches its operations on. */
final class StitchExecutors {

  private static final int POOL_CORE_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
  private static final int POOL_QUEUE_CAPACITY = 256;
  private static final long POOL_KEEP_ALIVE_SECONDS = 60;
  private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

  private StitchExecutors() {}

  /** The bounded pool of daemon threads a handler owns unless it is given an executor. */
  static @NotNull Executor newDefault() {
    return newDefault(POOL_CORE_SIZE);
  }

  /**
   * The default pool with another core size: it grows to four times that (at most {@link
   * Integer#MAX_VALUE} threads) and queues up to 256 more requests.
   */
  static @NotNull Executor newDefault(final int coreSize) {
    if (coreSize <= 0) {
      throw new IllegalArgumentException("coreSize must be greater than zero");
    }

    final int maxSize = coreSize > Integer.MAX_VALUE / 4 ? Integer.MAX_VALUE : 4 * coreSize;
    final ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            coreSize,
            maxSize,
            POOL_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
            StitchExecutors::newThread);
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  private static @NotNull Thread newThread(final @NotNull Runnable runnable) {
    final Thread thread =
        new Thread(runnable, "lilo-subscription-" + THREAD_COUNT.incrementAndGet());
    thread.setDaemon(true);
    return thread;
  }
}
