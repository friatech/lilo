package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SchemaSource {

  @NotNull
  CompletableFuture<ExecutionResult> execute(
      @NotNull LiloContext liloContext, @NotNull GraphQLQuery query, @Nullable Object localContext);

  @NotNull
  String getName();

  @NotNull
  RuntimeWiring getRuntimeWiring();

  @NotNull
  TypeDefinitionRegistry getTypeDefinitionRegistry();

  void invalidate();

  boolean isSchemaNotLoaded();

  @NotNull
  CompletableFuture<SchemaSource> loadSchema(
      @NotNull LiloContext context, @Nullable Object localContext);
}
