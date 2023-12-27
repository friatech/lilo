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
package io.fria.lilo.samples.spring_boot_basic_authentication.lilo_gateway;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncQueryRetriever;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

class QueryRetrieverImpl implements SyncQueryRetriever {

  private final String schemaUrl;
  private final RestTemplate restTemplate;

  QueryRetrieverImpl(final @NotNull String schemaUrl) {
    this.schemaUrl = schemaUrl;
    this.restTemplate =
        new RestTemplateBuilder() //
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE) //
            .build(); //
  }

  @Override
  public @NotNull String get(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull GraphQLQuery graphQLQuery,
      final @Nullable Object localContext) {

    final HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, (String) localContext);

    return Objects.requireNonNull(
        this.restTemplate
            .exchange(
                this.schemaUrl,
                HttpMethod.POST,
                new HttpEntity<>(graphQLQuery.getQuery(), headers),
                String.class)
            .getBody());
  }
}
