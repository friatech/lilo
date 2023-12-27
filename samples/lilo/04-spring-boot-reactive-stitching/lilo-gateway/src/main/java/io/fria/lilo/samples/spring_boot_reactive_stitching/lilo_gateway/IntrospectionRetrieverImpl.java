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
package io.fria.lilo.samples.spring_boot_reactive_stitching.lilo_gateway;

import io.fria.lilo.AsyncIntrospectionRetriever;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class IntrospectionRetrieverImpl implements AsyncIntrospectionRetriever {

  private final String schemaUrl;
  private final WebClient webClient;

  IntrospectionRetrieverImpl(final @NotNull String schemaUrl) {
    this.schemaUrl = schemaUrl;
    this.webClient =
        WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public @NotNull CompletableFuture<String> get(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull String query,
      final @Nullable Object localContext) {

    return this.webClient
        .post()
        .uri(this.schemaUrl)
        .bodyValue(query)
        .retrieve()
        .toEntity(String.class)
        .mapNotNull(HttpEntity::getBody)
        .toFuture();
  }
}
