package io.fria.lilo.basic_stitching_sample.lilo_gateway;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.QueryRetriever;
import io.fria.lilo.SchemaSource;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

class QueryRetrieverImpl implements QueryRetriever {

  private final String       schemaUrl;
  private final RestTemplate restTemplate;

  QueryRetrieverImpl(@NonNull final String schemaUrl) {
    this.schemaUrl = schemaUrl + "/graphql";
    this.restTemplate = new RestTemplateBuilder() //
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE) //
      .build(); //
  }

  @Override
  public @NonNull String get(
    @NonNull final LiloContext liloContext,
    @NonNull final SchemaSource schemaSource,
    @NonNull final GraphQLQuery graphQLQuery,
    @Nullable final Object localContext) {

    return Objects.requireNonNull(
      this.restTemplate.postForObject(this.schemaUrl, graphQLQuery.getQuery(), String.class)
    );
  }
}
