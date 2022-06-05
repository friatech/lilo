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

Running gateway:

```bash
 ./mvnw -pl bss-lilo-gateway spring-boot:run
```

Running server1:

```bash
 ./mvnw -pl bss-server1 spring-boot:run
```

Running server2:

```bash
 ./mvnw -pl bss-server2 spring-boot:run
```

# Testing

```bash
curl -X POST \
    -H 'content-type: application/json' \
    -d '{"query":"{\ngreeting1\ngreeting2\n}","variables":null}' \
    http://localhost:8080/graphql
```

# Test Cases

- Start Gateway, Server 1, Server 2
  - greeting1 and greeting2 should return success result
- Start Gateway, Server 2
  - Only greeting2 should return successful result
  - Start Server1
  - greeting1 and greeting2 should return success result
  - Stop Server1
  - Only greeting2 should return successful result
  - Start Server1
  - greeting1 and greeting2 should return success result
