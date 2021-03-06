package io.fria.lilo.basic_stitching_sample.lilo_gateway;

import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

class IntrospectionRetrieverImpl implements SyncIntrospectionRetriever {

  private final String schemaUrl;
  private final RestTemplate restTemplate;

  IntrospectionRetrieverImpl(final @NonNull String schemaUrl) {
    this.schemaUrl = schemaUrl + "/graphql";
    this.restTemplate =
        new RestTemplateBuilder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public @NonNull String get(
      final @NonNull LiloContext liloContext,
      final @NonNull SchemaSource schemaSource,
      final @NonNull String query,
      final @Nullable Object localContext) {

    return Objects.requireNonNull(
        this.restTemplate.postForObject(this.schemaUrl, query, String.class));
  }
}
