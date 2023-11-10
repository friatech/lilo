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
import io.fria.lilo.subscription.SessionAdapter;
import io.fria.lilo.subscription.SubscriptionGatewayHandler;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class GatewayWebSocketHandler extends AbstractWebSocketHandler {

  private @NotNull final Lilo lilo;
  private @Nullable SubscriptionGatewayHandler gatewayHandler;
  private @Nullable GatewayWebSocketHandler.ClientSessionAdapter clientSessionAdapter;

  public GatewayWebSocketHandler(final @NotNull Lilo lilo) {
    this.lilo = lilo;
  }

  @Override
  public void afterConnectionEstablished(final @NotNull WebSocketSession clientSession) {
    this.clientSessionAdapter = new ClientSessionAdapter(clientSession);
    this.gatewayHandler = new SubscriptionGatewayHandler(this.lilo, this.clientSessionAdapter);
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message) {

    if (this.clientSessionAdapter == null || this.gatewayHandler == null) {
      throw new IllegalStateException(
          "Client Session Adapter and Gateway Handler must be initialized");
    }

    this.gatewayHandler.handleMessage(message.getPayload());
  }

  @Override
  public void afterConnectionClosed(
      final @NotNull WebSocketSession session, final @NotNull CloseStatus status) {

    if (this.clientSessionAdapter == null) {
      throw new IllegalStateException("Client Session Adapter must be initialized");
    }

    this.clientSessionAdapter.closeSession();
  }

  private record ClientSessionAdapter(@NotNull WebSocketSession clientSession)
      implements SessionAdapter {

    @Override
    public void closeSession() {
      try {
        this.clientSession.close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void sendMessage(final @NotNull String eventMessage) {
      try {
        this.clientSession.sendMessage(new TextMessage(eventMessage));
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
