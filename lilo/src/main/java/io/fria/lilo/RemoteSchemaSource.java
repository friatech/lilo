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
package io.fria.lilo;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.toMap;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.fria.lilo.error.LiloInvalidConfigException;
import io.fria.lilo.error.LiloSourceDataFetcherException;
import io.fria.lilo.subscription.SubscriptionRetriever;
import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RemoteSchemaSource extends SchemaSource {

  // LOG, INTROSPECTION_REQUEST and the helpers below are package-private for the retriever flows;
  // sharing LOG keeps the logger category of the messages unchanged
  static final Logger LOG = LoggerFactory.getLogger(RemoteSchemaSource.class);
  static final String INTROSPECTION_REQUEST =
      toStr(
          GraphQLRequest.builder()
              .query(GraphQLRequest.INTROSPECTION_QUERY)
              .operationName("IntrospectionQuery")
              .build());

  private @NotNull final RemoteIntrospectionFlow introspectionFlow;
  private @NotNull final RemoteQueryFlow queryFlow;
  private @Nullable final SubscriptionRetriever subscriptionRetriever;

  private RemoteSchemaSource(
      final @NotNull String schemaName,
      final @NotNull IntrospectionRetriever<?> introspectionRetriever,
      final @NotNull QueryRetriever<?> queryRetriever,
      final @Nullable SubscriptionRetriever subscriptionRetriever) {

    super(schemaName);
    this.introspectionFlow = RemoteIntrospectionFlow.of(introspectionRetriever);
    this.queryFlow = RemoteQueryFlow.of(queryRetriever);
    this.subscriptionRetriever = subscriptionRetriever;
  }

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName, final @NotNull String schemaUrl) {

    return new RemoteSchemaSource(
        Objects.requireNonNull(schemaName),
        new DefaultRemoteIntrospectionRetriever(schemaUrl),
        new DefaultRemoteQueryRetriever(schemaUrl),
        null);
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

  static @Nullable Object fetchData(
      final @NotNull CompletableFuture<ExecutionResult> graphQLResultFuture) {
    final ExecutionResult graphQLResult;

    try {
      graphQLResult = graphQLResultFuture.get();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (final ExecutionException e) {
      throw new RuntimeException(e);
    }

    return fetchData(graphQLResult);
  }

  static @Nullable Object fetchData(final @NotNull ExecutionResult graphQLResult) {

    final List<GraphQLError> errors = graphQLResult.getErrors();
    final boolean hasErrors = errors != null && !errors.isEmpty();
    final Map<String, Object> data = graphQLResult.getData();

    if (data == null) {
      if (hasErrors) {
        throw new LiloSourceDataFetcherException(errors);
      }

      throw new IllegalArgumentException("Remote source returned a response without data");
    }

    final Object value = data.values().iterator().next();

    if (hasErrors) {
      // Partial result: keep the remote data and report the remote errors alongside it
      return DataFetcherResult.newResult().data(value).errors(errors).build();
    }

    return value;
  }

  static @NotNull ExecutionResult toExecutionResult(final @NotNull String queryResult) {

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

    return this.queryFlow.execute(this, liloContext, query, localContext);
  }

  @Override
  @SuppressWarnings("HiddenField")
  public @NotNull CompletableFuture<SchemaSource> loadSchema(
      final @NotNull LiloContext liloContext, final @Nullable Object localContext) {

    return this.introspectionFlow.loadSchema(this, liloContext, localContext);
  }

  private @NotNull CompletableFuture<Object> fetchData(
      final @NotNull GraphQLQuery query,
      final @NotNull LiloContext liloContext,
      final @Nullable Object localContext) {

    return this.queryFlow.fetchData(this, liloContext, query, localContext);
  }

  @SuppressWarnings("HiddenField")
  @NotNull
  SchemaSource fetchIntrospection(
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
    final var typeDefinitionOrNull = typeDefinitionRegistry.getTypeOrNull(typeName);

    if (typeDefinitionOrNull == null) {
      return Optional.empty();
    }

    final ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) typeDefinitionOrNull;
    final List<FieldDefinition> fields = typeDefinition.getFieldDefinitions();

    for (final FieldDefinition field : fields) {
      typeWiringBuilder.dataFetcher(
          field.getName(),
          e -> {
            final Object localContext = e.getLocalContext();
            final var query = QueryTransformer.extractQuery(e);

            if (query.getOperationType() == OperationDefinition.Operation.SUBSCRIPTION) {
              if (this.subscriptionRetriever != null) {
                final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
                this.subscriptionRetriever.sendQuery(
                    liloContext, this, query, publisher, localContext);

                return publisher;
              } else {
                throw new LiloInvalidConfigException("There's no defined SubscriptionRetriever");
              }
            } else {
              return this.fetchData(query, liloContext, localContext);
            }
          });
    }

    return Optional.of(typeWiringBuilder.build());
  }
}
