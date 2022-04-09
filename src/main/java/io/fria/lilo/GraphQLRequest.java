package io.fria.lilo;

import graphql.ExecutionInput;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphQLRequest {

  public static final String INTROSPECTION_QUERY =
      "\n"
          + "  query IntrospectionQuery {\n"
          + "    __schema {\n"
          + "      queryType { name }\n"
          + "      mutationType { name }\n"
          + "      subscriptionType { name }\n"
          + "      types {\n"
          + "        ...FullType\n"
          + "      }\n"
          + "      directives {\n"
          + "        name\n"
          + "        description\n"
          + "        locations\n"
          + "        args {\n"
          + "          ...InputValue\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "\n"
          + "  fragment FullType on __Type {\n"
          + "    kind\n"
          + "    name\n"
          + "    description\n"
          + "    fields(includeDeprecated: true) {\n"
          + "      name\n"
          + "      description\n"
          + "      args {\n"
          + "        ...InputValue\n"
          + "      }\n"
          + "      type {\n"
          + "        ...TypeRef\n"
          + "      }\n"
          + "      isDeprecated\n"
          + "      deprecationReason\n"
          + "    }\n"
          + "    inputFields {\n"
          + "      ...InputValue\n"
          + "    }\n"
          + "    interfaces {\n"
          + "      ...TypeRef\n"
          + "    }\n"
          + "    enumValues(includeDeprecated: true) {\n"
          + "      name\n"
          + "      description\n"
          + "      isDeprecated\n"
          + "      deprecationReason\n"
          + "    }\n"
          + "    possibleTypes {\n"
          + "      ...TypeRef\n"
          + "    }\n"
          + "  }\n"
          + "\n"
          + "  fragment InputValue on __InputValue {\n"
          + "    name\n"
          + "    description\n"
          + "    type { ...TypeRef }\n"
          + "    defaultValue\n"
          + "  }\n"
          + "\n"
          +
          //
          // The depth of the types is actually an arbitrary decision.  It could be any depth in
          // fact.  This depth
          // was taken from GraphIQL
          // https://github.com/graphql/graphiql/blob/master/src/utility/introspectionQueries.js
          // which uses 7 levels and hence could represent a type like say [[[[[Float!]]]]]
          //
          "fragment TypeRef on __Type {\n"
          + "    kind\n"
          + "    name\n"
          + "    ofType {\n"
          + "      kind\n"
          + "      name\n"
          + "      ofType {\n"
          + "        kind\n"
          + "        name\n"
          + "        ofType {\n"
          + "          kind\n"
          + "          name\n"
          + "          ofType {\n"
          + "            kind\n"
          + "            name\n"
          + "            ofType {\n"
          + "              kind\n"
          + "              name\n"
          + "              ofType {\n"
          + "                kind\n"
          + "                name\n"
          + "                ofType {\n"
          + "                  kind\n"
          + "                  name\n"
          + "                }\n"
          + "              }\n"
          + "            }\n"
          + "          }\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "\n";

  private String query;
  private String operationName;
  private Map<String, Object> variables;

  public ExecutionInput toExecutionInput(final Object localContext) {

    final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput().query(this.query);

    if (this.operationName != null) {
      builder.operationName(this.operationName);
    }

    if (this.variables != null && !this.variables.isEmpty()) {
      builder.variables(this.variables);
    }

    if (localContext != null) {
      builder.localContext(localContext);
    }

    return builder.build();
  }

  public ExecutionInput toExecutionInput() {

    return this.toExecutionInput(null);
  }
}
