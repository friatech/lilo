# Contributing to Lilo

Thanks for helping out. Please keep changes small and focused.

## Build

Build with the Maven wrapper on JDK 21 (the build tooling requires it; the `lilo` bytecode still targets Java 11 and `lilo-spring` Java 17):

```
./mvnw -B -ntp clean verify -Dgpg.skip
```

This is the gate that every change must pass. It runs the tests, Checkstyle, Error Prone, SpotBugs and the license header check (Apache RAT). The code formatter (fmt) runs during `verify` and rewrites files in place, so commit the result. New Java files carry the Apache license header used by the existing files. CI also runs the `lilo` tests on JDK 11.

## Tests first

Write the test before the change and see it fail for the right reason, then make it pass. Bug fixes need a test that fails without the fix. Keep tests deterministic: use latches and timeouts instead of sleeps.

## Samples

The samples live in `samples/lilo` and `samples/lilo-spring` and build against the `SNAPSHOT` version of Lilo. Install Lilo first, then build a sample:

```
./mvnw install -DskipTests -Dgpg.skip
```

## Commits and pull requests

- Use a short imperative subject, for example `Fix alias forwarding for nested fields`.
- Do not change `<version>` values, README install snippets or the badge; the release process owns versions.
- Fill in the pull request template: summary, behavior changes and testing. Call out any change that alters behavior for existing users.
- Add an entry to the `Unreleased` section of `CHANGELOG.md` for user-visible changes.

## Security

Report vulnerabilities privately as described in `SECURITY.md`.
