# Lilo
Lilo is a super-fast GraphQL stitching library. Project is heavily inspired by Atlassian Braid, but it seems no more maintained.

## Installation

Add dependencies to your `pom.xml` file.

```xml
<dependencies>
  ...
  <dependency>
    <groupId>io.fria</groupId>
    <artifactId>lilo</artifactId>
    <version>22.5.0</version>
  </dependency>
  ...
</dependencies>
```

If you're using gradle add the dependency to your `build.gradle` file.

```groovy
implementation 'io.fria:lilo:22.5.0'
```

## Basic Usage

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
a remote source or local source, or you can implement an RSocket communication via the target server.

After every thing is properly set you can run your implementation similar to this:

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

## Parameter passing

Sometimes an HTTP header or a JWT token should be passed to the retrievers. You can pass a local context object
inside the execution input.

```java
final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                                                    .localContext("aLocalContextObject")
                                                    .query("{add(a: 1, b: 2)}")
                                                    .build();
```

The localContext object is now accessible from your `IntrospectionRetriever` and `QueryRetriever`.
