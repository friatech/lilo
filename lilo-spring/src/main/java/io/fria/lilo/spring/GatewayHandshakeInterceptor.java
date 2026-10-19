/*
 * Copyright 2022-2024 the original author or authors.
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

import io.fria.lilo.subscription.SubscriptionConnectionContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 *
 * <p>It also copies the headers of the upgrade request into the session attributes under {@link
 * #HEADERS_ATTRIBUTE}, as an unmodifiable {@code Map<String, String>} of the lower-cased header
 * name to its first value. The gateway hands them to subscription retrievers in a {@link
 * SubscriptionConnectionContext}.
 */
public class GatewayHandshakeInterceptor implements HandshakeInterceptor {

  /** Session attribute holding the handshake request headers. */
  public static final String HEADERS_ATTRIBUTE = SubscriptionConnectionContext.HEADERS_ATTRIBUTE;

  private static @NotNull Map<String, String> firstValues(
      final @NotNull ServerHttpRequest request) {

    final Map<String, String> headers = new LinkedHashMap<>();

    request
        .getHeaders()
        .forEach(
            (name, values) -> {
              final String value = firstValue(values);
              if (name != null && value != null) {
                // Header names are case-insensitive: the first spelling seen wins
                headers.putIfAbsent(name.toLowerCase(Locale.ROOT), value);
              }
            });

    return Map.copyOf(headers);
  }

  private static @Nullable String firstValue(final @Nullable List<String> values) {
    if (values == null) {
      return null;
    }
    for (final String value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

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
    attributes.put(HEADERS_ATTRIBUTE, firstValues(request));
    return true;
  }
}
