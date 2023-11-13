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

import static io.fria.lilo.JsonUtils.toObj;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefinedSchemaSource extends SchemaSource {

  private final String definition;
  private GraphQL graphQL;

  private DefinedSchemaSource(
      final @NotNull String schemaName,
      final @NotNull String definition,
      final @NotNull RuntimeWiring runtimeWiring) {

    super(schemaName);
    this.definition = definition;
    this.runtimeWiring = runtimeWiring;
  }

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName,
      final @NotNull String definition,
      final @NotNull RuntimeWiring runtimeWiring) {

    return new DefinedSchemaSource(schemaName, definition, runtimeWiring);
  }

  private static @NotNull SchemaSource loadSchema(final @NotNull DefinedSchemaSource schemaSource) {

    schemaSource.typeDefinitionRegistry = new SchemaParser().parse(schemaSource.definition);
    final var graphQLSchema =
        new SchemaGenerator()
            .makeExecutableSchema(schemaSource.typeDefinitionRegistry, schemaSource.runtimeWiring);

    schemaSource.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    return schemaSource;
  }

  @Override
  public @NotNull CompletableFuture<ExecutionResult> execute(
      final @NotNull LiloContext liloContext,
      final @NotNull GraphQLQuery query,
      final @Nullable Object localContext) {

    return CompletableFuture.supplyAsync(
        () -> {
          final var graphQLRequestOptional = toObj(query.getQuery(), GraphQLRequest.class);

          if (graphQLRequestOptional.isEmpty()) {
            throw new IllegalArgumentException("Query request is invalid: Empty query");
          }

          return DefinedSchemaSource.this.graphQL.execute(
              graphQLRequestOptional.get().toExecutionInput(localContext));
        });
  }

  @Override
  public @NotNull CompletableFuture<SchemaSource> loadSchema(
      final @NotNull LiloContext context, final @Nullable Object localContext) {

    return CompletableFuture.supplyAsync(() -> loadSchema(DefinedSchemaSource.this));
  }
}
