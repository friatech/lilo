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
import graphql.language.SDLDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefinitionNormalizerTest {

  private static final String DIRECTIVES =
      "directive @a on OBJECT | INTERFACE | INPUT_OBJECT | ENUM | UNION | SCALAR "
          + "directive @b on OBJECT | INTERFACE | INPUT_OBJECT | ENUM | UNION | SCALAR "
          + "directive @tag(name: String) repeatable on SCALAR ";

  private static SDLDefinition<?> definition(final String sdl, final String name) {

    final TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
    final TypeDefinition<?> type = registry.types().get(name);

    if (type != null) {
      return type;
    }

    if (registry.scalars().containsKey(name)) {
      return registry.scalars().get(name);
    }

    return registry.getDirectiveDefinition(name).orElseThrow();
  }

  private static String normalized(final String sdl, final String name) {
    return AstPrinter.printAstCompact(DefinitionNormalizer.normalize(definition(sdl, name)));
  }

  /** The unsorted definition normalizes to exactly the sorted one as written. */
  private static void assertNormalizesTo(
      final String unsorted, final String sorted, final String name) {

    Assertions.assertEquals(
        AstPrinter.printAstCompact(definition(sorted, name)), normalized(unsorted, name));
    Assertions.assertEquals(normalized(sorted, name), normalized(unsorted, name));
  }

  @Test
  void objectFieldsArgumentsInterfacesAndDirectivesAreSorted() {

    final String interfaces = DIRECTIVES + "interface I { x: Int } interface J { y: Int } ";

    assertNormalizesTo(
        interfaces + "type T implements J & I @b @a { y(q: Int p: Int): Int x: Int }",
        interfaces + "type T implements I & J @a @b { x: Int y(p: Int q: Int): Int }",
        "T");
  }

  @Test
  void interfaceFieldsArgumentsInterfacesAndDirectivesAreSorted() {

    final String interfaces = DIRECTIVES + "interface I { x: Int } interface J { y: Int } ";

    assertNormalizesTo(
        interfaces + "interface K implements J & I @b @a { y: Int x(q: Int p: Int): Int }",
        interfaces + "interface K implements I & J @a @b { x(p: Int q: Int): Int y: Int }",
        "K");
  }

  @Test
  void inputValuesAndDirectivesAreSorted() {

    assertNormalizesTo(
        DIRECTIVES + "input F @b @a { b: Int a: Int }",
        DIRECTIVES + "input F @a @b { a: Int b: Int }",
        "F");
  }

  @Test
  void enumValuesAndDirectivesAreSorted() {

    assertNormalizesTo(
        DIRECTIVES + "enum C @b @a { RED GREEN BLUE }",
        DIRECTIVES + "enum C @a @b { BLUE GREEN RED }",
        "C");
  }

  @Test
  void unionMembersAndDirectivesAreSorted() {

    final String members = DIRECTIVES + "type A { a: Int } type B { b: Int } ";

    assertNormalizesTo(members + "union U @b @a = B | A", members + "union U @a @b = A | B", "U");
  }

  @Test
  void scalarDirectivesAreSortedByTheirPrintedFormIncludingArguments() {

    assertNormalizesTo(
        DIRECTIVES + "scalar S @tag(name: \"y\") @b @tag(name: \"x\") @a",
        DIRECTIVES + "scalar S @a @b @tag(name: \"x\") @tag(name: \"y\")",
        "S");
  }

  @Test
  void directiveDefinitionArgumentsAndLocationsAreSorted() {

    assertNormalizesTo(
        "directive @d(b: Int a: Int) on UNION | FIELD_DEFINITION | OBJECT",
        "directive @d(a: Int b: Int) on FIELD_DEFINITION | OBJECT | UNION",
        "d");
  }

  @Test
  void directiveOrderSplitByAnExtensionEqualsTheOnePieceType() {

    final String split = DIRECTIVES + "type P @b { x: Int } extend type P @a";
    final TypeDefinitionRegistry registry = new SchemaParser().parse(split);
    final SDLDefinition<?> folded =
        TypeExtensionFolder.withExtensions(registry.getType("P").orElseThrow(), registry);

    Assertions.assertEquals(
        normalized(DIRECTIVES + "type P @a @b { x: Int }", "P"),
        AstPrinter.printAstCompact(DefinitionNormalizer.normalize(folded)));
  }

  @Test
  void differentMembersStillPrintDifferently() {

    Assertions.assertNotEquals(
        normalized("type T { a: Int b: Int }", "T"), normalized("type T { a: Int c: Int }", "T"));
    Assertions.assertNotEquals(
        normalized(DIRECTIVES + "type T @a { a: Int }", "T"),
        normalized(DIRECTIVES + "type T @b { a: Int }", "T"));
  }

  @Test
  void theSourceDefinitionIsNotModified() {

    final SDLDefinition<?> definition = definition("type T { b: Int a: Int }", "T");
    final String before = AstPrinter.printAstCompact(definition);

    DefinitionNormalizer.normalize(definition);

    Assertions.assertEquals(before, AstPrinter.printAstCompact(definition));
  }
}
