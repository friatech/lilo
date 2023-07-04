/*
 * Copyright 2023 the original author or authors.
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

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SchemaMerger {

  private SchemaMerger() {
    // Utility class
  }

  static @NotNull OperationTypeNames getOperationTypeNames(
      final TypeDefinitionRegistry typeDefinitionRegistry) {

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

  static void mergeSchemas(
      final @NotNull Collection<SchemaSource> schemaSources,
      final @NotNull TypeDefinitionRegistry combinedRegistry,
      final RuntimeWiring.@NotNull Builder runtimeWiringBuilder) {

    String queryTypeName = null;
    String mutationTypeName = null;
    String subscriptionTypeName = null;

    final List<FieldDefinition> queryFieldDefinitions = new ArrayList<>();
    final List<FieldDefinition> mutationFieldDefinitions = new ArrayList<>();
    final List<FieldDefinition> subscriptionFieldDefinitions = new ArrayList<>();

    for (final SchemaSource schemaSource : schemaSources) {

      if (schemaSource == null || schemaSource.isSchemaNotLoaded()) {
        continue;
      }

      final TypeDefinitionRegistry sourceRegistry = schemaSource.getTypeDefinitionRegistry();
      final OperationTypeNames operationTypeNames = getOperationTypeNames(sourceRegistry);

      queryTypeName = checkName(queryTypeName, operationTypeNames.query);
      mutationTypeName = checkName(mutationTypeName, operationTypeNames.mutation);
      subscriptionTypeName = checkName(subscriptionTypeName, operationTypeNames.subscription);

      final RuntimeWiring sourceWiring = schemaSource.getRuntimeWiring();
      final Map<String, Map<String, DataFetcher>> dataFetchers = sourceWiring.getDataFetchers();

      mergeFieldDefinitions(
          queryTypeName, queryFieldDefinitions, sourceRegistry, runtimeWiringBuilder, dataFetchers);
      mergeFieldDefinitions(
          mutationTypeName,
          mutationFieldDefinitions,
          sourceRegistry,
          runtimeWiringBuilder,
          dataFetchers);
      mergeFieldDefinitions(
          subscriptionTypeName,
          subscriptionFieldDefinitions,
          sourceRegistry,
          runtimeWiringBuilder,
          dataFetchers);

      // Add types other than Query and Mutation to combined registry
      excludeTypes(sourceRegistry, queryTypeName, mutationTypeName, subscriptionTypeName)
          .forEach(combinedRegistry::add);

      // Add all directive definitions
      sourceRegistry.getDirectiveDefinitions().values().forEach(combinedRegistry::add);

      // Add all scalar definitions
      sourceRegistry.scalars().values().forEach(combinedRegistry::add);
    }

    addType(queryTypeName, queryFieldDefinitions, combinedRegistry);
    addType(mutationTypeName, mutationFieldDefinitions, combinedRegistry);
    addType(subscriptionTypeName, subscriptionFieldDefinitions, combinedRegistry);
    addSchema(queryTypeName, mutationTypeName, subscriptionTypeName, combinedRegistry);
  }

  private static void addSchema(
      final @Nullable String queryTypeName,
      final @Nullable String mutationTypeName,
      final @Nullable String subscriptionTypeName,
      final @NotNull TypeDefinitionRegistry combinedRegistry) {

    final SchemaDefinition.Builder builder = SchemaDefinition.newSchemaDefinition();

    if (queryTypeName != null && combinedRegistry.getType(queryTypeName).isPresent()) {
      builder.operationTypeDefinition(
          OperationTypeDefinition.newOperationTypeDefinition()
              .name("query")
              .typeName(TypeName.newTypeName(queryTypeName).build())
              .build());
    }

    if (mutationTypeName != null && combinedRegistry.getType(mutationTypeName).isPresent()) {
      builder.operationTypeDefinition(
          OperationTypeDefinition.newOperationTypeDefinition()
              .name("mutation")
              .typeName(TypeName.newTypeName(mutationTypeName).build())
              .build());
    }

    if (subscriptionTypeName != null
        && combinedRegistry.getType(subscriptionTypeName).isPresent()) {
      builder.operationTypeDefinition(
          OperationTypeDefinition.newOperationTypeDefinition()
              .name("subscription")
              .typeName(TypeName.newTypeName(subscriptionTypeName).build())
              .build());
    }

    combinedRegistry.add(builder.build());
  }

  private static void addType(
      final @Nullable String typeName,
      final @NotNull List<FieldDefinition> fieldDefinitions,
      final @NotNull TypeDefinitionRegistry combinedRegistry) {

    if (typeName == null || fieldDefinitions.isEmpty()) {
      return;
    }

    combinedRegistry.add(
        ObjectTypeDefinition.newObjectTypeDefinition()
            .name(typeName)
            .fieldDefinitions(fieldDefinitions)
            .build());
  }

  private static @Nullable String checkName(
      final @Nullable String globalTypeName, final @Nullable String sourceTypeName) {

    if (sourceTypeName != null) {
      if (globalTypeName != null && !globalTypeName.equals(sourceTypeName)) {
        throw new IllegalArgumentException("type names don't match between sources");
      }

      return sourceTypeName;
    }

    return globalTypeName;
  }

  private static @NotNull List<SDLDefinition> excludeTypes(
      final @NotNull TypeDefinitionRegistry sourceRegistry,
      final @Nullable String queryTypeName,
      final @Nullable String mutationTypeName,
      final @Nullable String subscriptionTypeName) {

    return sourceRegistry.types().values().stream()
        .filter(
            t ->
                !t.getName().equals(queryTypeName)
                    && !t.getName().equals(mutationTypeName)
                    && !t.getName().equals(subscriptionTypeName))
        .collect(Collectors.toList());
  }

  private static void mergeFieldDefinitions(
      final @Nullable String typeName,
      final @NotNull List<FieldDefinition> fieldDefinitions,
      final @NotNull TypeDefinitionRegistry sourceRegistry,
      final RuntimeWiring.@NotNull Builder runtimeWiringBuilder,
      final @NotNull Map<String, Map<String, DataFetcher>> dataFetchers) {

    if (typeName == null) {
      return;
    }

    final Optional<TypeDefinition> typeOptional = sourceRegistry.getType(typeName);

    if (typeOptional.isEmpty()) {
      return;
    }

    final ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) typeOptional.get();
    fieldDefinitions.addAll(typeDefinition.getFieldDefinitions());
    runtimeWiringBuilder.type(
        TypeRuntimeWiring.newTypeWiring(typeName).dataFetchers(dataFetchers.get(typeName)).build());
  }

  static final class OperationTypeNames {

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

    @NotNull
    String getQuery() {
      return this.query;
    }

    @Nullable
    String getMutation() {
      return this.mutation;
    }

    @Nullable
    String getSubscription() {
      return this.subscription;
    }
  }
}
