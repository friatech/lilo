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

import graphql.GraphQLError;
import graphql.language.AstPrinter;
import graphql.language.FieldDefinition;
import graphql.language.NamedNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    // Keyed by definition name; remembers the first source that added it and its printed form
    final Map<String, SourceDefinition> definitions = new HashMap<>();

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
          .forEach(d -> addDefinition(d, "", schemaSource, combinedRegistry, definitions));

      // Add all directive definitions
      sourceRegistry
          .getDirectiveDefinitions()
          .values()
          .forEach(d -> addDefinition(d, "@", schemaSource, combinedRegistry, definitions));

      // Add all scalar definitions
      sourceRegistry
          .scalars()
          .values()
          .forEach(d -> addDefinition(d, "", schemaSource, combinedRegistry, definitions));
    }

    addType(queryTypeName, queryFieldDefinitions, combinedRegistry);
    addType(mutationTypeName, mutationFieldDefinitions, combinedRegistry);
    addType(subscriptionTypeName, subscriptionFieldDefinitions, combinedRegistry);
    addSchema(queryTypeName, mutationTypeName, subscriptionTypeName, combinedRegistry);
  }

  private static void addDefinition(
      final @NotNull SDLDefinition<?> definition,
      final @NotNull String keyPrefix,
      final @NotNull SchemaSource schemaSource,
      final @NotNull TypeDefinitionRegistry combinedRegistry,
      final @NotNull Map<String, SourceDefinition> definitions) {

    final String name = ((NamedNode<?>) definition).getName();
    // The compact printer omits descriptions: documentation is not part of a type's structure
    final String printed = AstPrinter.printAstCompact(definition);
    final SourceDefinition existing = definitions.get(keyPrefix + name);

    if (existing != null) {
      if (!existing.printed.equals(printed)) {
        throw new IllegalArgumentException(
            String.format(
                "Type %s is defined differently by sources %s and %s",
                name, existing.sourceName, schemaSource.getName()));
      }

      return;
    }

    definitions.put(keyPrefix + name, new SourceDefinition(schemaSource.getName(), printed));

    final Optional<GraphQLError> error = combinedRegistry.add(definition);

    if (error.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "Type %s from source %s could not be added: %s",
              name, schemaSource.getName(), error.get().getMessage()));
    }
  }

  private static void addOperationType(
      final @Nullable String operationTypeName,
      final @NotNull TypeDefinitionRegistry combinedRegistry,
      final SchemaDefinition.Builder builder,
      final String name) {

    if (operationTypeName != null && combinedRegistry.getTypeOrNull(operationTypeName) != null) {
      builder.operationTypeDefinition(
          OperationTypeDefinition.newOperationTypeDefinition()
              .name(name)
              .typeName(TypeName.newTypeName(operationTypeName).build())
              .build());
    }
  }

  private static void addSchema(
      final @Nullable String queryTypeName,
      final @Nullable String mutationTypeName,
      final @Nullable String subscriptionTypeName,
      final @NotNull TypeDefinitionRegistry combinedRegistry) {

    final SchemaDefinition.Builder builder = SchemaDefinition.newSchemaDefinition();

    addOperationType(queryTypeName, combinedRegistry, builder, "query");
    addOperationType(mutationTypeName, combinedRegistry, builder, "mutation");
    addOperationType(subscriptionTypeName, combinedRegistry, builder, "subscription");

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
                !Objects.equals(t.getName(), queryTypeName)
                    && !Objects.equals(t.getName(), mutationTypeName)
                    && !Objects.equals(t.getName(), subscriptionTypeName))
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

    final TypeDefinition typeDefinitionOrNull = sourceRegistry.getTypeOrNull(typeName);

    if (typeDefinitionOrNull == null) {
      return;
    }

    final ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) typeDefinitionOrNull;
    fieldDefinitions.addAll(typeDefinition.getFieldDefinitions());

    // Only wire the data fetchers of fields this source defines. A source wiring may contain
    // fetchers for fields served by other sources, and graphql-java's strict wiring mode rejects
    // registering the same field twice.
    final Map<String, DataFetcher> typeDataFetchers = dataFetchers.getOrDefault(typeName, Map.of());
    final Map<String, DataFetcher> ownDataFetchers = new HashMap<>();

    for (final FieldDefinition fieldDefinition : typeDefinition.getFieldDefinitions()) {
      final DataFetcher dataFetcher = typeDataFetchers.get(fieldDefinition.getName());

      if (dataFetcher != null) {
        ownDataFetchers.put(fieldDefinition.getName(), dataFetcher);
      }
    }

    runtimeWiringBuilder.type(
        TypeRuntimeWiring.newTypeWiring(typeName).dataFetchers(ownDataFetchers).build());
  }

  private static final class SourceDefinition {

    private final String sourceName;
    private final String printed;

    SourceDefinition(final @NotNull String sourceName, final @NotNull String printed) {
      this.sourceName = sourceName;
      this.printed = printed;
    }
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
}
