package io.fria.lilo.subscription;

import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class SubscriptionSourcePublisher implements Publisher, SessionAdapter {

  private final @NotNull Sinks.Many<Object> sink = Sinks.many().multicast().onBackpressureBuffer();

  @Override
  public void closeSession() {
    this.sink.tryEmitComplete();
  }

  @Override
  public void sendMessage(@NotNull final String eventMessage) {
    this.sink.tryEmitNext(eventMessage);
  }

  public Flux<Object> getFlux() {
    return this.sink.asFlux();
  }

  @Override
  public void subscribe(final @NotNull Subscriber subscriber) {
    this.sink.asFlux().subscribe(subscriber);
  }
}
