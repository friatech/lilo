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

import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The names of the root operation types of a schema. */
final class OperationTypeNames {

  private final String query;
  private final String mutation;
  private final String subscription;

  OperationTypeNames(
      final @NotNull String query,
      final @Nullable String mutation,
      final @Nullable String subscription) {
    this.query = query;
    this.mutation = mutation;
    this.subscription = subscription;
  }

  /**
   * Reads the operation type names from the schema definition, or the default names when the
   * registry has none.
   */
  static @NotNull OperationTypeNames of(final TypeDefinitionRegistry typeDefinitionRegistry) {

    final Optional<SchemaDefinition> schemaDefinitionOptional =
        typeDefinitionRegistry.schemaDefinition();

    if (schemaDefinitionOptional.isEmpty()) {
      return new OperationTypeNames("Query", "Mutation", "Subscription");
    }

    final SchemaDefinition schemaDefinition = schemaDefinitionOptional.get();

    final Map<String, String> names =
        schemaDefinition.getOperationTypeDefinitions().stream()
            .collect(
                Collectors.toMap(OperationTypeDefinition::getName, d -> d.getTypeName().getName()));

    return new OperationTypeNames(
        names.get("query"), names.get("mutation"), names.get("subscription"));
  }

  @Nullable
  String getMutation() {
    return this.mutation;
  }

  @NotNull
  String getQuery() {
    return this.query;
  }

  @Nullable
  String getSubscription() {
    return this.subscription;
  }
}
