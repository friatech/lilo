package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static io.fria.lilo.JsonUtils.toObj;

public abstract class SchemaSource {

  @NotNull
  abstract CompletableFuture<ExecutionResult> execute(
      @NotNull LiloContext liloContext, @NotNull GraphQLQuery query, @Nullable Object localContext);

  @NotNull
  abstract String getName();

  @NotNull
  abstract RuntimeWiring getRuntimeWiring();

  @NotNull
  abstract TypeDefinitionRegistry getTypeDefinitionRegistry();

  abstract void invalidate();

  abstract boolean isSchemaLoaded();

  abstract void loadSchema(@NotNull LiloContext context, @Nullable Object localContext);
}
