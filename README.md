# Lilo
Lilo is a super-fast GraphQL stitching library. Procject is heavily inspired by Atlassian Braid but it seems no more maintained.

Basic Usage:

```java
final Lilo lilo = Lilo.builder()
                      .addSource(
                          SchemaSource.builder()
                                      .name("SERVER_1")
                                      .introspectionRetriever(new MyIntrospectionRetriever("https://server1/graphql"))
                                      .queryRetriever(new MyQueryRetriever("https://server1/graphql"))
                                      .build()              
                      )
                      .addSource(
                          SchemaSource.builder()
                                      .name("SERVER_2")
                                      .introspectionRetriever(new MyIntrospectionRetriever("https://server2/graphql"))
                                      .queryRetriever(new MyQueryRetriever("https://server2/graphql"))
                                      .build()              
                      )
                      .build();
```

You need to provide a `IntrospectionRetriever` and `QueryRetriever`. Those can fetch the query result from
a remote source or local source or you can implement a RSocket communication via the target server.

After every thing properly set you can run your commands:

```java

final GraphQLRequest graphQLRequest = GraphQLRequest.builder()
                                                    .query(incomingQuery)
                                                    .operationName(incomingOperationName)
                                                    .variables(incomingVariables)
                                                    .build();

final Map<String, Object> resultMap = lilo.stitch(graphQLRequest.toExecutionInput()).toSpecification();

// You can serialize to JSON and return map as String, following is the Jackson example of serializing
final String JsonResult = new ObjectMapper().writeValueAsString(resultMap);
```