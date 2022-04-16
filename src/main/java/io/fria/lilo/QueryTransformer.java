package io.fria.lilo;

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static io.fria.lilo.JsonUtils.toStr;

final class QueryTransformer {

  private QueryTransformer() {
    // Utility class
  }

  static GraphQLQuery extractQuery(final DataFetchingEnvironment environment) {

    final var queryBuilder = GraphQLQuery.builder();
    transformDocument(environment, queryBuilder);

    return queryBuilder.build();
  }

  private static void findUsedItems(
      final Node<?> node,
      final Set<String> usedReferenceNames,
      final Set<String> usedFragmentNames) {

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
      final Node<?> node,
      final Set<String> usedReferenceNames,
      final Set<String> usedFragmentNames) {

    node.getChildren().forEach(n -> findUsedItems(n, usedReferenceNames, usedFragmentNames));
  }

  private static FragmentDefinition removeAlias(final FragmentDefinition fragment) {

    final SelectionSet selectionSet = fragment.getSelectionSet();

    if (selectionSet == null) {
      return fragment;
    }

    final List<Selection> newSelections =
        selectionSet.getSelections().stream()
            .map(
                s -> {
                  if (s instanceof Field) {
                    return removeAlias((Field) s);
                  }

                  return s;
                })
            .collect(Collectors.toList());

    return fragment.transform(
        builder ->
            builder.selectionSet(SelectionSet.newSelectionSet(newSelections).build()).build());
  }

  private static Field removeAlias(final Field field) {

    final List<Selection> newSelections = removeAliasInChildren(field);

    return field.transform(
        builder -> {
          if (newSelections != null) {
            builder
                .alias(null)
                .selectionSet(SelectionSet.newSelectionSet(newSelections).build())
                .build();
          } else {
            builder.alias(null).build();
          }
        });
  }

  private static List<Selection> removeAliasInChildren(final Field field) {

    final SelectionSet selectionSet = field.getSelectionSet();

    List<Selection> newSelections = null;

    if (selectionSet != null) {
      newSelections =
          selectionSet.getSelections().stream()
              .map(
                  s -> {
                    if (s instanceof Field) {
                      return removeAlias((Field) s);
                    }

                    return s;
                  })
              .collect(Collectors.toList());
    }

    return newSelections;
  }

  private static List<Definition> transformDefinitions(
      final DataFetchingEnvironment environment,
      final List<Definition> originalDefinitions,
      final GraphQLRequest.GraphQLRequestBuilder requestBuilder,
      final GraphQLQuery.GraphQLQueryBuilder queryBuilder) {

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
            operationDefinition, usedReferenceNames, queryNode, queryBuilder));
    newDefinitions.addAll(transformFragmentDefinitions(originalDefinitions, usedFragmentNames));

    final Map<String, Object> filteredVariables = new HashMap<>();

    environment.getVariables().entrySet().stream()
        .filter(e -> usedReferenceNames.contains(e.getKey()))
        .forEach(e -> filteredVariables.put(e.getKey(), e.getValue()));

    requestBuilder.variables(filteredVariables);

    return newDefinitions;
  }

  private static Document transformDocument(
      final DataFetchingEnvironment environment,
      final GraphQLQuery.GraphQLQueryBuilder queryBuilder) {

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

    queryBuilder.query(request);

    return newDocument;
  }

  private static List<Definition<?>> transformFragmentDefinitions(
      final List<Definition> originalDefinitions, final Set<String> usedFragmentNames) {

    return originalDefinitions.stream()
        .filter(d -> d instanceof FragmentDefinition)
        .map(d -> (FragmentDefinition) d)
        .filter(fd -> usedFragmentNames.contains(fd.getName()))
        .map(QueryTransformer::removeAlias)
        .collect(Collectors.toList());
  }

  private static Definition<?> transformOperationDefinition(
      final OperationDefinition operationDefinition,
      final Set<String> usedReferenceNames,
      final Field queryNode,
      final GraphQLQuery.GraphQLQueryBuilder queryBuilder) {

    final List<VariableDefinition> newVariables =
        operationDefinition.getVariableDefinitions().stream()
            .filter(v -> usedReferenceNames.contains(v.getName()))
            .collect(Collectors.toList());

    final Field newQueryNode = removeAlias(queryNode);
    queryBuilder.queryNode(newQueryNode);

    return operationDefinition.transform(
        builder ->
            builder
                .selectionSet(new SelectionSet(List.of(newQueryNode)))
                .variableDefinitions(newVariables));
  }
}
