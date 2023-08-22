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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.LiloSubscriptionDefaultServerHandler;
import java.io.IOException;
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
  private final LiloSubscriptionDefaultServerHandler defaultHandler;

  public MyWebSocketHandler(final @NotNull Lilo lilo) {
    this.lilo = lilo;
    this.defaultHandler = new LiloSubscriptionDefaultServerHandler(this.lilo);
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message)
      throws IOException {

    final Object response = this.defaultHandler.handleMessage(message.getPayload());

    if (response instanceof String) {
      session.sendMessage(new TextMessage((String) response));
    } else if (response instanceof Publisher) {
      final Publisher publisher = (Publisher) response;
      publisher.subscribe(
          new Subscriber() {
            @Override
            public void onComplete() {}

            @Override
            public void onError(final Throwable throwable) {}

            @Override
            public void onNext(final Object o) {
              try {
                session.sendMessage(new TextMessage((String) o));
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            }

            @Override
            public void onSubscribe(final Subscription subscription) {}
          });
    }
  }
}
