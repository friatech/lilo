package io.fria.lilo.reactive_stitching_sample.lilo_gateway;

import io.fria.lilo.AsyncIntrospectionRetriever;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;

class IntrospectionRetrieverImpl implements AsyncIntrospectionRetriever {

  private final String schemaUrl;
  private final WebClient webClient;

  IntrospectionRetrieverImpl(final @NonNull String schemaUrl) {
    this.schemaUrl = schemaUrl + "/graphql";
    this.webClient =
        WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public @NonNull CompletableFuture<String> get(
      final @NonNull LiloContext liloContext,
      final @NonNull SchemaSource schemaSource,
      final @NonNull String query,
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
