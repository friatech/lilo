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

import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.SubscriptionGatewayHandler;
import io.fria.lilo.subscription.SubscriptionMessage;
import io.fria.lilo.subscription.SubscriptionSessionAdapter;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Flux;

@Component
public class SubscriptionServerHandler extends AbstractWebSocketHandler {

  private final @NotNull SubscriptionGatewayHandler gatewayHandler;

  public SubscriptionServerHandler(final @NotNull Lilo lilo) {
    this.gatewayHandler = new SubscriptionGatewayHandler(lilo);
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message) {

    this.gatewayHandler.handleMessage(
        new GatewaySubscriptionSessionAdapter(session), message.getPayload());
  }

  private class GatewaySubscriptionSessionAdapter implements SubscriptionSessionAdapter {

    private @NotNull final WebSocketSession session;

    public GatewaySubscriptionSessionAdapter(final @NotNull WebSocketSession session) {
      this.session = session;
    }

    @Override
    public void closeSession() {
      try {
        this.session.close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void subscribe(
        @NotNull final Publisher<Object> upstreamPublisher, @NotNull final String requestId) {
      final Flux<Object> upstreamFlux = (Flux<Object>) upstreamPublisher;

      upstreamFlux.subscribe(
          payload -> {
            final var subscriptionMessage =
                SubscriptionMessage.builder().id(requestId).type("next").payload(payload).build();
            this.sendMessage(JsonUtils.toStr(subscriptionMessage));
          },
          throwable -> {},
          this::closeSession);
    }

    @Override
    public void sendMessage(@NotNull final String eventMessage) {
      try {
        this.session.sendMessage(new TextMessage(eventMessage));
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
