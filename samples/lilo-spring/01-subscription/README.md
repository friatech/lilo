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

Example uses default versions of retrievers for exhibiting the proper usage of subscription mechanism.

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
