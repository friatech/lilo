# Lilo [![Build Status](https://github.com/friatech/lilo/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/friatech/lilo/actions/workflows/ci.yml?query=branch%3Amain) [![Latest Release](https://img.shields.io/maven-central/v/io.fria/lilo?versionPrefix=24.)](https://maven-badges.herokuapp.com/maven-central/io.fria/lilo/) [![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

![Lilo and Stitch](resources/lilo-and-stitch.webp)

**Lilo** is a super-fast GraphQL stitching library. The project is heavily inspired by [Atlassian Braid](https://bitbucket.org/atlassian/graphql-braid).
**Lilo** focuses on simplicity and easy to use. You can find plenty of samples in the codebase and we aim to add more and more in every release.
Please do not forget to check `/samples` directory.

We are planning to add additional frameworks supports. For now SpringBoot is supported. If you will use `Lilo` with `SpringBoot`. Please check [Lilo Spring Documentation](lilo-spring/README.md).

## Installation

Just add a single **Lilo** dependency to your `pom.xml` file.

```xml
<dependencies>
  ...
  <dependency>
    <groupId>io.fria</groupId>
    <artifactId>lilo</artifactId>
    <version>24.5.0</version>
  </dependency>
  ...
</dependencies>
```

If you're using gradle add the dependency to your `build.gradle` file.

```groovy
implementation 'io.fria:lilo:24.5.0'
```

## Basic Usage

Here is the story, Alice has 2 GraphQL microservices and she wants to make her gateway to dispatch
the GraphQL requests to their respective microservices. `Microservice A` provides a GraphQL query for user listing,
and `Microservice B` provides a GraphQL mutation for user creation. So Alice can use both query and mutation via
just sending requests to the `Gateway` directly. **Lilo** stitches the GraphQL schemas and provides a combined schema.

```
  +----------------+
  |                |
  | Microservice A | <---------+
  |                |           |            +----------------+
  +----------------+           |            |                |        o~
                               +----------- |    Gateway     | <---- /|\
  +----------------+           |            |                |       / \
  |                |           |            +----------------+
  | Microservice B | <---------+
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

But for a complete working example you need to serve the stitching logic from a GraphQL endpoint. (Probably `/graphql`)
Please examine the `01-spring-boot-hello-world` example in `lilo-samples` folder.

In most cases, we need an authentication or some sort of header manipulation. In that case, creating custom
introspection and query retrievers might help us to modify outgoing requests or incoming responses. The following
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

For further details, you can examine the `02-spring-boot-basic-stitching` example in `lilo-samples` folder.

If Gateway distributes the messages to `Microservice A` and also contains an embedded schema then the architecture might
be something like this:

```

  +----------------+                       +----------------+
  |                |                       |                |        o~
  | Microservice A |---------------------- |    Gateway     | <---- /|\
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
a remote source and/or local source.

After source definitions are properly set you can run your implementation similar to this:

```java
final Map<String, Object> resultMap = lilo.stitch("{greeting1}").toSpecification();
```

Or more advanced GraphQL request which includes querym operation name and variables:

```java

final GraphQLRequest graphQLRequest = GraphQLRequest.builder()
    .query(incomingGraphQlQuery)
    .operationName(incomingGraphQlOperationName)
    .variables(incomingGraphQlVariables)
    .build();

final Map<String, Object> resultMap = lilo.stitch(graphQLRequest.toExecutionInput()).toSpecification();
```

## Parameter passing

Sometimes an HTTP header or a JWT token must be passed to the retrievers. You can pass a local context object
inside the execution input.

```java
final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
    .localContext("aLocalContextObject")
    .query("{add(a: 1, b: 2)}")
    .build();
```

The localContext object is now accessible from your `IntrospectionRetriever` and `QueryRetriever`.

## Subscription

GraphQL provides a subscription methodology for providing continuous data. It's a well-known pub/sub pattern for applications. 
If your application needs a continuous stream of real-time events, notifications, sensor data. Subscription might be wise
choice for you.

### Use Lilo framework library

Subscription usage needs a few configuration class definition and handlers definition. Lilo core library is framework-agnostic. If you are using
a framework like SpringBoot. Please check the lilo documentation for that specific framework. Currently, `SpringBoot` is supported.
Please check [Lilo Spring Documentation](lilo-spring/README.md).

### Subscription Usage

If you're going to use Subscription stitching. You need to define an additional retriever other than `QueryRetriever` and `IntrospectionRetriever`.
As you might guess, it's a `SubscriptionRetriever`. Besides retrievers, we need to define handlers:
- `SubscriptionGatewayHandler` is responsible for handling web socket sessions coming from GraphQL clients to Lilo Stitching Gateway.
- `SubscriptionSourceHandler` is responsible for handling websocket sessions going from gateway to remote GraphQL servers.

Since those handler classes are framework-agnostic. A binding should be implemented between framework and handlers.

## Additional Resources

You can watch `Tame Your Spring Microservices With GraphQL Stitching Using Lilo` presentation.

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/5GQpxqORlr0/0.jpg)](https://www.youtube.com/watch?v=5GQpxqORlr0)

## Lilo's sisters

- [Atlassian Braid](https://bitbucket.org/atlassian/graphql-braid)
- [GraphQuilt Orchestrator](https://github.com/graph-quilt/graphql-orchestrator-java)
