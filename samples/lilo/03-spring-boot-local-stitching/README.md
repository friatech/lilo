In this example there is one gateway and 2 different servers. `Server 1` provides `greeting1` query and
server2 provides `greeting2` query.

```
                                              ------------
                -----------                   |          |
                |         | --- stitches ---> | Server 1 |
                |         |                   |          |
                |         |                   ------------
--- REQUEST --> | Gateway |
                |         |                   ------------
                |         |                   |          |
                |         | --- stitches ---> | Local    |
                -----------                   | Schema   |
                                              ------------
```

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

Running Server 2 project on port 8081:

```shell
./mvnw -pl server1 spring-boot:run
```

# Testing

```shell
curl -s -X POST \
  -H 'content-type: application/json' \
  -d '{"query":"{\ngreeting1\ngreeting2\n}","variables":null}' \
  http://localhost:8080/graphql
```

Expected result:

```json
{
  "data": {
    "greeting1": "Hello from Server 1",
    "greeting2": "Hello from local schema"
  }
}
```

Alternatively, you can use the embedded GraphiQL client web page on `http://localhost:8080`

```graphql
query {
  greeting1
  greeting2
}
```
