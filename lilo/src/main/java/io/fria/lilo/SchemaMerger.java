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
import graphql.schema.FieldCoordinates;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  /**
   * Reads the loaded state of each source once. A source can be invalidated at any time, so
   * everything built from one load (the merged schema and the set of remote root fields) must use
   * the same snapshot instead of asking the sources again.
   */
  static @NotNull List<SourceSnapshot> snapshot(
      final @NotNull Collection<SchemaSource> schemaSources) {

    final List<SourceSnapshot> snapshots = new ArrayList<>();

    for (final SchemaSource schemaSource : schemaSources) {

      if (schemaSource == null) {
        continue;
      }

      // The registry is written last when a source loads and is the only field invalidate
      // clears, so reading it first and the wiring next yields a wiring of the same load or newer
      final TypeDefinitionRegistry registry = schemaSource.typeDefinitionRegistry;
      final RuntimeWiring wiring = schemaSource.runtimeWiring;

      if (registry != null && wiring != null) {
        snapshots.add(
            new SourceSnapshot(
                schemaSource.getName(),
                schemaSource instanceof RemoteSchemaSource,
                registry,
                wiring));
      }
    }

    return snapshots;
  }

  static void mergeSchemas(
      final @NotNull Collection<SchemaSource> schemaSources,
      final @NotNull TypeDefinitionRegistry combinedRegistry,
      final RuntimeWiring.@NotNull Builder runtimeWiringBuilder) {
    mergeSnapshots(snapshot(schemaSources), combinedRegistry, runtimeWiringBuilder);
  }

  static void mergeSnapshots(
      final @NotNull List<SourceSnapshot> snapshots,
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

    final DataFetcherWiring dataFetcherWiring = new DataFetcherWiring(runtimeWiringBuilder);

    for (final SourceSnapshot schemaSource : snapshots) {

      final TypeDefinitionRegistry sourceRegistry = schemaSource.getRegistry();
      final OperationTypeNames operationTypeNames = getOperationTypeNames(sourceRegistry);
      // Folded before anything is compared, so a type split into a definition and extensions
      // compares equal to the same type written in one piece
      final Map<String, TypeDefinition> sourceTypes =
          TypeExtensionFolder.completeTypes(sourceRegistry);

      queryTypeName = checkName(queryTypeName, operationTypeNames.query);
      mutationTypeName = checkName(mutationTypeName, operationTypeNames.mutation);
      subscriptionTypeName = checkName(subscriptionTypeName, operationTypeNames.subscription);

      final RuntimeWiring sourceWiring = schemaSource.getWiring();
      final Map<String, Map<String, DataFetcher>> dataFetchers = sourceWiring.getDataFetchers();

      dataFetcherWiring.mergeFieldDefinitions(
          queryTypeName, queryFieldDefinitions, sourceTypes, dataFetchers);
      dataFetcherWiring.mergeFieldDefinitions(
          mutationTypeName, mutationFieldDefinitions, sourceTypes, dataFetchers);
      dataFetcherWiring.mergeFieldDefinitions(
          subscriptionTypeName, subscriptionFieldDefinitions, sourceTypes, dataFetchers);

      // Add types other than Query and Mutation to combined registry
      final List<SDLDefinition> nonRootTypes =
          excludeTypes(sourceTypes, queryTypeName, mutationTypeName, subscriptionTypeName);
      nonRootTypes.forEach(d -> addDefinition(d, "", schemaSource, combinedRegistry, definitions));

      if (!schemaSource.isRemote()) {
        dataFetcherWiring.collectDataFetchers(schemaSource.getName(), nonRootTypes, dataFetchers);
      }

      // Add all directive definitions
      sourceRegistry
          .getDirectiveDefinitions()
          .values()
          .forEach(d -> addDefinition(d, "@", schemaSource, combinedRegistry, definitions));

      // Add all scalar definitions
      sourceRegistry.scalars().values().stream()
          .map(d -> TypeExtensionFolder.withExtensions(d, sourceRegistry))
          .forEach(d -> addDefinition(d, "", schemaSource, combinedRegistry, definitions));
    }

    dataFetcherWiring.wireDataFetchers(remoteRootFields(snapshots));

    addType(queryTypeName, queryFieldDefinitions, combinedRegistry);
    addType(mutationTypeName, mutationFieldDefinitions, combinedRegistry);
    addType(subscriptionTypeName, subscriptionFieldDefinitions, combinedRegistry);
    addSchema(queryTypeName, mutationTypeName, subscriptionTypeName, combinedRegistry);
  }

  private static void addDefinition(
      final @NotNull SDLDefinition<?> definition,
      final @NotNull String keyPrefix,
      final @NotNull SourceSnapshot schemaSource,
      final @NotNull TypeDefinitionRegistry combinedRegistry,
      final @NotNull Map<String, SourceDefinition> definitions) {

    final String name = ((NamedNode<?>) definition).getName();
    // The compact printer omits descriptions: documentation is not part of a type's structure.
    // Member order is not part of it either, so members are sorted before printing.
    final String printed = AstPrinter.printAstCompact(DefinitionNormalizer.normalize(definition));
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

  @SuppressWarnings("rawtypes")
  private static @NotNull List<SDLDefinition> excludeTypes(
      final @NotNull Map<String, TypeDefinition> sourceTypes,
      final @Nullable String queryTypeName,
      final @Nullable String mutationTypeName,
      final @Nullable String subscriptionTypeName) {

    return sourceTypes.values().stream()
        .filter(
            t ->
                !Objects.equals(t.getName(), queryTypeName)
                    && !Objects.equals(t.getName(), mutationTypeName)
                    && !Objects.equals(t.getName(), subscriptionTypeName))
        .collect(Collectors.toList());
  }

  /** Root fields served by remote sources; their payloads are keyed by result key. */
  static @NotNull Set<FieldCoordinates> remoteRootFields(
      final @NotNull List<SourceSnapshot> snapshots) {

    final Set<FieldCoordinates> remoteRootFields = new HashSet<>();

    for (final SourceSnapshot snapshot : snapshots) {
      if (!snapshot.isRemote()) {
        continue;
      }

      final TypeDefinitionRegistry registry = snapshot.getRegistry();
      final OperationTypeNames names = getOperationTypeNames(registry);

      Stream.of(names.getQuery(), names.getMutation(), names.getSubscription())
          .filter(Objects::nonNull)
          .map(registry::getTypeOrNull)
          .filter(t -> t instanceof ObjectTypeDefinition)
          .map(t -> (ObjectTypeDefinition) t)
          .forEach(
              t ->
                  t.getFieldDefinitions()
                      .forEach(
                          f ->
                              remoteRootFields.add(
                                  FieldCoordinates.coordinates(t.getName(), f.getName()))));
    }

    return remoteRootFields;
  }

  /** The loaded state of one source, read once. */
  static final class SourceSnapshot {

    private final String name;
    private final boolean remote;
    private final TypeDefinitionRegistry registry;
    private final RuntimeWiring wiring;

    SourceSnapshot(
        final @NotNull String name,
        final boolean remote,
        final @NotNull TypeDefinitionRegistry registry,
        final @NotNull RuntimeWiring wiring) {
      this.name = name;
      this.remote = remote;
      this.registry = registry;
      this.wiring = wiring;
    }

    @NotNull
    String getName() {
      return this.name;
    }

    @NotNull
    TypeDefinitionRegistry getRegistry() {
      return this.registry;
    }

    @NotNull
    RuntimeWiring getWiring() {
      return this.wiring;
    }

    boolean isRemote() {
      return this.remote;
    }
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
