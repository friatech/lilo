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
package io.fria.lilo;

import io.fria.lilo.subscription.SubscriptionRetriever;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;

public class DefaultRemoteSubscriptionRetriever implements SubscriptionRetriever {

  private final String schemaUrl;

  public DefaultRemoteSubscriptionRetriever(final @NotNull String schemaUrl) {
    this.schemaUrl = Objects.requireNonNull(schemaUrl);
  }

  @Override
  public void connect(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @Nullable final Object localContext) {}

  @Override
  public void sendQuery(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @NotNull final GraphQLQuery query,
      @Nullable final Object localContext) {}

  @Override
  public @NotNull Publisher<String> subscribe(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @Nullable final Object localContext) {
    return null;
  }
}
