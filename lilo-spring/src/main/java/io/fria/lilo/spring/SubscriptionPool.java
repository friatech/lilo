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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * The stitch pool of a {@code SubscriptionGatewayHandler} configured through {@link
 * LiloConfigProperties}. It is shaped like the handler's own default pool, only its core size is
 * configurable: daemon threads named {@code lilo-subscription-N}, growing to four times the core
 * size, with up to 256 queued requests and idle threads ending after 60 seconds.
 */
final class SubscriptionPool {

  private static final int QUEUE_CAPACITY = 256;
  private static final long KEEP_ALIVE_SECONDS = 60;
  private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

  private SubscriptionPool() {}

  static @NotNull Executor create(final int coreSize) {
    GatewayLimits.validSubscriptionPoolSize(coreSize);
    final ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            coreSize,
            4 * coreSize,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            SubscriptionPool::newThread);
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
