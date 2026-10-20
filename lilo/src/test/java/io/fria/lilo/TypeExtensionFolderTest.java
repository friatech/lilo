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
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TypeExtensionFolderTest {

  private static final String DIRECTIVES =
      "directive @d on OBJECT | INTERFACE | INPUT_OBJECT | ENUM | UNION | SCALAR "
          + "directive @e on OBJECT | INTERFACE | INPUT_OBJECT | ENUM | UNION | SCALAR ";

  private static TypeDefinitionRegistry parse(final String sdl) {
    return new SchemaParser().parse(sdl);
  }

  @SuppressWarnings("rawtypes")
  private static String folded(final String sdl, final String name) {

    final Map<String, TypeDefinition> types = TypeExtensionFolder.completeTypes(parse(sdl));
    Assertions.assertTrue(types.containsKey(name), () -> name + " missing in " + types.keySet());
    return AstPrinter.printAstCompact(types.get(name));
  }

  private static String onePiece(final String sdl, final String name) {

    final TypeDefinitionRegistry registry = parse(sdl);
    final TypeDefinition<?> type = registry.types().get(name);
    return AstPrinter.printAstCompact(type != null ? type : registry.scalars().get(name));
  }

  /** Folding appends the extensions' members and directives after the definition's own. */
  private static void assertFoldsTo(final String split, final String expected, final String name) {
    Assertions.assertEquals(onePiece(expected, name), folded(split, name));
  }

  @Test
  void objectExtensionAddsFieldsInterfacesAndDirectives() {

    final String interfaces = DIRECTIVES + "interface A { x: Int } interface B { y: Int } ";

    assertFoldsTo(
        interfaces + "type T implements A @d { x: Int } extend type T implements B @e { y: Int }",
        interfaces + "type T implements A & B @d @e { x: Int y: Int }",
        "T");
  }

  @Test
  void interfaceExtensionAddsFieldsInterfacesAndDirectives() {

    final String base = DIRECTIVES + "interface J { j: Int } ";

    assertFoldsTo(
        base + "interface I @d { a: Int } extend interface I implements J @e { j: Int }",
        base + "interface I implements J @d @e { a: Int j: Int }",
        "I");
  }

  @Test
  void inputExtensionAddsInputValuesAndDirectives() {

    assertFoldsTo(
        DIRECTIVES + "input F { a: Int } extend input F @d { b: Int }",
        DIRECTIVES + "input F @d { a: Int b: Int }",
        "F");
  }

  @Test
  void enumExtensionAddsValuesAndDirectives() {

    assertFoldsTo(
        DIRECTIVES + "enum C @e { RED } extend enum C @d { GREEN }",
        DIRECTIVES + "enum C @e @d { RED GREEN }",
        "C");
  }

  @Test
  void unionExtensionAddsMembersAndDirectives() {

    final String members = DIRECTIVES + "type A { a: Int } type B { b: Int } ";

    assertFoldsTo(
        members + "union U = A extend union U @d = B", members + "union U @d = A | B", "U");
  }

  @Test
  void scalarExtensionAddsDirectives() {

    final TypeDefinitionRegistry registry = parse(DIRECTIVES + "scalar S @e extend scalar S @d");

    Assertions.assertEquals(
        onePiece(DIRECTIVES + "scalar S @e @d", "S"),
        AstPrinter.printAstCompact(
            TypeExtensionFolder.withExtensions(registry.scalars().get("S"), registry)));
  }

  @Test
  void rootTypeExtensionsAreFolded() {

    final String split =
        "type Query { a: Int } extend type Query { b: Int } "
            + "type Mutation { c: Int } extend type Mutation { d: Int } "
            + "type Subscription { e: Int } extend type Subscription { f: Int }";

    Assertions.assertEquals(
        onePiece("type Query { a: Int b: Int }", "Query"), folded(split, "Query"));
    Assertions.assertEquals(
        onePiece("type Mutation { c: Int d: Int }", "Mutation"), folded(split, "Mutation"));
    Assertions.assertEquals(
        onePiece("type Subscription { e: Int f: Int }", "Subscription"),
        folded(split, "Subscription"));
  }

  @Test
  void severalExtensionsAreAppendedInSourceOrder() {

    assertFoldsTo(
        DIRECTIVES + "type T { a: Int } extend type T @e { b: Int } extend type T @d { c: Int }",
        DIRECTIVES + "type T @e @d { a: Int b: Int c: Int }",
        "T");
  }

  @Test
  void extensionWrittenBeforeItsBaseIsAppendedAfterIt() {
    assertFoldsTo("extend type T { b: Int } type T { a: Int }", "type T { a: Int b: Int }", "T");
  }

  @Test
  void typeWithoutExtensionsIsReturnedUnchanged() {

    final TypeDefinitionRegistry registry = parse("type T { a: Int } type U { b: Int }");
    final TypeDefinition<?> type = registry.getType("T").orElseThrow();

    Assertions.assertSame(type, TypeExtensionFolder.withExtensions(type, registry));
    Assertions.assertSame(type, TypeExtensionFolder.completeTypes(registry).get("T"));
  }

  @Test
  void foldingDoesNotModifyTheRegistry() {

    final TypeDefinitionRegistry registry = parse("type T { a: Int } extend type T { b: Int }");
    TypeExtensionFolder.completeTypes(registry);

    final ObjectTypeDefinition type = (ObjectTypeDefinition) registry.getType("T").orElseThrow();
    Assertions.assertEquals(1, type.getFieldDefinitions().size());
  }

  @Test
  void extensionOfATypeTheSourceDoesNotDefineIsDropped() {

    final TypeDefinitionRegistry registry =
        parse("type Query { b: Int } extend type Pair { z: Int }");

    Assertions.assertEquals(
        List.of("Query"), List.copyOf(TypeExtensionFolder.completeTypes(registry).keySet()));
  }

  @Test
  void extensionOfATypeOfAnotherSourceIsNotMergedAndFailsItsOwnSource() {

    final TypeDefinitionRegistry registry1 =
        parse("type Query { a: Pair } type Pair { x: Int y: Int }");
    final TypeDefinitionRegistry registry2 =
        parse("type Query { b: Int } extend type Pair { z: Int }");

    final TypeDefinitionRegistry combined = new TypeDefinitionRegistry();
    SchemaMerger.mergeSnapshots(
        List.of(
            new SchemaMerger.SourceSnapshot(
                "project1", false, registry1, RuntimeWiring.MOCKED_WIRING),
            new SchemaMerger.SourceSnapshot(
                "project2", false, registry2, RuntimeWiring.MOCKED_WIRING)),
        combined,
        RuntimeWiring.newRuntimeWiring());

    final ObjectTypeDefinition pair = (ObjectTypeDefinition) combined.getType("Pair").orElseThrow();
    Assertions.assertEquals(
        List.of("x", "y"),
        pair.getFieldDefinitions().stream()
            .map(FieldDefinition::getName)
            .collect(Collectors.toList()));

    // The source that wrote the extension cannot build its own schema, which is what rejects it
    final SchemaProblem problem =
        Assertions.assertThrows(
            SchemaProblem.class,
            () ->
                new SchemaGenerator().makeExecutableSchema(registry2, RuntimeWiring.MOCKED_WIRING));
    Assertions.assertTrue(
        problem.getMessage().contains("missing its base underlying type"), problem.getMessage());
  }
}
