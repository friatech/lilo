package io.fria.lilo.basic_stitching_sample.lilo_gateway;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncQueryRetriever;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

class QueryRetrieverImpl implements SyncQueryRetriever {

  private final String schemaUrl;
  private final RestTemplate restTemplate;

  QueryRetrieverImpl(final @NotNull String schemaUrl) {
    this.schemaUrl = schemaUrl + "/graphql";
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

    return Objects.requireNonNull(
        this.restTemplate.postForObject(this.schemaUrl, graphQLQuery.getQuery(), String.class));
  }
}
