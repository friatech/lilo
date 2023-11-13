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
package io.fria.lilo.samples.subscription.lilo_gateway;

import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiloConfiguration {

  private static final String SOURCE1_NAME = "SERVER1";
  private static final String SOURCE1_GRAPHQL_URL = "http://localhost:8081/graphql";
  private static final String SOURCE1_GRAPHQL_WS_URL = "ws://localhost:8081/graphql";
  private static final String SOURCE2_NAME = "SERVER2";
  private static final String SOURCE2_GRAPHQL_URL = "http://localhost:8082/graphql";
  private static final String SOURCE2_GRAPHQL_WS_URL = "ws://localhost:8082/graphql";

  @Bean
  public @NotNull Lilo lilo() {

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                SOURCE1_NAME,
                new IntrospectionRetrieverImpl(SOURCE1_GRAPHQL_URL),
                new QueryRetrieverImpl(SOURCE1_GRAPHQL_URL),
                new SubscriptionRetrieverImpl(SOURCE1_GRAPHQL_WS_URL)))
        .addSource(
            RemoteSchemaSource.create(
                SOURCE2_NAME,
                new IntrospectionRetrieverImpl(SOURCE2_GRAPHQL_URL),
                new QueryRetrieverImpl(SOURCE2_GRAPHQL_URL),
                new SubscriptionRetrieverImpl(SOURCE2_GRAPHQL_WS_URL)))
        .build();
  }
}
