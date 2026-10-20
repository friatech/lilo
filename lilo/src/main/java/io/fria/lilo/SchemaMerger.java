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
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.NamedNode;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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

    for (final SourceSnapshot schemaSource : snapshots) {

      final TypeDefinitionRegistry sourceRegistry = schemaSource.getRegistry();
      final OperationTypeNames operationTypeNames = getOperationTypeNames(sourceRegistry);
      // Folded before anything is compared, so a type split into a definition and extensions
      // compares equal to the same type written in one piece
      final Map<String, TypeDefinition> sourceTypes = completeTypes(sourceRegistry);

      queryTypeName = checkName(queryTypeName, operationTypeNames.query);
      mutationTypeName = checkName(mutationTypeName, operationTypeNames.mutation);
      subscriptionTypeName = checkName(subscriptionTypeName, operationTypeNames.subscription);

      final RuntimeWiring sourceWiring = schemaSource.getWiring();
      final Map<String, Map<String, DataFetcher>> dataFetchers = sourceWiring.getDataFetchers();

      mergeFieldDefinitions(
          queryTypeName, queryFieldDefinitions, sourceTypes, runtimeWiringBuilder, dataFetchers);
      mergeFieldDefinitions(
          mutationTypeName,
          mutationFieldDefinitions,
          sourceTypes,
          runtimeWiringBuilder,
          dataFetchers);
      mergeFieldDefinitions(
          subscriptionTypeName,
          subscriptionFieldDefinitions,
          sourceTypes,
          runtimeWiringBuilder,
          dataFetchers);

      // Add types other than Query and Mutation to combined registry
      excludeTypes(sourceTypes, queryTypeName, mutationTypeName, subscriptionTypeName)
          .forEach(d -> addDefinition(d, "", schemaSource, combinedRegistry, definitions));

      // Add all directive definitions
      sourceRegistry
          .getDirectiveDefinitions()
          .values()
          .forEach(d -> addDefinition(d, "@", schemaSource, combinedRegistry, definitions));

      // Add all scalar definitions
      sourceRegistry.scalars().values().stream()
          .map(d -> withExtensions(d, sourceRegistry))
          .forEach(d -> addDefinition(d, "", schemaSource, combinedRegistry, definitions));
    }

    addType(queryTypeName, queryFieldDefinitions, combinedRegistry);
    addType(mutationTypeName, mutationFieldDefinitions, combinedRegistry);
    addType(subscriptionTypeName, subscriptionFieldDefinitions, combinedRegistry);
    addSchema(queryTypeName, mutationTypeName, subscriptionTypeName, combinedRegistry);
  }

  /**
   * Returns the type definitions of a source with the source's own extensions ({@code extend type},
   * {@code extend interface} and so on) folded into them, keyed by type name. The combined registry
   * only receives complete types, so an extension of a type that this source does not define is not
   * merged; a {@link DefinedSchemaSource} already rejects one when it builds its own schema.
   */
  @SuppressWarnings("rawtypes")
  private static @NotNull Map<String, TypeDefinition> completeTypes(
      final @NotNull TypeDefinitionRegistry registry) {

    final Map<String, TypeDefinition> types = new LinkedHashMap<>(registry.types());
    types.replaceAll((name, type) -> withExtensions(type, registry));
    return types;
  }

  @SuppressWarnings("rawtypes")
  private static @NotNull TypeDefinition withExtensions(
      final @NotNull TypeDefinition type, final @NotNull TypeDefinitionRegistry registry) {

    final String name = type.getName();

    if (type instanceof ObjectTypeDefinition) {
      final ObjectTypeDefinition d = (ObjectTypeDefinition) type;
      final List<ObjectTypeExtensionDefinition> extensions =
          registry.objectTypeExtensions().getOrDefault(name, List.of());
      return extensions.isEmpty()
          ? d
          : d.transform(
              b ->
                  b.fieldDefinitions(
                          concat(
                              d.getFieldDefinitions(),
                              extensions,
                              ObjectTypeDefinition::getFieldDefinitions))
                      .implementz(
                          concat(
                              d.getImplements(), extensions, ObjectTypeDefinition::getImplements))
                      .directives(directives(d, extensions)));
    } else if (type instanceof InterfaceTypeDefinition) {
      final InterfaceTypeDefinition d = (InterfaceTypeDefinition) type;
      final List<InterfaceTypeExtensionDefinition> extensions =
          registry.interfaceTypeExtensions().getOrDefault(name, List.of());
      return extensions.isEmpty()
          ? d
          : d.transform(
              b ->
                  b.definitions(
                          concat(
                              d.getFieldDefinitions(),
                              extensions,
                              InterfaceTypeDefinition::getFieldDefinitions))
                      .implementz(
                          concat(
                              d.getImplements(),
                              extensions,
                              InterfaceTypeDefinition::getImplements))
                      .directives(directives(d, extensions)));
    } else if (type instanceof InputObjectTypeDefinition) {
      final InputObjectTypeDefinition d = (InputObjectTypeDefinition) type;
      final List<InputObjectTypeExtensionDefinition> extensions =
          registry.inputObjectTypeExtensions().getOrDefault(name, List.of());
      return extensions.isEmpty()
          ? d
          : d.transform(
              b ->
                  b.inputValueDefinitions(
                          concat(
                              d.getInputValueDefinitions(),
                              extensions,
                              InputObjectTypeDefinition::getInputValueDefinitions))
                      .directives(directives(d, extensions)));
    } else if (type instanceof EnumTypeDefinition) {
      final EnumTypeDefinition d = (EnumTypeDefinition) type;
      final List<EnumTypeExtensionDefinition> extensions =
          registry.enumTypeExtensions().getOrDefault(name, List.of());
      return extensions.isEmpty()
          ? d
          : d.transform(
              b ->
                  b.enumValueDefinitions(
                          concat(
                              d.getEnumValueDefinitions(),
                              extensions,
                              EnumTypeDefinition::getEnumValueDefinitions))
                      .directives(directives(d, extensions)));
    } else if (type instanceof UnionTypeDefinition) {
      final UnionTypeDefinition d = (UnionTypeDefinition) type;
      final List<UnionTypeExtensionDefinition> extensions =
          registry.unionTypeExtensions().getOrDefault(name, List.of());
      return extensions.isEmpty()
          ? d
          : d.transform(
              b ->
                  b.memberTypes(
                          concat(
                              d.getMemberTypes(), extensions, UnionTypeDefinition::getMemberTypes))
                      .directives(directives(d, extensions)));
    } else if (type instanceof ScalarTypeDefinition) {
      // A scalar extension can only add directives
      final ScalarTypeDefinition d = (ScalarTypeDefinition) type;
      final List<ScalarTypeExtensionDefinition> extensions =
          registry.scalarTypeExtensions().getOrDefault(name, List.of());
      return extensions.isEmpty() ? d : d.transform(b -> b.directives(directives(d, extensions)));
    }

    return type;
  }

  private static <D extends TypeDefinition<?>> @NotNull List<Directive> directives(
      final @NotNull D definition, final @NotNull List<? extends D> extensions) {
    return concat(definition.getDirectives(), extensions, TypeDefinition::getDirectives);
  }

  private static <D, T> @NotNull List<T> concat(
      final @NotNull List<T> members,
      final @NotNull List<? extends D> extensions,
      final @NotNull Function<D, List<T>> extensionMembers) {

    final List<T> all = new ArrayList<>(members);
    extensions.forEach(e -> all.addAll(extensionMembers.apply(e)));
    return all;
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
    final String printed = AstPrinter.printAstCompact(normalize(definition));
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

  /**
   * Returns a copy of the definition whose members (fields, arguments, input values, enum values,
   * union members, implemented interfaces and directive locations) and type-level directives are
   * sorted, so that two definitions differing only in member order print the same; a type whose
   * directives are partly applied by an extension then equals the same type written in one piece.
   * The source node is not modified.
   */
  private static @NotNull Node<?> normalize(final @NotNull SDLDefinition<?> definition) {

    if (definition instanceof ObjectTypeDefinition) {
      final ObjectTypeDefinition d = (ObjectTypeDefinition) definition;
      return d.transform(
          b ->
              b.fieldDefinitions(sortFields(d.getFieldDefinitions()))
                  .implementz(sortTypes(d.getImplements()))
                  .directives(sortDirectives(d.getDirectives())));
    } else if (definition instanceof InterfaceTypeDefinition) {
      final InterfaceTypeDefinition d = (InterfaceTypeDefinition) definition;
      return d.transform(
          b ->
              b.definitions(sortFields(d.getFieldDefinitions()))
                  .implementz(sortTypes(d.getImplements()))
                  .directives(sortDirectives(d.getDirectives())));
    } else if (definition instanceof InputObjectTypeDefinition) {
      final InputObjectTypeDefinition d = (InputObjectTypeDefinition) definition;
      return d.transform(
          b ->
              b.inputValueDefinitions(sortInputValues(d.getInputValueDefinitions()))
                  .directives(sortDirectives(d.getDirectives())));
    } else if (definition instanceof EnumTypeDefinition) {
      final EnumTypeDefinition d = (EnumTypeDefinition) definition;
      return d.transform(
          b ->
              b.enumValueDefinitions(
                      sortedBy(d.getEnumValueDefinitions(), EnumValueDefinition::getName))
                  .directives(sortDirectives(d.getDirectives())));
    } else if (definition instanceof UnionTypeDefinition) {
      final UnionTypeDefinition d = (UnionTypeDefinition) definition;
      return d.transform(
          b ->
              b.memberTypes(sortTypes(d.getMemberTypes()))
                  .directives(sortDirectives(d.getDirectives())));
    } else if (definition instanceof ScalarTypeDefinition) {
      final ScalarTypeDefinition d = (ScalarTypeDefinition) definition;
      return d.transform(b -> b.directives(sortDirectives(d.getDirectives())));
    } else if (definition instanceof DirectiveDefinition) {
      final DirectiveDefinition d = (DirectiveDefinition) definition;
      return d.transform(
          b ->
              b.inputValueDefinitions(sortInputValues(d.getInputValueDefinitions()))
                  .directiveLocations(
                      sortedBy(d.getDirectiveLocations(), DirectiveLocation::getName)));
    }

    return definition;
  }

  private static @NotNull List<FieldDefinition> sortFields(
      final @NotNull List<FieldDefinition> fields) {

    final List<FieldDefinition> normalized =
        fields.stream()
            .map(
                f ->
                    f.transform(
                        b ->
                            b.inputValueDefinitions(sortInputValues(f.getInputValueDefinitions()))))
            .collect(Collectors.toList());

    return sortedBy(normalized, FieldDefinition::getName);
  }

  private static @NotNull List<InputValueDefinition> sortInputValues(
      final @NotNull List<InputValueDefinition> inputValues) {
    return sortedBy(inputValues, InputValueDefinition::getName);
  }

  private static @NotNull List<Directive> sortDirectives(
      final @NotNull List<Directive> directives) {
    return sortedBy(directives, AstPrinter::printAstCompact);
  }

  @SuppressWarnings("rawtypes")
  private static @NotNull List<Type> sortTypes(final @NotNull List<Type> types) {
    return sortedBy(types, AstPrinter::printAstCompact);
  }

  private static <T> @NotNull List<T> sortedBy(
      final @NotNull List<T> items, final @NotNull Function<T, String> key) {

    final List<T> sorted = new ArrayList<>(items);
    sorted.sort(Comparator.comparing(key));
    return sorted;
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

  private static void mergeFieldDefinitions(
      final @Nullable String typeName,
      final @NotNull List<FieldDefinition> fieldDefinitions,
      final @NotNull Map<String, TypeDefinition> sourceTypes,
      final RuntimeWiring.@NotNull Builder runtimeWiringBuilder,
      final @NotNull Map<String, Map<String, DataFetcher>> dataFetchers) {

    if (typeName == null) {
      return;
    }

    final TypeDefinition typeDefinitionOrNull = sourceTypes.get(typeName);

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
