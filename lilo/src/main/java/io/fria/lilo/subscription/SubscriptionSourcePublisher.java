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
package io.fria.lilo.subscription;

import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class SubscriptionSourcePublisher implements Publisher<Object> {

  private final @NotNull Sinks.Many<Object> sink = Sinks.many().multicast().onBackpressureBuffer();

  public void close() {
    this.sink.emitComplete((signalType, emitResult) -> false);
  }

  public Flux<Object> getFlux() {
    return this.sink.asFlux();
  }

  public void send(@NotNull final Object message) {
    this.sink.tryEmitNext(message);
  }

  @Override
  public void subscribe(final Subscriber<? super Object> subscriber) {
    this.sink.asFlux().subscribe(subscriber);
  }
}
