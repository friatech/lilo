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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.config;

import static io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.config.LiloConfiguration.LILO_GRAPHQL_WS_PATH;

import io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.handlers.GatewayWebSocketHandler;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final @NotNull GatewayWebSocketHandler webSocketHandler;

  public WebSocketConfig(final @NotNull GatewayWebSocketHandler webSocketHandler) {
    this.webSocketHandler = webSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(final @NotNull WebSocketHandlerRegistry registry) {

    registry
        .addHandler(this.webSocketHandler, LILO_GRAPHQL_WS_PATH)
        .addInterceptors(new GatewayHandshakeInterceptor())
        .setAllowedOrigins("*");
  }

  private static class GatewayHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public void afterHandshake(
        final @NotNull ServerHttpRequest request,
        final @NotNull ServerHttpResponse response,
        final @NotNull WebSocketHandler wsHandler,
        final @Nullable Exception exception) {}

    @Override
    public boolean beforeHandshake(
        final @NotNull ServerHttpRequest request,
        final @NotNull ServerHttpResponse response,
        final @NotNull WebSocketHandler wsHandler,
        final @NotNull Map<String, Object> attributes) {
      // Some GraphQL clients need this explicitly
      response.getHeaders().add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      return true;
    }
  }
}
