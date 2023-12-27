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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This class provides code completion support for IDEs when using lilo properties in
 * application.yml/properties file. Also provides a programmatic approach to accessing this
 * configuration from other classes.
 */
@ConfigurationProperties(prefix = "lilo")
public class LiloConfigProperties {

  private @Nullable String graphQlPath = "/graphql";
  private @Nullable String subscriptionPath = "/graphql";

  /**
   * GraphQL path which serves query and mutation operations. By default, it's /graphql
   *
   * @return GraphQL query/mutation path
   */
  public @Nullable String getGraphQlPath() {
    return this.graphQlPath;
  }

  /**
   * GraphQL subscription Path, WebSocket handler will be bound to this path. By default, it's
   * /graphql
   *
   * @return GraphQL Subscription path
   */
  public @Nullable String getSubscriptionPath() {
    return this.subscriptionPath;
  }

  /**
   * setter for GraphQL query and mutation path. If not set, it's /graphql
   *
   * @param graphQlPath new GraphQL query / mutation path
   */
  public void setGraphQlPath(final @NotNull String graphQlPath) {
    this.graphQlPath = graphQlPath;
  }

  /**
   * setter for GraphQL subscription path. If not set, it's /graphql
   *
   * @param subscriptionPath new GraphQL subscription path
   */
  public void setSubscriptionPath(final @NotNull String subscriptionPath) {
    this.subscriptionPath = subscriptionPath;
  }
}
