package io.fria.lilo;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefinedSchemaSource implements SchemaSource {

  private DefinedSchemaSource(
      final @NotNull String schemaName,
      final @NotNull String definition,
      final @NotNull RuntimeWiring runtimeWiring) {}

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName,
      final @NotNull String definition,
      final @NotNull RuntimeWiring runtimeWiring) {

    return new DefinedSchemaSource(schemaName, definition, runtimeWiring);
  }

  @Override
  public @NotNull String execute(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull GraphQLQuery query,
      final @Nullable Object localContext) {
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
