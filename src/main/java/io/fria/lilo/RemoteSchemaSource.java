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
package io.fria.lilo;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.toMap;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.fria.lilo.error.LiloGraphQLError;
import io.fria.lilo.error.SourceDataFetcherException;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RemoteSchemaSource implements SchemaSource {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteSchemaSource.class);
  private static final String INTROSPECTION_REQUEST =
      toStr(
          GraphQLRequest.builder()
              .query(GraphQLRequest.INTROSPECTION_QUERY)
              .operationName("IntrospectionQuery")
              .build());

  private @NotNull final String schemaName;
  private @NotNull final IntrospectionRetriever<?> introspectionRetriever;
  private @NotNull final QueryRetriever<?> queryRetriever;
  private @Nullable final SubscriptionRetriever subscriptionRetriever;
  private @Nullable TypeDefinitionRegistry typeDefinitionRegistry;
  private RuntimeWiring runtimeWiring;

  private RemoteSchemaSource(
      final @NotNull String schemaName,
      final @NotNull IntrospectionRetriever<?> introspectionRetriever,
      final @NotNull QueryRetriever<?> queryRetriever,
      final @Nullable SubscriptionRetriever subscriptionRetriever) {
    this.schemaName = schemaName;
    this.introspectionRetriever = introspectionRetriever;
    this.queryRetriever = queryRetriever;
    this.subscriptionRetriever = subscriptionRetriever;
  }

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName, final @NotNull String schemaUrl) {

    return new RemoteSchemaSource(
        Objects.requireNonNull(schemaName),
        new DefaultRemoteIntrospectionRetriever(schemaUrl),
        new DefaultRemoteQueryRetriever(schemaUrl),
        new DefaultRemoteSubscriptionRetriever(schemaUrl));
  }

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName,
      final @NotNull IntrospectionRetriever<?> introspectionRetriever,
      final @NotNull QueryRetriever<?> queryRetriever) {

    return new RemoteSchemaSource(
        Objects.requireNonNull(schemaName),
        Objects.requireNonNull(introspectionRetriever),
        Objects.requireNonNull(queryRetriever),
        null);
  }

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName,
      final @NotNull IntrospectionRetriever<?> introspectionRetriever,
      final @NotNull QueryRetriever<?> queryRetriever,
      final @Nullable SubscriptionRetriever subscriptionRetriever) {

    return new RemoteSchemaSource(
        Objects.requireNonNull(schemaName),
        Objects.requireNonNull(introspectionRetriever),
        Objects.requireNonNull(queryRetriever),
        subscriptionRetriever);
  }

  private static @Nullable Object fetchData(
      final @NotNull CompletableFuture<ExecutionResult> graphQLResultFuture) {
    final ExecutionResult graphQLResult;

    try {
      graphQLResult = graphQLResultFuture.get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    final List<GraphQLError> errors = graphQLResult.getErrors();

    if (errors != null && !errors.isEmpty()) {
      throw new SourceDataFetcherException(errors);
    }

    return ((Map<String, Object>) graphQLResult.getData()).values().iterator().next();
  }

  private static @NotNull ExecutionResult toExecutionResult(final @NotNull String queryResult) {

    final Optional<GraphQLResult> graphQLResultOptional = toObj(queryResult, GraphQLResult.class);

    if (graphQLResultOptional.isEmpty()) {
      throw new IllegalArgumentException("DataFetcher caught an empty response");
    }

    return graphQLResultOptional.get();
  }

  @Override
  @SuppressWarnings("HiddenField")
  public @NotNull CompletableFuture<ExecutionResult> execute(
      final @NotNull LiloContext liloContext,
      final @NotNull GraphQLQuery query,
      final @Nullable Object localContext) {

    if (this.queryRetriever instanceof AsyncQueryRetriever) {
      final AsyncQueryRetriever queryRetriever = (AsyncQueryRetriever) this.queryRetriever;
      final var queryResult = queryRetriever.get(liloContext, this, query, localContext);
      return queryResult.thenApply(RemoteSchemaSource::toExecutionResult);
    } else {
      final SyncQueryRetriever queryRetriever = (SyncQueryRetriever) this.queryRetriever;
      final var queryResult = queryRetriever.get(liloContext, this, query, localContext);
      return CompletableFuture.supplyAsync(() -> RemoteSchemaSource.toExecutionResult(queryResult));
    }
  }

  @Override
  public @NotNull String getName() {
    return this.schemaName;
  }

  @Override
  public @NotNull RuntimeWiring getRuntimeWiring() {

    if (this.isSchemaNotLoaded()) {
      throw new IllegalArgumentException(this.schemaName + " has not been loaded yet!");
    }

    return this.runtimeWiring;
  }

  @Override
  public @NotNull TypeDefinitionRegistry getTypeDefinitionRegistry() {

    if (this.isSchemaNotLoaded()) {
      throw new IllegalArgumentException(this.schemaName + " has not been loaded yet!");
    }

    return this.typeDefinitionRegistry;
  }

  @Override
  public void invalidate() {
    this.typeDefinitionRegistry = null;
  }

  @Override
  public boolean isSchemaNotLoaded() {
    return this.typeDefinitionRegistry == null;
  }

  @Override
  @SuppressWarnings("HiddenField")
  public @NotNull CompletableFuture<SchemaSource> loadSchema(
      final @NotNull LiloContext liloContext, final @Nullable Object localContext) {

    // TODO: Maybe there should be async and sync retrievers
    if (this.subscriptionRetriever != null) {
      this.subscriptionRetriever.connect(liloContext, this, localContext);
    }

    if (this.introspectionRetriever instanceof AsyncIntrospectionRetriever) {
      final AsyncIntrospectionRetriever introspectionRetriever =
          (AsyncIntrospectionRetriever) this.introspectionRetriever;

      return introspectionRetriever
          .get(liloContext, this, INTROSPECTION_REQUEST, localContext)
          .thenApply(res -> this.fetchIntrospection(res, liloContext))
          .exceptionally(
              e -> {
                LOG.error(
                    "Could not load introspection for {}", RemoteSchemaSource.this.schemaName);
                LOG.debug("Introspection fetching exception", e);
                return RemoteSchemaSource.this;
              });
    } else {
      final SyncIntrospectionRetriever introspectionRetriever =
          (SyncIntrospectionRetriever) this.introspectionRetriever;

      return CompletableFuture.supplyAsync(
          () -> {
            final String introspectionResponse;

            try {
              introspectionResponse =
                  introspectionRetriever.get(
                      liloContext, RemoteSchemaSource.this, INTROSPECTION_REQUEST, localContext);
            } catch (final Exception e) {
              LOG.error("Could not load introspection for {}", RemoteSchemaSource.this.schemaName);
              LOG.debug("Introspection fetching exception", e);
              return RemoteSchemaSource.this;
            }

            return RemoteSchemaSource.this.fetchIntrospection(introspectionResponse, liloContext);
          });
    }
  }

  private @NotNull CompletableFuture<Object> fetchData(
      final @NotNull GraphQLQuery query,
      final @NotNull LiloContext liloContext,
      final @Nullable Object localContext) {

    if (this.queryRetriever instanceof AsyncQueryRetriever) {
      final var graphQLResultFuture =
          RemoteSchemaSource.this.execute(liloContext, query, localContext);

      return graphQLResultFuture.thenApply(executionResult -> fetchData(graphQLResultFuture));
    } else {
      return CompletableFuture.supplyAsync(
          () -> {
            final var graphQLResultFuture =
                RemoteSchemaSource.this.execute(liloContext, query, localContext);

            return fetchData(graphQLResultFuture);
          });
    }
  }

  @SuppressWarnings("HiddenField")
  private @NotNull SchemaSource fetchIntrospection(
      final @NotNull String introspectionResponse, final @NotNull LiloContext liloContext) {
    final var introspectionResultOptional = toMap(introspectionResponse);

    if (introspectionResultOptional.isEmpty()) {
      throw new IllegalArgumentException("Introspection response is empty");
    }

    final var dataOptional = getMap(introspectionResultOptional.get(), "data");

    if (dataOptional.isEmpty()) {
      throw new IllegalArgumentException(
          "Introspection response is not valid, requires data section");
    }

    final var schemaDoc =
        new IntrospectionResultToSchema().createSchemaDefinition(dataOptional.get());
    final var typeDefinitionRegistry = new SchemaParser().buildRegistry(schemaDoc);
    final var operationTypeNames = SchemaMerger.getOperationTypeNames(typeDefinitionRegistry);

    final RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    this.typeWiring(typeDefinitionRegistry, liloContext, operationTypeNames.getQuery())
        .ifPresent(runtimeWiringBuilder::type);
    this.typeWiring(typeDefinitionRegistry, liloContext, operationTypeNames.getMutation())
        .ifPresent(runtimeWiringBuilder::type);
    this.typeWiring(typeDefinitionRegistry, liloContext, operationTypeNames.getSubscription())
        .ifPresent(runtimeWiringBuilder::type);

    this.runtimeWiring = runtimeWiringBuilder.build();
    this.typeDefinitionRegistry = typeDefinitionRegistry;

    return this;
  }

  @SuppressWarnings("HiddenField")
  private @NotNull Optional<TypeRuntimeWiring> typeWiring(
      final @NotNull TypeDefinitionRegistry typeDefinitionRegistry,
      final @NotNull LiloContext liloContext,
      final @Nullable String typeName) {

    if (typeName == null) {
      return Optional.empty();
    }

    final var typeWiringBuilder = newTypeWiring(typeName);
    final var typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);

    if (typeDefinitionOptional.isEmpty()) {
      return Optional.empty();
    }

    final ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) typeDefinitionOptional.get();
    final List<FieldDefinition> fields = typeDefinition.getFieldDefinitions();

    for (final FieldDefinition field : fields) {
      typeWiringBuilder.dataFetcher(
          field.getName(),
          e -> {
            final var query = QueryTransformer.extractQuery(e);

            // TODO: There's a problem here
            if (query.getOperationType() == OperationDefinition.Operation.SUBSCRIPTION) {
              if (this.subscriptionRetriever != null) {
                this.subscriptionRetriever.sendQuery(liloContext, this, query, e.getLocalContext());
                final Publisher<Object> publisher =
                    this.subscriptionRetriever.subscribe(liloContext, this, e.getLocalContext());

                return publisher;
              } else {
                // TODO: Better exception handling
                throw new Exception("");
              }
            } else {
              return this.fetchData(query, liloContext, e.getLocalContext());
            }
          });
    }

    return Optional.of(typeWiringBuilder.build());
  }

  private static final class GraphQLResult implements ExecutionResult {

    private Map<String, Object> data;
    private List<LiloGraphQLError> errors;
    private Map<Object, Object> extensions;

    @Override
    public @NotNull Map<String, Object> getData() {
      return this.data;
    }

    @Override
    public @Nullable List<GraphQLError> getErrors() {

      if (this.errors == null) {
        return null;
      }

      return this.errors.stream().map(le -> (GraphQLError) le).collect(Collectors.toList());
    }

    @Override
    public @Nullable Map<Object, Object> getExtensions() {
      return this.extensions;
    }

    @Override
    public boolean isDataPresent() {
      return this.data != null;
    }

    @Override
    public @NotNull Map<String, Object> toSpecification() {

      return ExecutionResultImpl.newExecutionResult()
          .data(this.data)
          .errors(this.getErrors())
          .extensions(this.extensions)
          .build()
          .toSpecification();
    }
  }
}
