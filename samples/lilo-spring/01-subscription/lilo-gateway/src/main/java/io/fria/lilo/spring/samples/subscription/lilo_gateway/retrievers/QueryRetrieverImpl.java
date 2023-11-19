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
package io.fria.lilo.spring.samples.subscription.lilo_gateway.retrievers;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncQueryRetriever;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

public class QueryRetrieverImpl implements SyncQueryRetriever {

  private final String schemaUrl;
  private final WebClient webClient;

  public QueryRetrieverImpl(final @NotNull String schemaUrl) {
    this.schemaUrl = schemaUrl;
    this.webClient =
        WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public @NotNull String get(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull GraphQLQuery graphQLQuery,
      final @Nullable Object localContext) {

    return Objects.requireNonNull(
        this.webClient
            .post()
            .uri(this.schemaUrl)
            .bodyValue(graphQLQuery.getQuery())
            .retrieve()
            .bodyToMono(String.class)
            .block());
  }
}
