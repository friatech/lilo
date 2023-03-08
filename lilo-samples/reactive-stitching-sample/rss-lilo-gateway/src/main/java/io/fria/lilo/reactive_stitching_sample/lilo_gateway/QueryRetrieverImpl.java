package io.fria.lilo.reactive_stitching_sample.lilo_gateway;

import io.fria.lilo.AsyncQueryRetriever;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class QueryRetrieverImpl implements AsyncQueryRetriever {

  private final String schemaUrl;
  private final WebClient webClient;

  QueryRetrieverImpl(final @NotNull String schemaUrl) {
    this.schemaUrl = schemaUrl + "/graphql";
    this.webClient =
        WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public @NotNull CompletableFuture<String> get(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull GraphQLQuery graphQLQuery,
      final @Nullable Object localContext) {

    return this.webClient
        .post()
        .uri(this.schemaUrl)
        .bodyValue(graphQLQuery.getQuery())
        .retrieve()
        .toEntity(String.class)
        .mapNotNull(HttpEntity::getBody)
        .toFuture();
  }
}
