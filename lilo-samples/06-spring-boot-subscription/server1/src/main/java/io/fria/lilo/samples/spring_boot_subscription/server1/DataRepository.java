/*
 * Copyright 2023 the original author or authors.
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
package io.fria.lilo.samples.spring_boot_subscription.server1;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Repository
public class DataRepository implements CommandLineRunner {

  private static final String[] GREETINGS = {"Hi!", "Bonjour!", "Hola!", "Ciao!", "Zdravo!"};

  private final Sinks.Many<String> dataSink;

  public DataRepository() {
    this.dataSink = Sinks.many().unicast().onBackpressureBuffer();
  }

  public Flux<String> getGreetingsStream() {
    return this.dataSink.asFlux();
  }

  @Override
  public void run(final String... args) {

    int counter = 0;

    while (true) {
      this.dataSink.tryEmitNext(GREETINGS[counter++ % GREETINGS.length]);

      try {
        Thread.sleep(5_000);
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
