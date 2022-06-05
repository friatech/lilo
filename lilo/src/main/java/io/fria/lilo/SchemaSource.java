package io.fria.lilo;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;

public interface SchemaSource {

  @NotNull
  String getName();

  @NotNull
  RuntimeWiring getRuntimeWiring();

  @NotNull
  TypeDefinitionRegistry getTypeDefinitionRegistry();

  void invalidate();
}
