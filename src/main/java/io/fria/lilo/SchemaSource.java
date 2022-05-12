package io.fria.lilo;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SchemaSource {

  @NotNull
  String execute(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
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

  void loadSchema(final LiloContext context, final @Nullable Object localContext);
}
