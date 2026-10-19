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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.fria.lilo.subscription.SubscriptionConnectionContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class GatewayHandshakeInterceptorTest {

  private final GatewayHandshakeInterceptor interceptor = new GatewayHandshakeInterceptor();

  private static MockHttpServletRequest upgradeRequest() {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/graphql");
    request.addHeader("Upgrade", "websocket");
    request.addHeader("Authorization", "Bearer token");
    request.addHeader("X-Tenant", "first");
    request.addHeader("X-Tenant", "second");
    return request;
  }

  private Map<String, Object> handshake(
      final MockHttpServletRequest servletRequest, final ServletServerHttpResponse response) {
    final Map<String, Object> attributes = new HashMap<>();
    final boolean proceed =
        this.interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest),
            response,
            mock(WebSocketHandler.class),
            attributes);
    assertTrue(proceed);
    return attributes;
  }

  @Test
  void addsSubProtocolHeader() {
    final ServletServerHttpResponse response =
        new ServletServerHttpResponse(new MockHttpServletResponse());

    this.handshake(upgradeRequest(), response);

    assertEquals(
        List.of("graphql-transport-ws"), response.getHeaders().get("Sec-WebSocket-Protocol"));
  }

  @Test
  void copiesHandshakeHeadersIntoAttributes() {
    final Map<String, Object> attributes =
        this.handshake(
            upgradeRequest(), new ServletServerHttpResponse(new MockHttpServletResponse()));

    final Map<?, ?> headers =
        assertInstanceOf(Map.class, attributes.get(GatewayHandshakeInterceptor.HEADERS_ATTRIBUTE));
    assertEquals("Bearer token", headers.get("authorization"));
    assertEquals("websocket", headers.get("upgrade"));
    // Only the first value of a repeated header is kept
    assertEquals("first", headers.get("x-tenant"));
    assertEquals(
        SubscriptionConnectionContext.HEADERS_ATTRIBUTE,
        GatewayHandshakeInterceptor.HEADERS_ATTRIBUTE);
  }

  @Test
  void copiedHeadersAreUnmodifiable() {
    final Map<String, Object> attributes =
        this.handshake(
            upgradeRequest(), new ServletServerHttpResponse(new MockHttpServletResponse()));

    @SuppressWarnings("unchecked")
    final Map<String, String> headers =
        (Map<String, String>) attributes.get(GatewayHandshakeInterceptor.HEADERS_ATTRIBUTE);
    assertThrows(UnsupportedOperationException.class, () -> headers.put("x", "y"));
  }
}
