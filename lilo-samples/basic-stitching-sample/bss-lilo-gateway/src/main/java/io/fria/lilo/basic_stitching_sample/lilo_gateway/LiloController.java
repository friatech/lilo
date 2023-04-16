package io.fria.lilo.basic_stitching_sample.lilo_gateway;

import com.atlassian.braid.Braid;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.source.GraphQLRemoteRetriever;
import com.atlassian.braid.source.Query;
import com.atlassian.braid.source.QueryExecutorSchemaSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import static io.fria.lilo.JsonUtils.toMap;
import static io.fria.lilo.JsonUtils.toStr;

@Controller
public class LiloController {

  private static final String SERVER1_NAME = "SERVER1";
  private static final String SERVER1_BASE_URL = "http://localhost:8081";
  private static final String SERVER2_NAME = "SERVER2";
  private static final String SERVER2_BASE_URL = "http://localhost:8082";

  private final Lilo lilo;

  public LiloController() {

    this.lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    SERVER1_NAME,
                    new IntrospectionRetrieverImpl(SERVER1_BASE_URL),
                    new QueryRetrieverImpl(SERVER1_BASE_URL)))
            .addSource(
                RemoteSchemaSource.create(
                    SERVER2_NAME,
                    new IntrospectionRetrieverImpl(SERVER2_BASE_URL),
                    new QueryRetrieverImpl(SERVER2_BASE_URL)))
            .build();
  }

  @ResponseBody
  @PostMapping("/graphql")
  public @NotNull Map<String, Object> stitch(@RequestBody final @NotNull GraphQLRequest request) throws ExecutionException, InterruptedException {

    final RestTemplate restTemplate =  new RestTemplateBuilder()
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .build();;

    Braid braid = Braid.builder()
      .schemaSource(QueryExecutorSchemaSource.builder()
        .namespace(SchemaNamespace.of("foo"))
//        .schemaProvider(new Supplier<Reader>() {
//
//  private static final String INTROSPECTION_REQUEST =
//      toStr(
//          GraphQLRequest.builder()
//              .query(GraphQLRequest.INTROSPECTION_QUERY)
//              .operationName("IntrospectionQuery")
//              .build());
//
//          @Override
//          public Reader get() {
//            final String introspectionResult = restTemplate.postForObject(SERVER1_BASE_URL + "/graphql", INTROSPECTION_REQUEST, String.class);
//            final Map<String, Object> resultMap = toMap(introspectionResult).get();
//            final Map<String, Object>              data      = (Map<String, Object>) resultMap.get("data");
//            final Object                           schema    = data.get("__schema");
//
//            return new StringReader(introspectionResult);
//          }
//        })
        .remoteRetriever(new GraphQLRemoteRetriever<Object>() {
          @Override
          public CompletableFuture<Map<String, Object>> queryGraphQL(final Query query, final Object o) {
            final String result = Objects.requireNonNull(
              restTemplate.postForObject(SERVER1_BASE_URL + "/graphql", query, String.class));
            final Map<String, Object> resultMap = toMap(result).get();

            return CompletableFuture.completedFuture(resultMap);
          }
        })
        .build())

//      .schemaSource(QueryExecutorSchemaSource.builder()
//        // .namespace(SchemaNamespace.of("bar"))
//        .schemaProvider(() -> getResourceAsReader("bar.graphql"))
//        .remoteRetriever(new HttpRestRemoteRetriever(new URL("http://bar.com/graphql")))
//        .build())
      .build();

    return braid.newGraphQL().execute(request.toExecutionInput()).get().toSpecification();
//
//    return this.lilo.stitch(request.toExecutionInput()).toSpecification();
  }
}
