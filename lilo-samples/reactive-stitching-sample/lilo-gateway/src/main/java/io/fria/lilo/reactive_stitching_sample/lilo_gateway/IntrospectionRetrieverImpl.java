package io.fria.lilo.reactive_stitching_sample.lilo_gateway;

import io.fria.lilo.AsyncIntrospectionRetriever;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;

class IntrospectionRetrieverImpl implements AsyncIntrospectionRetriever {

  private final String schemaUrl;
  private final RestTemplate restTemplate;

  IntrospectionRetrieverImpl(@NonNull final String schemaUrl) {
    this.schemaUrl = schemaUrl + "/graphql";
    this.restTemplate =
        new RestTemplateBuilder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public @NonNull CompletableFuture<String> get(
      @NonNull final LiloContext liloContext,
      @NonNull final SchemaSource schemaSource,
      @NonNull final String query,
      @Nullable final Object localContext) {

    return Objects.requireNonNull(
        this.restTemplate.postForObject(this.schemaUrl, query, String.class));
  }
}
