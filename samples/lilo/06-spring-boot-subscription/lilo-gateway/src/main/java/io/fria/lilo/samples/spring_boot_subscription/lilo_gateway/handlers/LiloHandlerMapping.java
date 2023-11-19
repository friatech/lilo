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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.handlers;

import static io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.config.LiloConfiguration.LILO_GRAPHQL_WS_PATH;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * This class is needed when we use the same url for subscription and graphql queries. It dispatches
 * requests to needed mapping class and indirectly to adapters.
 */
@Order(Integer.MIN_VALUE)
@Component
public class LiloHandlerMapping implements HandlerMapping {

  private final @NotNull HandlerMapping webSocketHandlerMapping;
  private final @NotNull RequestMappingHandlerMapping requestMappingHandlerMapping;

  public LiloHandlerMapping(
      final @NotNull HandlerMapping webSocketHandlerMapping,
      final @NotNull RequestMappingHandlerMapping requestMappingHandlerMapping) {
    this.webSocketHandlerMapping = webSocketHandlerMapping;
    this.requestMappingHandlerMapping = requestMappingHandlerMapping;
  }

  @Override
  public @Nullable HandlerExecutionChain getHandler(final @NotNull HttpServletRequest request)
      throws Exception {

    if (LILO_GRAPHQL_WS_PATH.equals(request.getRequestURI()) && "GET".equals(request.getMethod())) {
      return this.webSocketHandlerMapping.getHandler(request);
    }

    return this.requestMappingHandlerMapping.getHandler(request);
  }
}
