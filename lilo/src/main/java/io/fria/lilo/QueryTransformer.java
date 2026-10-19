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

import static io.fria.lilo.JsonUtils.toStr;

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

final class QueryTransformer {

  private static final String TYPENAME = "__typename";

  private QueryTransformer() {
    // Utility class
  }

  static @NotNull GraphQLQuery extractQuery(final @NotNull DataFetchingEnvironment environment) {

    final var queryBuilder = GraphQLQuery.builder();
    transformDocument(environment, queryBuilder);

    return queryBuilder.build();
  }

  private static void findUsedItems(
      final @NotNull Node<?> node,
      final @NotNull Set<String> usedReferenceNames,
      final @NotNull Set<String> usedFragmentNames) {

    if (node instanceof FragmentSpread) {
      usedFragmentNames.add(((FragmentSpread) node).getName());
      findUsedItemsInChildren(node, usedReferenceNames, usedFragmentNames);
    } else if (node instanceof VariableReference) {
      usedReferenceNames.add(((VariableReference) node).getName());
    } else {
      findUsedItemsInChildren(node, usedReferenceNames, usedFragmentNames);
    }
  }

  private static void findUsedItemsInChildren(
      final @NotNull Node<?> node,
      final @NotNull Set<String> usedReferenceNames,
      final @NotNull Set<String> usedFragmentNames) {

    node.getChildren().forEach(n -> findUsedItems(n, usedReferenceNames, usedFragmentNames));
  }

  // The gateway resolves interface and union values from the __typename returned by the source,
  // so every selection set on an abstract type is made to select it. The walk is driven by the
  // gateway schema; selections it cannot type are left untouched.
  private static @NotNull Field injectTypename(
      final @NotNull GraphQLSchema schema,
      final @NotNull Field field,
      final @NotNull GraphQLCompositeType parentType) {

    final SelectionSet selectionSet = field.getSelectionSet();

    // Only object and interface types declare fields; a union selects nothing but __typename
    if (selectionSet == null || !(parentType instanceof GraphQLFieldsContainer)) {
      return field;
    }

    final GraphQLFieldDefinition fieldDefinition =
        ((GraphQLFieldsContainer) parentType).getFieldDefinition(field.getName());

    if (fieldDefinition == null) {
      return field;
    }

    final GraphQLType fieldType = GraphQLTypeUtil.unwrapAll(fieldDefinition.getType());

    if (!(fieldType instanceof GraphQLCompositeType)) {
      return field;
    }

    final SelectionSet newSelectionSet =
        injectTypename(schema, selectionSet, (GraphQLCompositeType) fieldType);

    return field.transform(builder -> builder.selectionSet(newSelectionSet));
  }

  private static @NotNull FragmentDefinition injectTypename(
      final @NotNull GraphQLSchema schema, final @NotNull FragmentDefinition fragment) {

    final SelectionSet selectionSet = fragment.getSelectionSet();
    final GraphQLType type = schema.getType(fragment.getTypeCondition().getName());

    if (selectionSet == null || !(type instanceof GraphQLCompositeType)) {
      return fragment;
    }

    final SelectionSet newSelectionSet =
        injectTypename(schema, selectionSet, (GraphQLCompositeType) type);

    return fragment.transform(builder -> builder.selectionSet(newSelectionSet));
  }

  private static @NotNull InlineFragment injectTypename(
      final @NotNull GraphQLSchema schema,
      final @NotNull InlineFragment fragment,
      final @NotNull GraphQLCompositeType parentType) {

    final SelectionSet selectionSet = fragment.getSelectionSet();
    final TypeName typeCondition = fragment.getTypeCondition();
    final GraphQLType type =
        typeCondition == null ? parentType : schema.getType(typeCondition.getName());

    if (selectionSet == null || !(type instanceof GraphQLCompositeType)) {
      return fragment;
    }

    final SelectionSet newSelectionSet =
        injectTypename(schema, selectionSet, (GraphQLCompositeType) type);

    return fragment.transform(builder -> builder.selectionSet(newSelectionSet));
  }

  private static @NotNull SelectionSet injectTypename(
      final @NotNull GraphQLSchema schema,
      final @NotNull SelectionSet selectionSet,
      final @NotNull GraphQLCompositeType parentType) {

    final List<Selection> newSelections = new ArrayList<>();
    boolean typenameSelected = false;

    for (final Selection<?> selection : selectionSet.getSelections()) {
      if (selection instanceof Field) {
        final Field field = (Field) selection;
        // An aliased __typename lands under another result key, so it does not count
        typenameSelected |= TYPENAME.equals(field.getName()) && field.getAlias() == null;
        newSelections.add(injectTypename(schema, field, parentType));
      } else if (selection instanceof InlineFragment) {
        newSelections.add(injectTypename(schema, (InlineFragment) selection, parentType));
      } else {
        // Fragment spreads are covered when their fragment definition is transformed
        newSelections.add(selection);
      }
    }

    final boolean abstractType =
        parentType instanceof GraphQLInterfaceType || parentType instanceof GraphQLUnionType;

    if (abstractType && !typenameSelected) {
      newSelections.add(new Field(TYPENAME));
    }

    return selectionSet.transform(builder -> builder.selections(newSelections));
  }

