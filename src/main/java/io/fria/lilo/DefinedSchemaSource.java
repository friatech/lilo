package io.fria.lilo;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefinedSchemaSource implements SchemaSource {

  private DefinedSchemaSource(
      @NotNull final String schemaName,
      @NotNull final String definition,
      @NotNull final RuntimeWiring runtimeWiring) {}

  public static @NotNull SchemaSource create(
      @NotNull final String schemaName,
      @NotNull final String definition,
      @NotNull final RuntimeWiring runtimeWiring) {

    return new DefinedSchemaSource(schemaName, definition, runtimeWiring);
  }

  @Override
  public @NotNull String execute(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @NotNull final GraphQLQuery query,
      @Nullable final Object localContext) {
    return null;
  }

  @Override
  public @NotNull String getName() {
    return null;
  }

  @Override
  public @NotNull RuntimeWiring getRuntimeWiring() {
    return null;
  }

  @Override
  public @NotNull TypeDefinitionRegistry getTypeDefinitionRegistry() {
    return null;
  }

  @Override
  public void invalidate() {}

  @Override
  public boolean isSchemaLoaded() {
    return false;
  }

  @Override
  public void loadSchema(final LiloContext context, final @Nullable Object localContext) {}
}
