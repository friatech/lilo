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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.fria.lilo.Lilo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;

/** Starts a minimal web application context the way an application enables Lilo. */
class EnableLiloContextTest {

  private static AnnotationConfigWebApplicationContext start(final Class<?> configuration) {
    final AnnotationConfigWebApplicationContext context =
        new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(configuration);
    context.refresh();
    return context;
  }

  private static void assertLiloIsWired(final AnnotationConfigWebApplicationContext context) {
    assertNotNull(context.getBean(GatewayWebSocketHandler.class));
    assertNotNull(context.getBean(LiloHandlerMapping.class));
    final WebSocketHandlerMapping mapping = context.getBean(WebSocketHandlerMapping.class);
    assertEquals(1, mapping.getUrlMap().size());
    assertNotNull(mapping.getUrlMap().get("/graphql"));
  }

  @Test
  void startsWithoutTheApplicationEnablingWebSocket() {
    try (AnnotationConfigWebApplicationContext context = start(LiloOnly.class)) {
      assertLiloIsWired(context);
    }
  }

  @Test
  void startsWhenTheApplicationAlsoEnablesWebSocket() {
    try (AnnotationConfigWebApplicationContext context = start(LiloAndWebSocket.class)) {
      assertLiloIsWired(context);
    }
  }

  @Configuration
  @EnableWebMvc
  @EnableLilo
  static class LiloOnly {

    @Bean
    Lilo lilo() {
      return Lilo.builder().build();
    }
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSocket
  @EnableLilo
  static class LiloAndWebSocket {

    @Bean
    Lilo lilo() {
      return Lilo.builder().build();
    }
  }
}
