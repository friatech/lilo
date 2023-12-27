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

import io.fria.lilo.Lilo;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;

/**
 * LiloConfig is the configuration bean directly bound to EnableLilo annotation. When EnableLilo
 * annotation is used. A singleton instance of this class is created.
 */
@Configuration
@Import({LiloConfigProperties.class, SubscriptionWebSocketConfigurer.class})
public class LiloConfig {

  /**
   * GatewayWebSocketHandler bean definition
   *
   * @param lilo Lilo instance
   * @param configProperties LiloConfig properties for getting subscription path
   * @return GatewayWebSocketHandler bean singleton instance
   */
  @Bean
  public @NotNull GatewayWebSocketHandler gatewayWebSocketHandler(
      final @NotNull Lilo lilo, final @NotNull LiloConfigProperties configProperties) {
    return new GatewayWebSocketHandler(
        lilo, Objects.requireNonNull(configProperties.getSubscriptionPath()));
  }

  /**
   * LiloHandlerMapping bean definition
   *
   * @param configProperties LiloConfig properties for getting subscription path
   * @param webSocketHandlerMapping Spring WebSocketHandlerMapping bean for delegating websocket
   *     requests.
   * @param requestMappingHandlerMapping Spring RequestMappingHandlerMapping bean for delegating
   *     standard web requests.
   * @return LiloHandlerMapping bean singleton instance
   */
  @Bean
  public @Nullable LiloHandlerMapping liloHandlerMapping(
      final @NotNull LiloConfigProperties configProperties,
      final @NotNull WebSocketHandlerMapping webSocketHandlerMapping,
      final @NotNull RequestMappingHandlerMapping requestMappingHandlerMapping) {

    if (configProperties.getGraphQlPath() == null
        || configProperties.getSubscriptionPath() == null) {
      throw new RuntimeException("Lilo configuration error");
    }

    if (!configProperties.getGraphQlPath().equals(configProperties.getSubscriptionPath())) {
      return null;
    }

    return new LiloHandlerMapping(
        Objects.requireNonNull(configProperties.getGraphQlPath()),
        webSocketHandlerMapping,
        requestMappingHandlerMapping);
  }
}
