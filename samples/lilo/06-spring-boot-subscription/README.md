# Before starting

This example is a little bit complicated, although we have tried to make it as simple as possible. The main reason is;
every web framework handles web socket connections differently. To maintain Lilo's framework agnostic structure.
Some subscription handlers are added. But it's still not easy to use but highly configurable. Using `lilo-spring` dependency
for easy-to-use configuration on SpringFramework might be wise choice.

# About

```
                                              ------------
                -----------                   |          |
                |         | --- stitches ---> | Server 1 |
                |         |                   |          |
                |         |                   ------------
--- REQUEST --> | Gateway |
                |         |                   ------------
                |         |                   |          |
                |         | --- stitches ---> | Server 2 |
                -----------                   |          |
                                              ------------
```

In this example there is one gateway and 2 different servers. `Server 1` provides `greeting1` query and
`greeting1Subscription`. Also, `Server 2` provides `greeting2` query and `greeting2Subscription`.

Unlike other examples, 3 retrievers should be defined. One of them is `IntrospectionRetrieverImpl` which is an implementation
of `SyncIntrospectionRetriever`. It aims to retrieve whole GraphQL schema definitions from remote servers; `Server 1`
and `Server 2`. There's another retriever (`QueryRetrieverImpl`) for getting specific query results from specific server.
And SubscriptionRetrieverImpl is responsible for a websocket connection between gateway and servers.

We have 2 handlers for handling web-socket connections and transferring them to lilo.
- `GatewayWebSocketHandler` is responsible for handling web socket sessions coming from GraphQL clients to Lilo Stitching Gateway
- `SourceWebSocketHandler` is responsible for handling websocket sessions going from gateway to remote GraphQL servers.

`LiloHandlerMapping` is a dispatcher class when we use the same path for subscription and GraphQL queries.

# Running

Examples require Java 17+ to run

Better to compile the whole project first:

```shell
./mvnw clean compile package
```

Running Gateway project on port 8080:

```shell
./mvnw -pl lilo-gateway spring-boot:run
```

Running Server 1 project on port 8081:

```shell
./mvnw -pl server1 spring-boot:run
```

Running Server 2 project on port 8082:

```shell
./mvnw -pl server2 spring-boot:run
```

# Testing

For testing you can use the embedded GraphiQL client web page on `http://localhost:8080`

```graphql
subscription {
  greeting1Subscription
}
```

or

```graphql
subscription {
  greeting2Subscription
}
```
