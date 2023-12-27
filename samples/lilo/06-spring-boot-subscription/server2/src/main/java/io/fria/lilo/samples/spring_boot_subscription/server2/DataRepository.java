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
package io.fria.lilo.samples.spring_boot_subscription.server2;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Sinks;

@Repository
public class DataRepository {

  private static final int SERVER_ID = 20_000;
  private static final int MAX_CONCURRENT_SESSION = 10;
  private static final int MESSAGE_COUNT_LIMIT = 1000;
  private static final int DELAY_BETWEEN_PUSHES = 1000;
  private static final Logger LOG = LogManager.getLogger();

  private int concurrentSessionId;

  @Async
  public void generateData(
      final @NotNull Sinks.Many<String> dataSink, final AtomicBoolean dataGenerationIsStopped) {

    final int sessionNumber =
        SERVER_ID + (++this.concurrentSessionId % MAX_CONCURRENT_SESSION * MESSAGE_COUNT_LIMIT);

    try {
      dataSink.tryEmitNext("Hello " + sessionNumber);

      for (int i = 0; i < MESSAGE_COUNT_LIMIT; i++) {

        if (dataGenerationIsStopped.get()) {
          break;
        }

        final int streamValue = sessionNumber + i;
        Thread.sleep(DELAY_BETWEEN_PUSHES);
        final String message = streamValue + "";
        dataSink.tryEmitNext(message);
        LOG.info("Sending message {}", message);
      }
    } catch (final InterruptedException e) {
      dataSink.tryEmitComplete();
    } catch (final Exception e) {
      dataSink.tryEmitError(e);
      LOG.error("An error occurred on server {}", SERVER_ID, e);
    } finally {
      dataSink.tryEmitComplete();
      LOG.info("Data generation ended for session {}", concurrentSessionId);
    }
  }
}
