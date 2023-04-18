In this example there is one gateway and 2 different servers. server1 provides `greeting1` query and
server2 provides `greeting2` query.

```
                                              -----------
                -----------                   |         |
                |         | --- stitches ---> | Server1 |
                |         |                   |         |
                |         |                   -----------
--- REQUEST --> | Gateway |
                |         |                   -----------
                |         |                   |         |
                |         | --- stitches ---> | Server2 |
                -----------                   |         |
                                              -----------
```

# Running

Examples require Java 17+ to run

Running gateway:

```bash
 ./mvnw -pl rss-lilo-gateway spring-boot:run
```

Running server1:

```bash
 ./mvnw -pl rss-server1 spring-boot:run
```

Running server2:

```bash
 ./mvnw -pl rss-server2 spring-boot:run
```

# Testing

```bash
curl -X POST \
    -H 'content-type: application/json' \
    -d '{"query":"{\ngreeting1\ngreeting2\n}","variables":null}' \
    http://localhost:8080/graphql
```
