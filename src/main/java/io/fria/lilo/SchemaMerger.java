package io.fria.lilo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static io.fria.lilo.JsonUtils.getList;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.getName;

public final class SchemaMerger {

  private SchemaMerger() {
    // Utility class
  }

  static void mergeSchema(
      final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

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
      final String typeName,
      final Map<String, Object> typeDefinition,
      final Map<String, Map<String, Object>> targetTypeMap) {

    final String typeDefinitionName = getName(typeDefinition);

    if (!typeDefinitionName.equals(typeName)) {
      return;
    }

    final var targetQueryTypeDefinition = targetTypeMap.get(typeDefinitionName);
    final var fields = getList(targetQueryTypeDefinition, "fields");
    final var targetFields =
        fields.stream().collect(Collectors.toMap(f -> f.get("name").toString(), f -> f));

    getList(typeDefinition, "fields")
        .forEach(
            f -> {
              if (!targetFields.containsKey(getName(f))) {
                fields.add(f);
              }
            });
  }

  private static void mergeSchemaDirectives(
      final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

    final List<Map<String, Object>> sourceSchemaDirectives = getList(sourceSchema, "directives");

    if (sourceSchemaDirectives == null) {
      return;
    }

    List<Map<String, Object>> targetSchemaDirectives = getList(targetSchema, "directives");

    if (targetSchemaDirectives == null) {
      targetSchemaDirectives = new ArrayList<>();
      targetSchema.put("directives", targetSchemaDirectives);
    }

    final var targetDirectiveMap =
        targetSchemaDirectives.stream()
            .collect(Collectors.toMap(d -> d.get("name").toString(), d -> d));

    final List<Map<String, Object>> finalTargetSchemaDirectives = targetSchemaDirectives;

    sourceSchemaDirectives.forEach(
        sd -> {
          if (!targetDirectiveMap.containsKey(getName(sd))) {
            finalTargetSchemaDirectives.add(sd);
          }
        });
  }

  private static void mergeSchemaTypes(
      final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

    final List<Map<String, Object>> sourceSchemaTypes = getList(sourceSchema, "types");

    if (sourceSchemaTypes == null) {
      return;
    }

    List<Map<String, Object>> targetSchemaTypes = getList(targetSchema, "types");

    if (targetSchemaTypes == null) {
      targetSchemaTypes = new ArrayList<>();
      targetSchema.put("types", targetSchemaTypes);
    }

    final var targetTypeMap =
        targetSchemaTypes.stream()
            .collect(Collectors.toMap(st -> st.get("name").toString(), st -> st));
    final var finalTargetSchemaTypes = targetSchemaTypes;
    final var queryType = getMap(sourceSchema, "queryType");
    final var queryTypeName = queryType == null ? null : getName(queryType);
    final var mutationType = getMap(sourceSchema, "mutationType");
    final var mutationTypeName = mutationType == null ? null : getName(mutationType);

    sourceSchemaTypes.forEach(
        st -> {
          final String typeName = getName(st);

          if (!targetTypeMap.containsKey(typeName)) {
            finalTargetSchemaTypes.add(st);
            targetTypeMap.put(typeName, st);
          }

          addFields(queryTypeName, st, targetTypeMap);
          addFields(mutationTypeName, st, targetTypeMap);
        });
  }

  private static void mergeTypeName(
      final Map<String, Object> targetSchema,
      final Map<String, Object> sourceSchema,
      final String typeNameKey) {

    final Map<String, Object> sourceSchemaQueryType = getMap(sourceSchema, typeNameKey);

    if (sourceSchemaQueryType == null) {
      return;
    }

    final Map<String, Object> targetSchemaQueryType = getMap(targetSchema, typeNameKey);

    if (targetSchemaQueryType == null) {
      targetSchema.put(typeNameKey, sourceSchemaQueryType);
    } else if (!getName(sourceSchemaQueryType).equals(getName(targetSchemaQueryType))) {
      throw new IllegalArgumentException("type name mismatches");
    }
  }
}
