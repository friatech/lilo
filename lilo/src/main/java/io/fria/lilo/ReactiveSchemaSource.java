package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ReactiveSchemaSource {

  @NotNull
  ExecutionResult execute(
      @NotNull LiloContext liloContext,
      @NotNull ReactiveSchemaSource schemaSource,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);

  @NotNull
  String getName();

  @NotNull
  RuntimeWiring getRuntimeWiring();

  @NotNull
  TypeDefinitionRegistry getTypeDefinitionRegistry();

  void invalidate();

  boolean isSchemaLoaded();

  void loadSchema(@NotNull LiloContext context, @Nullable Object localContext);
}
