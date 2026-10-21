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

import graphql.language.AstPrinter;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.UnionTypeDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/** Brings definitions into a canonical form so that structurally equal definitions print alike. */
final class DefinitionNormalizer {

  // One entry per kind of definition that has members or directives to sort
  private static final List<Rule<?>> RULES =
      List.of(
          new Rule<>(
              ObjectTypeDefinition.class,
              d ->
                  d.transform(
                      b ->
                          b.fieldDefinitions(sortFields(d.getFieldDefinitions()))
                              .implementz(sortTypes(d.getImplements()))
                              .directives(sortDirectives(d.getDirectives())))),
          new Rule<>(
              InterfaceTypeDefinition.class,
              d ->
                  d.transform(
                      b ->
                          b.definitions(sortFields(d.getFieldDefinitions()))
                              .implementz(sortTypes(d.getImplements()))
                              .directives(sortDirectives(d.getDirectives())))),
          new Rule<>(
              InputObjectTypeDefinition.class,
              d ->
                  d.transform(
                      b ->
                          b.inputValueDefinitions(sortInputValues(d.getInputValueDefinitions()))
                              .directives(sortDirectives(d.getDirectives())))),
          new Rule<>(
              EnumTypeDefinition.class,
              d ->
                  d.transform(
                      b ->
                          b.enumValueDefinitions(
                                  sortedBy(
                                      d.getEnumValueDefinitions(), EnumValueDefinition::getName))
                              .directives(sortDirectives(d.getDirectives())))),
          new Rule<>(
              UnionTypeDefinition.class,
              d ->
                  d.transform(
                      b ->
                          b.memberTypes(sortTypes(d.getMemberTypes()))
                              .directives(sortDirectives(d.getDirectives())))),
          new Rule<>(
              ScalarTypeDefinition.class,
              d -> d.transform(b -> b.directives(sortDirectives(d.getDirectives())))),
          new Rule<>(
              DirectiveDefinition.class,
              d ->
                  d.transform(
                      b ->
                          b.inputValueDefinitions(sortInputValues(d.getInputValueDefinitions()))
                              .directiveLocations(
                                  sortedBy(
                                      d.getDirectiveLocations(), DirectiveLocation::getName)))));

  private DefinitionNormalizer() {
    // Utility class
  }

  /**
   * Returns a copy of the definition whose members (fields, arguments, input values, enum values,
   * union members, implemented interfaces and directive locations) and type-level directives are
   * sorted, so that two definitions differing only in member order print the same; a type whose
   * directives are partly applied by an extension then equals the same type written in one piece.
   * The source node is not modified.
   */
  static @NotNull Node<?> normalize(final @NotNull SDLDefinition<?> definition) {

    for (final Rule<?> rule : RULES) {
      if (rule.kind.isInstance(definition)) {
        return rule.normalize(definition);
      }
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

  /** How one kind of definition is normalized. */
  private static final class Rule<D extends Node<?>> {

    private final Class<D> kind;
    private final UnaryOperator<D> normalizer;

    Rule(final @NotNull Class<D> kind, final @NotNull UnaryOperator<D> normalizer) {
      this.kind = kind;
      this.normalizer = normalizer;
    }

    @NotNull
    D normalize(final @NotNull SDLDefinition<?> definition) {
      return this.normalizer.apply(this.kind.cast(definition));
    }
  }
}
