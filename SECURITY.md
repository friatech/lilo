# Security Policy

## Supported versions

Only the latest release line of Lilo receives security fixes. Please upgrade before reporting an issue that is already fixed there.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Use GitHub private vulnerability reporting instead: open the repository's **Security** tab, choose **Report a vulnerability**, and describe the problem, the affected Lilo version and a minimal way to reproduce it.

## Scope

In scope: the `lilo` and `lilo-spring` modules, for example schema stitching and request forwarding, the default remote retrievers, and the WebSocket subscription gateway (origin checks, send limits, per-session limits, context propagation).

Out of scope: the sample applications in `samples/`, vulnerabilities in third-party dependencies that are not reachable through Lilo (report those upstream), and the security of the remote GraphQL services that Lilo forwards requests to.
