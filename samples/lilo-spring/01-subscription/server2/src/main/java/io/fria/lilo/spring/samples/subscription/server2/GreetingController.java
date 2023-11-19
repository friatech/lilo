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
package io.fria.lilo.spring.samples.subscription.server2;

import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Controller
public class GreetingController {

  private @NotNull final DataRepository repository;

  public GreetingController(final @NotNull DataRepository repository) {
    this.repository = repository;
  }

  @QueryMapping
  public @NotNull String greeting2() {
    return "Hello from Server 2";
  }

  @SubscriptionMapping
  public @NotNull Publisher<String> greeting2Subscription() {

    final Sinks.Many<String> dataSink = Sinks.many().unicast().onBackpressureError();
    final Flux<String> flux = dataSink.asFlux();
    final AtomicBoolean dataGenerationIsStopped = new AtomicBoolean(false);

    this.repository.generateData(dataSink, dataGenerationIsStopped);

    return flux.doOnError(t -> dataGenerationIsStopped.set(true))
        .doOnComplete(() -> dataGenerationIsStopped.set(true))
        .doOnCancel(() -> dataGenerationIsStopped.set(true))
        .doOnTerminate(() -> dataGenerationIsStopped.set(true));
  }
}
