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
package io.fria.lilo.spring;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * This configuration bean is started when @EnableLilo is active. It aims to register
 * GatewayWebSocketHandler object as a default spring AbstractWebSocketHandler class. After that all
 * websocket requests coming to specified graphql path will be handled by Lilo. *
 */
public class SubscriptionWebSocketConfigurer implements WebSocketConfigurer {

  private final @NotNull GatewayWebSocketHandler gatewayWebSocketHandler;

  /**
   * Constructor for creating SubscriptionWebSocketConfigurer
   *
   * @param gatewayWebSocketHandler Lilo's WebSocketHandler for managing all communication between
   *     clients and gateway.
   */
  public SubscriptionWebSocketConfigurer(
      final @NotNull GatewayWebSocketHandler gatewayWebSocketHandler) {
    this.gatewayWebSocketHandler = gatewayWebSocketHandler;
  }

  /**
   * helps to register Lilo's GatewayWebSocketHandler
   *
   * @param registry Spring's global registry for WebSocketHandlers
   */
  @Override
  public void registerWebSocketHandlers(final @NotNull WebSocketHandlerRegistry registry) {

    registry
        .addHandler(
            this.gatewayWebSocketHandler, this.gatewayWebSocketHandler.getSubscriptionPath())
        .addInterceptors(new GatewayHandshakeInterceptor())
        .setAllowedOrigins("*");
  }
}
