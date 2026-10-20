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

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires the data fetchers of the sources into the combined runtime wiring during one merge: the
 * root fields of every source as each source is merged, and the non-root fields of local sources
 * once all sources are merged.
 */
final class DataFetcherWiring {

  // Logged under the merger's name, where the merge is configured and where the warning used to be
  private static final Logger LOG = LoggerFactory.getLogger(SchemaMerger.class);

  private final RuntimeWiring.Builder runtimeWiringBuilder;

  // Non-root data fetchers of local sources, keyed by type, field and source name
  @SuppressWarnings("rawtypes")
  private final Map<String, Map<String, Map<String, DataFetcher>>> typeDataFetchers =
      new LinkedHashMap<>();

  DataFetcherWiring(final RuntimeWiring.@NotNull Builder runtimeWiringBuilder) {
    this.runtimeWiringBuilder = runtimeWiringBuilder;
  }

  /**
   * Adds the fields a source defines on a root type to the combined root fields and wires their
   * data fetchers.
   */
  @SuppressWarnings("rawtypes")
  void mergeFieldDefinitions(
      final @Nullable String typeName,
      final @NotNull List<FieldDefinition> fieldDefinitions,
      final @NotNull Map<String, TypeDefinition> sourceTypes,
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

    this.runtimeWiringBuilder.type(
        TypeRuntimeWiring.newTypeWiring(typeName).dataFetchers(ownDataFetchers).build());
  }

  /**
   * Collects the data fetchers a local source supplies for the fields of the non-root object and
   * interface types it defines, so they run on the gateway as they do in the source's own schema.
   * As for root fields, only fields this source defines are taken.
   */
  @SuppressWarnings("rawtypes")
  void collectDataFetchers(
      final @NotNull String sourceName,
      final @NotNull List<SDLDefinition> nonRootTypes,
      final @NotNull Map<String, Map<String, DataFetcher>> sourceDataFetchers) {

    for (final SDLDefinition definition : nonRootTypes) {

      final List<FieldDefinition> fields;

      if (definition instanceof ObjectTypeDefinition) {
        fields = ((ObjectTypeDefinition) definition).getFieldDefinitions();
      } else if (definition instanceof InterfaceTypeDefinition) {
        fields = ((InterfaceTypeDefinition) definition).getFieldDefinitions();
      } else {
        continue;
      }

      final String typeName = ((TypeDefinition<?>) definition).getName();
      final Map<String, DataFetcher> fetchers = sourceDataFetchers.getOrDefault(typeName, Map.of());

      for (final FieldDefinition field : fields) {
        final DataFetcher dataFetcher = fetchers.get(field.getName());

        if (dataFetcher != null) {
          this.typeDataFetchers
              .computeIfAbsent(typeName, k -> new LinkedHashMap<>())
              .computeIfAbsent(field.getName(), k -> new TreeMap<>())
              .put(sourceName, dataFetcher);
        }
      }
    }
  }

  /**
   * Wires one data fetcher per collected field. The gateway has one copy of each type, so the
   * fetcher runs below every root field returning the type, and each is wrapped so that below a
   * remote root field the remote payload is still read by result key. When several sources supply
   * different fetcher instances for the same field, the one of the first source by name is used and
   * a warning names the others; the same instance supplied twice is registered once.
   */
  @SuppressWarnings("rawtypes")
  void wireDataFetchers(final @NotNull Set<FieldCoordinates> remoteRootFields) {

    final ResultKeyDataFetcherFactory dataFetcherFactory =
        new ResultKeyDataFetcherFactory(remoteRootFields);

    this.typeDataFetchers.forEach(
        (typeName, fieldDataFetchers) -> {
          final Map<String, DataFetcher> fetchers = new LinkedHashMap<>();

          fieldDataFetchers.forEach(
              (fieldName, sourceFetchers) -> {
                // Sorted by source name, so the choice does not depend on the load order
                final Map.Entry<String, DataFetcher> first =
                    sourceFetchers.entrySet().iterator().next();

                if (sourceFetchers.values().stream().anyMatch(f -> f != first.getValue())) {
                  LOG.warn(
                      "Field {}.{} has different data fetchers in sources {}; using the one of {}",
                      typeName,
                      fieldName,
                      sourceFetchers.keySet(),
                      first.getKey());
                }

                fetchers.put(fieldName, dataFetcherFactory.localDataFetcher(first.getValue()));
              });

          this.runtimeWiringBuilder.type(
              TypeRuntimeWiring.newTypeWiring(typeName).dataFetchers(fetchers).build());
        });
  }
}
