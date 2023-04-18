# Lilo

![How GraphQL Stitching works](resources/lilo-and-stitch.webp)

**Lilo** is a super-fast GraphQL stitching library. Project is heavily inspired by [Atlassian Braid](https://bitbucket.org/atlassian/graphql-braid).
We focused simplicity and easy to use while designing **Lilo** and also tried to provide plenty of samples in the codebase. Please don't forget to check `/lilo-samples` directory.

## Installation

Add **Lilo** dependency to your `pom.xml` file.

```xml
<dependencies>
  ...
  <dependency>
    <groupId>io.fria</groupId>
    <artifactId>lilo</artifactId>
    <version>23.4.2</version>
  </dependency>
  ...
</dependencies>
```

If you're using gradle add the dependency to your `build.gradle` file.

```groovy
implementation 'io.fria:lilo:23.4.2'
```

## Basic Usage

Here is the story, Alice has 2 graphql microservices and she wants to make her gateway to dispatch
the graphql requests to their respective microservices. microservice A provides a GraphQL query for user listing,
and microservice B provides a GraphQL mutation for user creation. So Alice can use both query and mutation from
gateway directly. Lilo stitches the GraphQL schemas and provides a combined schema.

```
  +----------------+
  |                |
  | microservice A | <---------+
  |                |           |            +----------------+
  +----------------+           |            |                |        o~
                               +----------- |    Gateway     | <---- /|\
  +----------------+           |            |                |       / \
  |                |           |            +----------------+
  | microservice B | <---------+
  |                |
  +----------------+

```

A very basic working example for that scenario would be:

```java
final Lilo lilo = Lilo.builder()
    .addSource(RemoteSchemaSource.create("SERVER_1", "https://server1/graphql"))
    .addSource(RemoteSchemaSource.create("SERVER_2", "https://server2/graphql"))
    .build();
```

For further details you can examine the `01-spring-boot-hello-world` example in `lilo-samples` folder.

But in most cases, we need authentication or some sort of header manipulation. In that case, creating a custom
introspection and query retrievers might help us to modify outgoing requests or incoming responses. Following
example shows a very basic example for custom retrievers.

```java
    .addSource(
        RemoteSchemaSource.create(
            "SERVER_1",
            new MyIntrospectionRetriever("https://server1/graphql"),
            new MyQueryRetriever("https://server1/graphql")
        )
    )
    .addSource(
        RemoteSchemaSource.create(
            "SERVER_2",
            new MyIntrospectionRetriever("https://server2/graphql"),
            new MyQueryRetriever("https://server2/graphql")
        )
    )
```

For further details you can examine the `02-spring-boot-basic-stitching` example in `lilo-samples` folder.

If Gateway distributes the messages to microservice A and also contains an embedded schema then the architecture can
be like something like this:

```

  +----------------+                       +----------------+
  |                |                       |                |        o~
  | microservice A |---------------------- |    Gateway     | <---- /|\
  |                |                       |                |       / \
  +----------------+                       +----------------+
                                                  A    |
                                                  |    |
                                                  +<---+
```

```java
final Lilo lilo = Lilo.builder()
    .addSource(
        RemoteSchemaSource.create(
            "SERVER_1",
            new MyIntrospectionRetriever("https://server1/graphql"),
            new MyQueryRetriever("https://server1/graphql")
        )
    )
    .addSource(
        DefinedSchemaSource.create(
            "SCHEMA_2",
            stringQueryDefinition,
            typeWiring
        )
    )
    .build();
```

We need to provide an `IntrospectionRetriever` and `QueryRetriever`. Those can fetch the query result from
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

## Alternatives to Lilo

- [Atlassian Braid](https://bitbucket.org/atlassian/graphql-braid)
- [GraphQuilt Orchestrator](https://github.com/graph-quilt/graphql-orchestrator-java)
