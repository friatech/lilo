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

import graphql.ExecutionResult;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SchemaSource {

  @NotNull final String schemaName;
  @Nullable volatile RuntimeWiring runtimeWiring;
  @Nullable volatile TypeDefinitionRegistry typeDefinitionRegistry;

  protected SchemaSource(final @NotNull String schemaName) {
    this.schemaName = schemaName;
  }

  @NotNull
  abstract CompletableFuture<ExecutionResult> execute(
      @NotNull LiloContext liloContext, @NotNull GraphQLQuery query, @Nullable Object localContext);

  public @NotNull String getName() {
    return this.schemaName;
  }

  public @NotNull RuntimeWiring getRuntimeWiring() {

    final RuntimeWiring wiring = this.runtimeWiring;

    if (this.isSchemaNotLoaded() || wiring == null) {
      throw new IllegalArgumentException(this.schemaName + " has not been loaded yet!");
    }

    return wiring;
  }

  public @NotNull TypeDefinitionRegistry getTypeDefinitionRegistry() {

    final TypeDefinitionRegistry registry = this.typeDefinitionRegistry;

    if (this.isSchemaNotLoaded() || registry == null) {
      throw new IllegalArgumentException(this.schemaName + " has not been loaded yet!");
    }

    return registry;
  }

  public void invalidate() {
    this.typeDefinitionRegistry = null;
  }

  public boolean isSchemaNotLoaded() {
    return this.typeDefinitionRegistry == null;
  }

  @NotNull
  abstract CompletableFuture<SchemaSource> loadSchema(
      @NotNull LiloContext context, @Nullable Object localContext);
}
