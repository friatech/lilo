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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fria.lilo.Lilo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

class SubscriptionWebSocketConfigurerTest {

  private static final String PATH = "/subscriptions";

  private static String[] register(final LiloConfigProperties properties) {
    final GatewayWebSocketHandler handler =
        new GatewayWebSocketHandler(Lilo.builder().build(), PATH);
    final WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
    final WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
    when(registry.addHandler(any(), anyString())).thenReturn(registration);
    when(registration.addInterceptors(any(HandshakeInterceptor[].class))).thenReturn(registration);
    when(registration.setAllowedOriginPatterns(any(String[].class))).thenReturn(registration);

    new SubscriptionWebSocketConfigurer(handler, properties).registerWebSocketHandlers(registry);

    verify(registry).addHandler(handler, PATH);
    verify(registration).addInterceptors(isA(GatewayHandshakeInterceptor.class));
    verify(registration, never()).setAllowedOrigins(any(String[].class));
    final ArgumentCaptor<String[]> patterns = ArgumentCaptor.forClass(String[].class);
    verify(registration).setAllowedOriginPatterns(patterns.capture());
    return patterns.getValue();
  }

  @Test
  void allowsEveryOriginByDefault() {
    assertArrayEquals(new String[] {"*"}, register(new LiloConfigProperties()));
  }

  @Test
  void honoursConfiguredOriginPatterns() {
    final LiloConfigProperties properties = new LiloConfigProperties();
    properties.setAllowedOrigins(List.of("https://a.example", "https://*.b.example"));

    assertArrayEquals(
        new String[] {"https://a.example", "https://*.b.example"}, register(properties));
  }
}
