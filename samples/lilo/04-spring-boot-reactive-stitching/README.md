In this example there is one gateway and 2 different servers. `Server 1` provides `greeting1` query and
server2 provides `greeting2` query. Gateway uses spring reactive and async communication.

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

Running Server 1 project on port 8082:

```shell
./mvnw -pl server2 spring-boot:run
```

# Testing

```shell
curl -X POST \
    -H 'content-type: application/json' \
    -d '{"query":"{\ngreeting1\ngreeting2\n}","variables":null}' \
    http://localhost:8080/graphql
```

Expected result:

```json
{
  "data": {
    "greeting1": "Hello from Server 1",
    "greeting2": "Hello from Server 2"
  }
}
```