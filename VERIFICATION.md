# Verification evidence

Checked in the build workspace on 2026-09-11.

## Passed

`./scripts/check-offline.sh` compiled the actual production `WebhookCrypto.java` with the Java 17 compiler module and executed 12 dependency-free assertions:

1. HMAC-SHA256 fixed known-answer vector.
2. Correct signature accepted.
3. Uppercase hex accepted.
4. Raw-body whitespace tampering rejected.
5. Wrong secret rejected.
6. Missing signature rejected.
7. Malformed hexadecimal signature rejected.
8. Truncated signature rejected.
9. SHA-256 `abc` known-answer vector.
10. Identical byte bodies produce identical fingerprints.
11. Whitespace changes alter the fingerprint.
12. Amount changes alter the fingerprint.

Also passed: Java 17 syntax-only parsing of all 27 Java sources; POM XML parsing; application and CI YAML parsing; Python demo syntax; shell syntax; `git diff --check`.

## Not verified

- Full Maven compilation/type checking, Spring context startup, Hibernate schema validation, and Flyway execution.
- All HTTP/PostgreSQL integration tests, including the 20-concurrent-duplicate guarantee.
- The JUnit unit-test class (the corresponding standalone crypto checks did run).
- The running-application demo, Windows launcher, and GitHub Actions execution.

The machine has a Java 17 runtime with `jdk.compiler` available but no `javac` executable, Maven installation, or Docker executable/daemon. An initial Maven Central download probe did not complete; the tool reported that network approval was cancelled. No dependency download or full build success is claimed. The offline script invokes the bundled compiler module directly and does not need external dependencies.

## Required next verification

On a machine with JDK 17, Docker running, and Maven Central/Docker Hub access:

```sh
./mvnw -B test
```

Inspect `target/surefire-reports/`; all five required integration classes must execute, with zero failures/errors/skips. CI intentionally does not skip integration tests if Docker is unavailable. Fix any failures before describing the ledger's transactional/concurrency guarantees as experimentally proven or replacing the limited resume bullets.
