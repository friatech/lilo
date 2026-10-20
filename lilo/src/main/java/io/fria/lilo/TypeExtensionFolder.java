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

import graphql.language.Directive;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Folds the extensions in a source's own SDL ({@code extend type}, {@code extend interface} and so
 * on) into the definitions they extend, so a type split into a definition and extensions compares
 * equal to the same type written in one piece.
 */
final class TypeExtensionFolder {

  private TypeExtensionFolder() {
    // Utility class
  }

  /**
   * Returns the type definitions of a source with the source's own extensions folded into them,
   * keyed by type name. Only complete types are returned, so an extension of a type that this
   * source does not define is dropped; a {@link DefinedSchemaSource} already rejects one when it
   * builds its own schema.
   */
  @SuppressWarnings("rawtypes")
  static @NotNull Map<String, TypeDefinition> completeTypes(
      final @NotNull TypeDefinitionRegistry registry) {

    final Map<String, TypeDefinition> types = new LinkedHashMap<>(registry.types());
    types.replaceAll((name, type) -> withExtensions(type, registry));
    return types;
  }

  /** Returns the type with the registry's extensions of it folded in, or the type itself. */
  @SuppressWarnings("rawtypes")
  static @NotNull TypeDefinition withExtensions(
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
}
