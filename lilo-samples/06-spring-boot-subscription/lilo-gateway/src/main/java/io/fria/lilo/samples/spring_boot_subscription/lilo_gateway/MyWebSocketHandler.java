package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.LiloSubscriptionDefaultHandler;
import java.io.IOException;
import java.util.concurrent.Flow;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class MyWebSocketHandler extends AbstractWebSocketHandler {

  private final Lilo lilo;

  public MyWebSocketHandler(final @NotNull Lilo lilo) {
    this.lilo = lilo;
  }

  @Override
  protected void handleTextMessage(final @NotNull WebSocketSession session, final @NotNull TextMessage message) throws IOException {

    final LiloSubscriptionDefaultHandler handler = new LiloSubscriptionDefaultHandler(this.lilo);

    final Object response = handler.handleMessage(message.getPayload());

    if (response instanceof String) {
      session.sendMessage(new TextMessage((String) response));
    } else if (response instanceof Publisher) {
      final Publisher publisher = (Publisher) response;
      publisher.subscribe(new Subscriber() {
        @Override
        public void onSubscribe(final Subscription subscription) {
          System.out.println("xxxxxxxxxxxxxxx");
        }

        @Override
        public void onNext(final Object o) {
          try {
            session.sendMessage(new TextMessage((String) o));
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public void onError(final Throwable throwable) {
          System.out.println("zzzzzzzzzzzzzzz");
        }

        @Override
        public void onComplete() {
          System.out.println("aaaaaaaaaaaaaaa");
        }
      });
    }


  }
}
