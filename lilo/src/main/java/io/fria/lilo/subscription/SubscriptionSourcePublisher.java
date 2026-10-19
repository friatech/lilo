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

import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class SubscriptionSourcePublisher implements Publisher<Object> {

  private final @NotNull Sinks.Many<Object> sink = Sinks.many().multicast().onBackpressureBuffer();

  /**
   * Signals the end of the publisher without being a subscriber of {@link #sink}: a subscriber of a
   * multicast sink takes the events buffered before the first subscriber, so watching for the end
   * through the data flux could steal events from the gateway.
   */
  private final @NotNull Sinks.Empty<Void> termination = Sinks.empty();

  public void close() {
    this.sink.emitComplete((signalType, emitResult) -> false);
    this.termination.tryEmitEmpty();
  }

  /**
   * Fails the publisher so that every subscriber receives the given error.
   *
   * @param error failure to propagate
   */
  public void error(final @NotNull Throwable error) {
    this.sink.emitError(error, (signalType, emitResult) -> false);
    this.termination.tryEmitEmpty();
  }

  public Flux<Object> getFlux() {
    return this.sink.asFlux();
  }

  /**
   * Returns a signal that completes once the publisher is closed or failed. Subscribing to it does
   * not consume any events.
   *
   * @return completion signal, replayed to late subscribers
   */
  public @NotNull Mono<Void> getTermination() {
    return this.termination.asMono();
  }

  public void send(@NotNull final Object message) {
    this.sink.tryEmitNext(message);
  }

  @Override
  public void subscribe(final Subscriber<? super Object> subscriber) {
    this.sink.asFlux().subscribe(subscriber);
  }
}
