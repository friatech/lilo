package io.fria.lilo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.getMapList;
import static io.fria.lilo.JsonUtils.getName;

final class SchemaMerger {

  private SchemaMerger() {
    // Utility class
  }

  static @NotNull OperationTypeNames getOperationTypeNames(
      @NotNull final Map<String, Object> sourceSchema) {

    final var queryTypeOptional = getMap(sourceSchema, "queryType");

    if (queryTypeOptional.isEmpty()) {
      throw new IllegalArgumentException("source schema should contain a queryType section");
    }

    final Optional<String> queryTypeNameOptional = getName(queryTypeOptional.get());

    if (queryTypeNameOptional.isEmpty()) {
      throw new IllegalArgumentException("queryType section in source schema cannot be empty");
    }

    final var mutationTypeOptional = getMap(sourceSchema, "mutationType");

    String mutationTypeName = null;

    if (mutationTypeOptional.isPresent()) {
      final Optional<String> mutationTypeNameOptional = getName(mutationTypeOptional.get());

      if (mutationTypeNameOptional.isPresent()) {
        mutationTypeName = mutationTypeNameOptional.get();
      }
    }

    return new OperationTypeNames(queryTypeNameOptional.get(), mutationTypeName);
  }

  static void mergeSchema(
      @NotNull final Map<String, Object> targetSchema,
      @NotNull final Map<String, Object> sourceSchema) {

    if (targetSchema.isEmpty()) {
      targetSchema.putAll(sourceSchema);
      return;
    }

    mergeTypeName(targetSchema, sourceSchema, "queryType");
    mergeTypeName(targetSchema, sourceSchema, "mutationType");
    mergeSchemaTypes(targetSchema, sourceSchema);
    mergeSchemaDirectives(targetSchema, sourceSchema);
  }

  private static void addFields(
      @Nullable final String typeName,
      @NotNull final Map<String, Object> typeDefinition,
      @NotNull final Map<String, Map<String, Object>> targetTypeMap) {

    if (typeName == null) {
      return;
    }

    final var typeDefinitionNameOptional = getName(typeDefinition);

    if (typeDefinitionNameOptional.isEmpty()) {
      return;
    }

    final String typeDefinitionName = typeDefinitionNameOptional.get();

    if (!typeDefinitionName.equals(typeName)) {
      return;
    }

    final var targetQueryTypeDefinition = targetTypeMap.get(typeDefinitionName);
    final var fields = getMapList(targetQueryTypeDefinition, "fields").orElse(List.of());

    final var targetFields =
        fields.stream().collect(Collectors.toMap(f -> f.get("name").toString(), f -> f));

    getMapList(typeDefinition, "fields")
        .orElse(List.of())
        .forEach(
            f -> {
              final Optional<String> fieldNameOptional = getName(f);

              if (fieldNameOptional.isPresent()
                  && !targetFields.containsKey(fieldNameOptional.get())) {
                fields.add(f);
              }
            });
  }

  private static void addFields(
      @NotNull final Map<String, Object> sourceSchemaTypes,
      @NotNull final List<Map<String, Object>> targetSchemaTypes,
      @NotNull final Map<String, Map<String, Object>> targetTypeMap,
      @NotNull final OperationTypeNames operationTypeNames) {

    final var typeNameOptional = getName(sourceSchemaTypes);

    if (typeNameOptional.isEmpty()) {
      return;
    }

    final String typeName = typeNameOptional.get();

    if (!targetTypeMap.containsKey(typeName)) {
      targetSchemaTypes.add(sourceSchemaTypes);
      targetTypeMap.put(typeName, sourceSchemaTypes);
    }

    addFields(operationTypeNames.queryTypeName, sourceSchemaTypes, targetTypeMap);
    addFields(operationTypeNames.mutationTypeName, sourceSchemaTypes, targetTypeMap);
  }

  private static void mergeSchemaDirectives(
      @NotNull final Map<String, Object> targetSchema,
      @NotNull final Map<String, Object> sourceSchema) {

    final var sourceSchemaDirectivesOptional = getMapList(sourceSchema, "directives");

    if (sourceSchemaDirectivesOptional.isEmpty()) {
      return;
    }

    final var targetSchemaDirectivesOptional = getMapList(targetSchema, "directives");
    final List<Map<String, Object>> targetSchemaDirectives;

    if (targetSchemaDirectivesOptional.isEmpty()) {
      targetSchemaDirectives = new ArrayList<>();
      targetSchema.put("directives", targetSchemaDirectives);
    } else {
      targetSchemaDirectives = targetSchemaDirectivesOptional.get();
    }

    final var targetDirectiveMap =
        targetSchemaDirectives.stream()
            .collect(Collectors.toMap(d -> d.get("name").toString(), d -> d));

    sourceSchemaDirectivesOptional
        .get()
        .forEach(
            sd -> {
              final Optional<String> sdOptional = getName(sd);

              if (sdOptional.isPresent() && !targetDirectiveMap.containsKey(sdOptional.get())) {
                targetSchemaDirectives.add(sd);
              }
            });
  }

  private static void mergeSchemaTypes(
      @NotNull final Map<String, Object> targetSchema,
      @NotNull final Map<String, Object> sourceSchema) {

    final var sourceSchemaTypesOptional = getMapList(sourceSchema, "types");

    if (sourceSchemaTypesOptional.isEmpty()) {
      return;
    }

    final var targetSchemaTypesOptional = getMapList(targetSchema, "types");
    final List<Map<String, Object>> targetSchemaTypes;

    if (targetSchemaTypesOptional.isEmpty()) {
      targetSchemaTypes = new ArrayList<>();
      targetSchema.put("types", targetSchemaTypes);
    } else {
      targetSchemaTypes = targetSchemaTypesOptional.get();
    }

    final var targetTypeMap =
        targetSchemaTypes.stream()
            .collect(Collectors.toMap(st -> st.get("name").toString(), st -> st));

    final OperationTypeNames operationTypeNames = getOperationTypeNames(sourceSchema);

    sourceSchemaTypesOptional
        .get()
        .forEach(st -> addFields(st, targetSchemaTypes, targetTypeMap, operationTypeNames));
  }

  private static void mergeTypeName(
      @NotNull final Map<String, Object> targetSchema,
      @NotNull final Map<String, Object> sourceSchema,
      @NotNull final String typeNameKey) {

    final var sourceSchemaQueryTypeOptional = getMap(sourceSchema, typeNameKey);

    if (sourceSchemaQueryTypeOptional.isEmpty()) {
      return;
    }

    final var sourceSchemaQueryType = sourceSchemaQueryTypeOptional.get();
    final var targetSchemaQueryTypeOptional = getMap(targetSchema, typeNameKey);

    if (targetSchemaQueryTypeOptional.isEmpty()) {
      targetSchema.put(typeNameKey, sourceSchemaQueryType);
    } else {
      final Map<String, Object> targetSchemaQueryType = targetSchemaQueryTypeOptional.get();

      if (!getName(sourceSchemaQueryType).equals(getName(targetSchemaQueryType))) {
        throw new IllegalArgumentException("type name mismatches");
      }
    }
  }

  static final class OperationTypeNames {

    private final String queryTypeName;
    private final String mutationTypeName;

    OperationTypeNames(
        @NotNull final String queryTypeName, @Nullable final String mutationTypeName) {
      this.queryTypeName = queryTypeName;
      this.mutationTypeName = mutationTypeName;
    }

    @Nullable
    String getMutationTypeName() {
      return this.mutationTypeName;
    }

    @NotNull
    String getQueryTypeName() {
      return this.queryTypeName;
    }
  }
}
