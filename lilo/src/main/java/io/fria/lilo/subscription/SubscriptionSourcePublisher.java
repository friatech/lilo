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

import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Publisher between a subscription source and the gateway: the source side {@link #send sends}
 * events and finally {@link #close closes} or {@link #error fails} it; the gateway subscribes to
 * {@link #getFlux()}.
 *
 * <p>The source side and the gateway work on different threads, so a close may land while an event
 * is being delivered. Such a close never throws: the {@link #getTermination() termination} signal
 * fires at once, and the terminal signal of the data flux is emitted by whichever thread finishes
 * the delivery in progress.
 */
public class SubscriptionSourcePublisher implements Publisher<Object> {

  private final @NotNull Sinks.Many<Object> sink = Sinks.many().multicast().onBackpressureBuffer();

  /**
   * Signals the end of the publisher without being a subscriber of {@link #sink}: a subscriber of a
   * multicast sink takes the events buffered before the first subscriber, so watching for the end
   * through the data flux could steal events from the gateway.
   */
  private final @NotNull Sinks.Empty<Void> termination = Sinks.empty();

  /** The requested end of the data flux; the first close or error wins. */
  private final @NotNull AtomicReference<Terminal> terminal = new AtomicReference<>();

  /**
   * Completes the publisher. Never throws, and later calls have no effect. Subclasses that override
   * this method must call {@code super.close()}, otherwise the publisher never ends.
   */
  public void close() {
    this.terminate(new Terminal(null));
  }

  /**
   * Fails the publisher so that every subscriber receives the given error. Never throws, and has no
   * effect once the publisher was closed or failed. Subclasses that override this method must call
   * {@code super.error(error)}, otherwise the publisher never ends.
   *
   * @param error failure to propagate
   */
  public void error(final @NotNull Throwable error) {
    this.terminate(new Terminal(error));
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
    try {
      this.sink.tryEmitNext(message);
    } finally {
      // A close that landed during this delivery could not emit; finish it now, even if the
      // delivery threw
      this.emitTerminal();
    }
  }

  @Override
  public void subscribe(final Subscriber<? super Object> subscriber) {
    this.sink.asFlux().subscribe(subscriber);
  }

  private void terminate(final @NotNull Terminal requested) {
    try {
      this.terminal.compareAndSet(null, requested);
      this.emitTerminal();
    } finally {
      this.termination.tryEmitEmpty();
    }
  }

  private void emitTerminal() {
    final Terminal requested = this.terminal.get();

    if (requested == null) {
      return;
    }

    // Fails with FAIL_NON_SERIALIZED while another thread delivers an event; that thread emits the
    // terminal signal when its delivery returns (see send). Other failures mean it already ended.
    if (requested.error == null) {
      this.sink.tryEmitComplete();
    } else {
      this.sink.tryEmitError(requested.error);
    }
  }

  /** A requested end: completion when {@code error} is null, failure otherwise. */
  private static final class Terminal {

    private final @Nullable Throwable error;

    private Terminal(final @Nullable Throwable error) {
      this.error = error;
    }
  }
}
