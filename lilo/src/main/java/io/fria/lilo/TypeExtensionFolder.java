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
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Folds the extensions in a source's own SDL ({@code extend type}, {@code extend interface} and so
 * on) into the definitions they extend, so a type split into a definition and extensions compares
 * equal to the same type written in one piece.
 */
final class TypeExtensionFolder {

  // One entry per kind that can be extended; the extensions add members and directives
  private static final List<Folder<?, ?>> FOLDERS =
      List.of(
          new Folder<>(
              ObjectTypeDefinition.class,
              TypeDefinitionRegistry::objectTypeExtensions,
              (d, extensions) ->
                  d.transform(
                      b ->
                          b.fieldDefinitions(
                                  concat(
                                      d.getFieldDefinitions(),
                                      extensions,
                                      ObjectTypeDefinition::getFieldDefinitions))
                              .implementz(
                                  concat(
                                      d.getImplements(),
                                      extensions,
                                      ObjectTypeDefinition::getImplements))
                              .directives(directives(d, extensions)))),
          new Folder<>(
              InterfaceTypeDefinition.class,
              TypeDefinitionRegistry::interfaceTypeExtensions,
              (d, extensions) ->
                  d.transform(
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
                              .directives(directives(d, extensions)))),
          new Folder<>(
              InputObjectTypeDefinition.class,
              TypeDefinitionRegistry::inputObjectTypeExtensions,
              (d, extensions) ->
                  d.transform(
                      b ->
                          b.inputValueDefinitions(
                                  concat(
                                      d.getInputValueDefinitions(),
                                      extensions,
                                      InputObjectTypeDefinition::getInputValueDefinitions))
                              .directives(directives(d, extensions)))),
          new Folder<>(
              EnumTypeDefinition.class,
              TypeDefinitionRegistry::enumTypeExtensions,
              (d, extensions) ->
                  d.transform(
                      b ->
                          b.enumValueDefinitions(
                                  concat(
                                      d.getEnumValueDefinitions(),
                                      extensions,
                                      EnumTypeDefinition::getEnumValueDefinitions))
                              .directives(directives(d, extensions)))),
          new Folder<>(
              UnionTypeDefinition.class,
              TypeDefinitionRegistry::unionTypeExtensions,
              (d, extensions) ->
                  d.transform(
                      b ->
                          b.memberTypes(
                                  concat(
                                      d.getMemberTypes(),
                                      extensions,
                                      UnionTypeDefinition::getMemberTypes))
                              .directives(directives(d, extensions)))),
          // A scalar extension can only add directives
          new Folder<>(
              ScalarTypeDefinition.class,
              TypeDefinitionRegistry::scalarTypeExtensions,
              (d, extensions) -> d.transform(b -> b.directives(directives(d, extensions)))));

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

    for (final Folder<?, ?> folder : FOLDERS) {
      if (folder.kind.isInstance(type)) {
        return folder.fold(type, registry);
      }
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

  /** How the extensions of one kind of type are found and folded into its definition. */
  private static final class Folder<D extends TypeDefinition<?>, E extends D> {

    private final Class<D> kind;
    private final Function<TypeDefinitionRegistry, Map<String, List<E>>> extensions;
    private final BiFunction<D, List<E>, D> merge;

    Folder(
        final @NotNull Class<D> kind,
        final @NotNull Function<TypeDefinitionRegistry, Map<String, List<E>>> extensions,
        final @NotNull BiFunction<D, List<E>, D> merge) {
      this.kind = kind;
      this.extensions = extensions;
      this.merge = merge;
    }

    @NotNull
    D fold(final @NotNull TypeDefinition<?> type, final @NotNull TypeDefinitionRegistry registry) {

      final D definition = this.kind.cast(type);
      final List<E> typeExtensions =
          this.extensions.apply(registry).getOrDefault(definition.getName(), List.of());
      return typeExtensions.isEmpty() ? definition : this.merge.apply(definition, typeExtensions);
    }
  }
}
