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
      return new OperationTypeNames("Query", "Mutation");
    }

    final SchemaDefinition schemaDefinition = schemaDefinitionOptional.get();

    final Map<String, String> names =
        schemaDefinition.getOperationTypeDefinitions().stream()
            .collect(
                Collectors.toMap(OperationTypeDefinition::getName, d -> d.getTypeName().getName()));

    return new OperationTypeNames(names.get("query"), names.get("mutation"));
  }

  static void mergeSchemas(
      final @NotNull Collection<SchemaSource> schemaSources,
      final @NotNull TypeDefinitionRegistry combinedRegistry,
      final RuntimeWiring.@NotNull Builder runtimeWiringBuilder) {

    String queryTypeName = null;
    String mutationTypeName = null;
    final List<FieldDefinition> queryFieldDefinitions = new ArrayList<>();
    final List<FieldDefinition> mutationFieldDefinitions = new ArrayList<>();

    for (final SchemaSource schemaSource : schemaSources) {

      if (schemaSource == null || schemaSource.isSchemaNotLoaded()) {
        continue;
      }

      final TypeDefinitionRegistry sourceRegistry = schemaSource.getTypeDefinitionRegistry();
      final OperationTypeNames operationTypeNames = getOperationTypeNames(sourceRegistry);

      queryTypeName = checkName(queryTypeName, operationTypeNames.query);
      mutationTypeName = checkName(mutationTypeName, operationTypeNames.mutation);

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

      // Add types other than Query and Mutation to combined registry
      excludeTypes(sourceRegistry, queryTypeName, mutationTypeName).forEach(combinedRegistry::add);

      // Add all directive definitions
      sourceRegistry.getDirectiveDefinitions().values().forEach(combinedRegistry::add);

      // Add all scalar definitions
      sourceRegistry.scalars().values().forEach(combinedRegistry::add);
    }

    addType(queryTypeName, queryFieldDefinitions, combinedRegistry);
    addType(mutationTypeName, mutationFieldDefinitions, combinedRegistry);
    addSchema(queryTypeName, mutationTypeName, combinedRegistry);
  }

  private static void addSchema(
      final @Nullable String queryTypeName,
      final @Nullable String mutationTypeName,
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
      final @Nullable String mutationTypeName) {

    return sourceRegistry.types().values().stream()
        .filter(t -> !t.getName().equals(queryTypeName) && !t.getName().equals(mutationTypeName))
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

    OperationTypeNames(final @NotNull String query, final @Nullable String mutation) {
      this.query = query;
      this.mutation = mutation;
    }

    @Nullable
    String getMutation() {
      return this.mutation;
    }

    @NotNull
    String getQuery() {
      return this.query;
    }
  }
}
