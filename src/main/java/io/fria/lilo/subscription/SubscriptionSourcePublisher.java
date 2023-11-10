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
