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
import io.fria.lilo.subscription.SubscriptionGatewayHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * GatewayWebSocketHandler manages websocket session interaction between Gateway and connected
 * GraphQL client.
 */
@Component
public class GatewayWebSocketHandler extends AbstractWebSocketHandler {

  private final @NotNull SubscriptionGatewayHandler gatewayHandler;

  public GatewayWebSocketHandler(final @NotNull Lilo lilo) {
    this.gatewayHandler = new SubscriptionGatewayHandler(lilo);
  }

  @Override
  public void afterConnectionClosed(
      final @Nullable WebSocketSession nativeSession, final @NotNull CloseStatus status) {
    this.gatewayHandler.handleSessionClose(ClientSessionWrapper.wrap(nativeSession));
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession nativeSession, final @NotNull TextMessage message) {
    this.gatewayHandler.handleMessage(
        ClientSessionWrapper.wrap(nativeSession), message.getPayload());
  }
}
