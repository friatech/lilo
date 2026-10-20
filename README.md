# Lilo [![Build Status](https://github.com/friatech/lilo/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/friatech/lilo/actions/workflows/ci.yml?query=branch%3Amain) [![Latest Release](https://img.shields.io/maven-central/v/io.fria/lilo?versionPrefix=24.)](https://maven-badges.herokuapp.com/maven-central/io.fria/lilo/) [![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

![Lilo and Stitch](resources/lilo-and-stitch.webp)

**Lilo** is a super-fast GraphQL stitching library. The project is heavily inspired by [Atlassian Braid](https://bitbucket.org/atlassian/graphql-braid).
**Lilo** focuses on simplicity and easy to use. You can find plenty of samples in the codebase and we aim to add more and more in every release.
Please do not forget to check `/samples` directory: `samples/lilo` holds the core examples (`01` to `06`) and `samples/lilo-spring` holds the `lilo-spring` subscription example.
The samples build against the `SNAPSHOT` version of Lilo, so run `./mvnw install -DskipTests -Dgpg.skip` at the repository root first, then build the sample you want.

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

## Compatibility and upgrade notes

| Module | Java | Built on |
|---|---|---|
| `lilo` | 11+ | graphql-java 26, Reactor 3.8 |
| `lilo-spring` | 17+ | Spring 7, Spring Boot 4 |

- Building Lilo itself needs JDK 21 (the build tooling requires it). The produced bytecode targets Java 11 (`lilo`) and Java 17 (`lilo-spring`).
- `lilo-spring` no longer supports Spring Boot 3. Use an earlier release if you are on Boot 3.
- `GraphQLQuery`, `GraphQLRequest`, `LiloContext` and `SubscriptionGatewayHandler` are now `final`.
- Collections returned by `GraphQLRequest`, `GraphQLQuery`, `GraphQLResult`, `GraphQLResultError` and `LiloSourceDataFetcherException` getters (variables, arguments, extensions, path, errors) are now read-only, and setters/builders store copies. Code that mutates `request.getVariables()` must call `setVariables(...)` with a new map instead.
- graphql-java 23+ enables strict wiring: a `RuntimeWiring` that registers the same field twice now throws `StrictModeWiringException`. Stitching two sources that declare the same root field also fails at merge time with this exception.

## Behavior and limitations

- Lilo stitches root fields only. There is no type merging or entity resolution across sources.
- Non-root types, enums, scalars and directives defined by several sources must be identical. Descriptions, comments, the order of members (fields, arguments, input fields, enum values, union members, implemented interfaces and directive locations) and the order of the directives applied to a type are ignored in the comparison; any other difference, such as a field type, nullability, an argument type or an extra or missing member, fails at merge; the root cause is an `IllegalArgumentException` naming the type and both sources (`stitch` may wrap it).
- Type extensions (`extend type`, `extend interface`, `extend input`, `extend enum`, `extend union`, `extend scalar`, including `extend type Query`) are folded into the type they extend within the same source before the comparison above, so a type split into a definition and extensions is equal to the same type written in one piece. A source cannot extend a type that only another source defines: a `DefinedSchemaSource` fails to load with "missing its base underlying type".
- Interface and union fields work because the gateway requests `__typename` from remote sources itself. A `DefinedSchemaSource` still needs its own type resolver for the source schema to build, and its data fetchers must return `__typename` in the returned `Map` for abstract types. Otherwise stitching throws "Please provide __typename for union types" (or "...for interface types"); this is an exception, not a GraphQL error in the result.
- Aliases are forwarded to remote sources below the root field, and nested values are read by result key. Root field aliases are not forwarded.
- When a remote source returns both data and errors, the data is kept and the errors are reported. When it returns only errors, the errors are reported.
- Schema loading is single-flight: concurrent first requests share one introspection per source, and `invalidate`/`invalidateAll` still force a reload. There is no timeout or backoff on schema loading, so a hung introspection blocks requests until it completes. Set timeouts on custom retrievers.
- `maxQueryDepth(int)` and `maxQueryComplexity(int)` on `Lilo.builder()` reject a query that exceeds the limit with a GraphQL error and no data, before any source is called. They use graphql-java's standard `MaxQueryDepthInstrumentation` (root fields are at depth 1) and `MaxQueryComplexityInstrumentation` (every field counts 1), chained before your own `instrumentation(...)`. No limit is set by default; zero or negative values throw `IllegalArgumentException`.
- `schemaDescription(String)` sets the description clients read as `__schema.description` through introspection, for example to say which sources the gateway stitches. Descriptions of the source schemas are not merged. It is unset by default.
- `introspectionEnabled(false)` rejects `__schema` and `__type` queries on the gateway with a GraphQL error. It is per Lilo instance (graphql-java's per-request `INTROSPECTION_DISABLED` context flag, not the JVM-wide switch) and does not affect the introspection Lilo sends to its sources. Introspection is enabled by default.
- `graphQLCustomizer(Consumer<GraphQL.Builder>)` runs each time the merged schema is built, after Lilo's own settings, so you can add a `PreparsedDocumentProvider`, an execution strategy and so on, or override Lilo's settings.
- A customizer that replaces the instrumentation drops the `maxQueryDepth`/`maxQueryComplexity` limits and the `introspectionEnabled(false)` guard, and one that sets an execution strategy that does not extend graphql-java's `AsyncExecutionStrategy` or `AsyncSerialExecutionStrategy` bypasses the introspection guard; pass your instrumentation through `instrumentation(...)` instead.
- An exception thrown by the customizer fails every schema load, and with it the requests waiting for the schema.

### Subscriptions

- `ping` is answered with `pong` (the payload is echoed back); a client `pong` is ignored. A message that is not valid JSON, has no type, or has a server-only type (`connection_ack`, `next`, `error`) closes the session. A client must send `connection_init` before `subscribe`, otherwise the session is closed; a second `connection_init` on the same session also closes it.
- One socket carries several operations, each tracked by its operation id. A client `complete` cancels only that operation, a duplicate active id closes the session, and a finished or failed id can be reused. A session may have at most 50 operations at once; further `subscribe` messages get an `error` frame ("Too many subscriptions on this connection") and the session stays open. Stitching runs on a bounded pool, and when it is saturated the `subscribe` gets an `error` frame ("Too many concurrent subscription requests").
- Invalid subscribe payloads, GraphQL errors, non-subscription operations and failures reported by a remote source are sent to the client as `error` frames (the operation id plus a JSON array of GraphQL errors) instead of being thrown.
- The `connection_init` payload and the handshake request headers reach a `SubscriptionRetriever` as the `localContext` argument, which is a `SubscriptionConnectionContext`:

```java
final SubscriptionConnectionContext context = (SubscriptionConnectionContext) localContext;
final Map<String, Object> payload = context.getConnectionInitPayload();
final String authorization = context.getHandshakeHeaders().get("authorization"); // names are lower-case, first value only
```

  Both maps are read-only at the top level and the headers can carry credentials (such as `Authorization` and `Cookie`), so do not log them.
- With `lilo-spring`, `@EnableLilo` also enables Spring's WebSocket support, so you don't need to add `@EnableWebSocket`. Your application still needs Spring MVC and a WebSocket-capable servlet container (for example `spring-boot-starter-web` and `spring-boot-starter-websocket`).
- `lilo.allowed-origins` lists the allowed WebSocket origin patterns. It defaults to `*` (every origin); an empty list allows only same-origin requests.
- Each client connection has bounded sends. A client that takes longer than `lilo.send-time-limit` (default `10s`) to accept a frame, or whose queued frames exceed `lilo.send-buffer-size-limit` bytes (default `524288`), is disconnected with close status 4500 and its subscriptions are released. The limits are checked when another frame is queued; the servlet container's own send timeout still bounds the frame being written. Size the buffer for your events: a stream of frames bigger than the limit, or frames arriving faster than the client reads them, trips it. A 4096-bin spectrogram event is about 210 KiB, so at 30 events per second the default `524288` holds about two queued events.
- `lilo.subscription-pool-size` sets the core size of the pool that stitches subscriptions (default max(4, available processors); the pool grows to four times that and queues up to 256 more requests), and `lilo.max-operations-per-session` sets how many operations one client connection may have at once (default `50`). A `subscribe` beyond either limit gets an `error` frame and the connection stays open. To configure the gateway in code instead, pass your own `SubscriptionGatewayHandler` to `GatewayWebSocketHandler(SubscriptionGatewayHandler, String, Duration, int)`.
- Frames from a source can be large. A jakarta.websocket container (Tomcat included) receives a text message bigger than its inbound buffer, 8 KiB by default, only through a handler that supports partial messages; otherwise it closes the socket with status 1009 and the operation ends. `lilo-spring`'s `SourceWebSocketHandler` supports partial messages and assembles up to `SourceWebSocketHandler.DEFAULT_MAX_MESSAGE_SIZE` characters (10 Mi; a constructor argument sets another limit), so `lilo-spring` users need no container tuning. The limit counts characters (UTF-16 `char`s), not bytes: 10 Mi characters take up to 20 MiB of heap and can be up to about 30 MB of UTF-8 on the wire. A bigger message fails the operation with an `error` frame, the source session is closed and the gateway logs the cause once at `WARN`. If you write your own source handler on Spring, override `supportsPartialMessages()` and assemble the fragments, as sample 06 does. With an older Lilo version, or a handler that cannot do that, raise the client container's limit (`WebSocketContainer.setDefaultMaxTextMessageBufferSize` on the container passed to `StandardWebSocketClient`), but size it to the largest real event rather than making it large: the container allocates that buffer for every session up front (about 2 bytes per character), and every subscription operation has its own source session. The same 8 KiB default applies to frames the gateway receives from clients; raise it with Spring's `ServletServerContainerFactoryBean.setMaxTextMessageBufferSize` if clients send large `subscribe` payloads. A source socket that closes while its operation is active fails the operation; the close status (for example `1009 The decoded text message was too big ...`) is logged by the gateway at `WARN`. An `error` message the source sends itself (for example an authorization failure) is forwarded to the client and logged only at `DEBUG`.

### Default remote retrievers

`DefaultRemoteQueryRetriever` and `DefaultRemoteIntrospectionRetriever` accept a `RemoteRetrieverOptions`. Defaults: 5 s connect timeout, 30 s request timeout, 10 MiB maximum response, redirects not followed, only `http`/`https` URLs, `Accept: application/json`. A non-2xx response whose body is a GraphQL response with `errors` is returned to the engine; any other non-2xx response throws `IllegalStateException` with the status and a truncated body.

```java
final RemoteRetrieverOptions options =
    RemoteRetrieverOptions.builder()
        .requestTimeout(Duration.ofSeconds(10))
        .headers((liloContext, localContext) -> Map.of("Authorization", "Bearer " + token))
        .build();

final DefaultRemoteQueryRetriever retriever =
    new DefaultRemoteQueryRetriever("https://example.com/graphql", options);
```

Each retriever owns an `HttpClient`, so create one per source and reuse it. The protected two-argument `retrieve(String, String)` still exists, but the default retrievers now call the options-aware overload, so a subclass overriding only the two-argument method is no longer invoked by `get`.

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
Please examine the `01-spring-boot-hello-world` example in the `samples/lilo` folder.

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

For further details, you can examine the `02-spring-boot-basic-stitching` example in the `samples/lilo` folder.

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
