package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static io.fria.lilo.JsonUtils.toObj;

public final class DefinedSchemaSource implements SchemaSource {

  private final String schemaName;
  private final String definition;
  private final RuntimeWiring runtimeWiring;
  private TypeDefinitionRegistry typeDefinitionRegistry;
  private GraphQL graphQL;

  private DefinedSchemaSource(
      final @NotNull String schemaName,
      final @NotNull String definition,
      final @NotNull RuntimeWiring runtimeWiring) {

    this.schemaName = schemaName;
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
  public @NotNull String getName() {
    return this.schemaName;
  }

  @Override
  public @NotNull RuntimeWiring getRuntimeWiring() {
    return this.runtimeWiring;
  }

  @Override
  public @NotNull TypeDefinitionRegistry getTypeDefinitionRegistry() {
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
  public @NotNull CompletableFuture<SchemaSource> loadSchema(
      final @NotNull LiloContext context, final @Nullable Object localContext) {

    return CompletableFuture.supplyAsync(() -> loadSchema(DefinedSchemaSource.this));
  }
}
