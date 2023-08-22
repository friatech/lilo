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
import java.lang.reflect.Method;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final Lilo lilo;

  public WebSocketConfig(final Lilo lilo) {
    this.lilo = lilo;
  }

  @Override
  public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {

    try {
      final Method setOrderMethod = registry.getClass().getDeclaredMethod("setOrder", int.class);
      setOrderMethod.invoke(registry, Integer.MIN_VALUE);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    registry
        .addHandler(new MyWebSocketHandler(this.lilo), "/graphql")
        .addInterceptors(
            new HandshakeInterceptor() {
              @Override
              public void afterHandshake(
                  final ServerHttpRequest request,
                  final ServerHttpResponse response,
                  final WebSocketHandler wsHandler,
                  final Exception exception) {}

              @Override
              public boolean beforeHandshake(
                  final ServerHttpRequest request,
                  final ServerHttpResponse response,
                  final WebSocketHandler wsHandler,
                  final Map<String, Object> attributes) {
                response.getHeaders().add("Sec-WebSocket-Protocol", "graphql-transport-ws");
                return true;
              }
            })
        .setHandshakeHandler(new DefaultHandshakeHandler())
        .setAllowedOrigins("*");
  }
}
