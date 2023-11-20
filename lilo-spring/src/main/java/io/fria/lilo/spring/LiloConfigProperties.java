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
package io.fria.lilo.spring;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lilo")
public class LiloConfigProperties {

  private @Nullable String graphQlPath = "/graphql";
  private @Nullable String subscriptionPath = "/graphql";

  public @Nullable String getGraphQlPath() {
    return this.graphQlPath;
  }

  public @Nullable String getSubscriptionPath() {
    return this.subscriptionPath;
  }

  public void setGraphQlPath(final @NotNull String graphQlPath) {
    this.graphQlPath = graphQlPath;
  }

  public void setSubscriptionPath(final @NotNull String subscriptionPath) {
    this.subscriptionPath = subscriptionPath;
  }
}
