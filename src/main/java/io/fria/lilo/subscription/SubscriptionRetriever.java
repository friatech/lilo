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
package io.fria.lilo.subscription;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;

public abstract class SubscriptionRetriever {

  public final @NotNull Publisher<Object> sendQuery(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull GraphQLQuery query,
      final @Nullable Object localContext) {

    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    this.sendQuery(liloContext, schemaSource, query, publisher, localContext);

    return publisher;
  }

  public abstract void sendQuery(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull GraphQLQuery query,
      @NotNull SessionAdapter sessionAdapter,
      @Nullable Object localContext);
}