  private static @NotNull List<Definition> transformDefinitions(
      final @NotNull DataFetchingEnvironment environment,
      final @NotNull List<Definition> originalDefinitions,
      final @NotNull GraphQLRequest.GraphQLRequestBuilder requestBuilder,
      final @NotNull GraphQLQuery.GraphQLQueryBuilder queryBuilder) {

    final var operationDefinitionOptional =
        originalDefinitions.stream().filter(d -> d instanceof OperationDefinition).findFirst();

    if (operationDefinitionOptional.isEmpty()) {
      throw new IllegalArgumentException("GraphQL query should contain either query or mutation");
    }

    final var operationDefinition = (OperationDefinition) operationDefinitionOptional.get();

    queryBuilder.operationType(operationDefinition.getOperation());
    requestBuilder.operationName(operationDefinition.getName());

    final Field queryNode = environment.getField();
    final Set<String> usedReferenceNames = new HashSet<>();
    final Set<String> usedFragmentNames = new HashSet<>();

    findUsedItems(queryNode, usedReferenceNames, usedFragmentNames);

    // scan for used reference and variables recursively in used fragments
    final List<FragmentDefinition> usedRootFragmentDefinitions =
        originalDefinitions.stream()
            .filter(d -> d instanceof FragmentDefinition)
            .map(d -> (FragmentDefinition) d)
            .filter(fd -> usedFragmentNames.contains(fd.getName()))
            .collect(Collectors.toList());

    usedRootFragmentDefinitions.forEach(
        fd -> findUsedItemsInChildren(fd, usedReferenceNames, usedFragmentNames));

    final List<Definition> newDefinitions = new ArrayList<>();
    newDefinitions.add(
        transformOperationDefinition(
            environment, operationDefinition, usedReferenceNames, queryNode, queryBuilder));
    newDefinitions.addAll(
        transformFragmentDefinitions(
            environment.getGraphQLSchema(), originalDefinitions, usedFragmentNames));

    final Map<String, Object> filteredVariables = new HashMap<>();

    environment.getVariables().entrySet().stream()
        .filter(e -> usedReferenceNames.contains(e.getKey()))
        .forEach(e -> filteredVariables.put(e.getKey(), e.getValue()));

    requestBuilder.variables(filteredVariables);
    queryBuilder.variables(filteredVariables);

    return newDefinitions;
  }

  private static @NotNull Document transformDocument(
      final @NotNull DataFetchingEnvironment environment,
      final @NotNull GraphQLQuery.GraphQLQueryBuilder queryBuilder) {

    final var originalDocument = environment.getDocument();
    final var definitions = originalDocument.getDefinitions();

    if (definitions.isEmpty()) {
      throw new IllegalArgumentException("query is not in appropriate format");
    }

    final var requestBuilder = GraphQLRequest.builder();
    final var newDefinitions =
        transformDefinitions(environment, definitions, requestBuilder, queryBuilder);
    final var newDocument =
        originalDocument.transform(builder -> builder.definitions(newDefinitions));
    final var queryText = AstPrinter.printAst(newDocument);
    final var request = toStr(requestBuilder.query(queryText).build());

    queryBuilder.query(request).arguments(environment.getArguments());

    return newDocument;
  }

  private static @NotNull List<Definition<?>> transformFragmentDefinitions(
      final @NotNull GraphQLSchema schema,
      final @NotNull List<Definition> originalDefinitions,
      final @NotNull Set<String> usedFragmentNames) {

    return originalDefinitions.stream()
        .filter(d -> d instanceof FragmentDefinition)
        .map(d -> (FragmentDefinition) d)
        .filter(fd -> usedFragmentNames.contains(fd.getName()))
        .map(fd -> injectTypename(schema, fd))
        .collect(Collectors.toList());
  }

  private static @NotNull Definition<?> transformOperationDefinition(
      final @NotNull DataFetchingEnvironment environment,
      final @NotNull OperationDefinition operationDefinition,
      final @NotNull Set<String> usedReferenceNames,
      final @NotNull Field queryNode,
      final @NotNull GraphQLQuery.GraphQLQueryBuilder queryBuilder) {

    final List<VariableDefinition> newVariables =
        operationDefinition.getVariableDefinitions().stream()
            .filter(v -> usedReferenceNames.contains(v.getName()))
            .collect(Collectors.toList());

    // Only the root alias is dropped: the root fetcher takes the single value of the source's data
    // whatever its key. Nested aliases are forwarded, so the source keys its payload by result key
    // and the same field may be selected twice with different arguments.
    final Field rootField = queryNode.transform(builder -> builder.alias(null));
    final Field newQueryNode =
        injectTypename(
            environment.getGraphQLSchema(),
            rootField,
            (GraphQLCompositeType) environment.getParentType());
    queryBuilder.queryNode(newQueryNode);

    return operationDefinition.transform(
        builder ->
            builder
                .selectionSet(new SelectionSet(List.of(newQueryNode)))
                .variableDefinitions(newVariables));
  }
}
