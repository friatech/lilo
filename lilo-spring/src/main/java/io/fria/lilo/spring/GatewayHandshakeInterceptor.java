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

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * This interceptor is used for communication between a GraphQL client and Lilo GraphQL Gateway.
 * Main purpose of this interface is just adding "Sec-WebSocket-Protocol" header. This one is
 * mandatory for some GraphQL clients.
 */
public class GatewayHandshakeInterceptor implements HandshakeInterceptor {

  @Override
  public void afterHandshake(
      final @NotNull ServerHttpRequest request,
      final @NotNull ServerHttpResponse response,
      final @NotNull WebSocketHandler wsHandler,
      final @Nullable Exception exception) {
    // No lilo managed logic for after handshaking
  }

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
