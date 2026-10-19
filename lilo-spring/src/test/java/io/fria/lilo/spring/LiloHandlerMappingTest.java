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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;

class LiloHandlerMappingTest {

  private static final String PATH = "/graphql";

  private final WebSocketHandlerMapping webSocketHandlerMapping =
      mock(WebSocketHandlerMapping.class);
  private final RequestMappingHandlerMapping requestMappingHandlerMapping =
      mock(RequestMappingHandlerMapping.class);
  private final LiloHandlerMapping mapping =
      new LiloHandlerMapping(PATH, this.webSocketHandlerMapping, this.requestMappingHandlerMapping);

  @Test
  void fallsThroughForNonMatchingRequest() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/other");

    assertNull(this.mapping.getHandler(request));

    verify(this.requestMappingHandlerMapping).getHandler(request);
    verify(this.webSocketHandlerMapping, never()).getHandler(any(HttpServletRequest.class));
  }

  @Test
  void delegatesPostOnSharedPathToRequestMapping() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
    final HandlerExecutionChain chain = new HandlerExecutionChain(new Object());
    when(this.requestMappingHandlerMapping.getHandler(request)).thenReturn(chain);

    assertSame(chain, this.mapping.getHandler(request));

    verify(this.webSocketHandlerMapping, never()).getHandler(any(HttpServletRequest.class));
  }

  @Test
  void plainGetOnSharedPathGoesToWebSocketMapping() throws Exception {
    // Pre-existing behaviour: the mapping looks at the path and method only, not at the Upgrade
    // header, so a plain GET on the shared path is routed to the websocket mapping too
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
    final HandlerExecutionChain chain = new HandlerExecutionChain(new Object());
    when(this.webSocketHandlerMapping.getHandler(request)).thenReturn(chain);

    assertSame(chain, this.mapping.getHandler(request));

    verify(this.requestMappingHandlerMapping, never()).getHandler(any(HttpServletRequest.class));
  }

  @Test
  void delegatesUpgradeOnSharedPathToWebSocketMapping() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
    request.addHeader("Upgrade", "websocket");
    final HandlerExecutionChain chain = new HandlerExecutionChain(new Object());
    when(this.webSocketHandlerMapping.getHandler(request)).thenReturn(chain);

    assertSame(chain, this.mapping.getHandler(request));

    verify(this.requestMappingHandlerMapping, never()).getHandler(any(HttpServletRequest.class));
  }
}
