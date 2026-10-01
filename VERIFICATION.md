# Verification evidence

Everything here was observed by running it; nothing is inferred.

## Observed results

| What | Command / environment | Result |
| --- | --- | --- |
| Full suite, fresh database | `TEST_DB_URL=... mvn -B verify`, JDK 21 runtime, **PostgreSQL 16.14 (local server, no Docker available in that sandbox)**, 2026-10-01 | **45 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |
| Repeat on a dirty database | same suite run twice more against a non-clean DB | 45/45 both times (this exposed and fixed one test that reused a fixed key) |
| Packaged app + `scripts/demo.py` | `java -jar` against PostgreSQL 16, signed payment sent twice | Same response twice, 1 entry, balance 1000, reconcile `ok:true` |
| Startup without a secret | `java -jar` with `WEBHOOK_SECRET` unset | Refuses to start with an explanatory error |
| Prior CI (before this change) | GitHub Actions run 6/7/8 on `main`, `ubuntu-latest`, Testcontainers `postgres:16-alpine` | 23/23 green. Runs 4–5 failed (401/HttpURLConnection streaming issue) before the Apache HttpClient fix |

**Not yet observed:** the *new* 45-test suite under GitHub Actions with Testcontainers (it has not been pushed to a CI run at the time of writing; check the Actions tab / badge). Running the same code through Testcontainers vs. a local PostgreSQL differs only in how the database is provisioned.

## Mutation checks (manual, temporary, reverted)

To confirm the concurrency tests detect the bugs they claim to:

| Deliberate bug | Tests that failed |
| --- | --- |
| Account lock removed (`findById` instead of `FOR UPDATE`) | `differentKeysOnSameAccountDoNotLoseCredits`, `tenKeysWithFiveDuplicatesEachCreditExactlyTenTimes`, `reconciliationNeverSeesDriftWhileCreditsAreInFlight` |
| Duplicate recovery disabled (unique violation escapes) | `twentyParallel...`, `competingBodies...`, `tenKeys...`, `reconciliation...`, `MultiInstanceTest` |

Note what this says: the classic "20 identical requests" test does **not** detect a missing account lock, because the claim row already serialises identical keys. The lock is covered by the different-keys tests.

## What the concurrency tests prove — and do not

Prove: under 20–50 simultaneous HTTP requests against real PostgreSQL (`READ COMMITTED`), including requests split across two independent app contexts with separate connection pools, a key is credited once, different keys are not lost, failed attempts leave no claim, and reconciliation never observes a half-applied credit.

Do **not** prove: behaviour across separate machines/networks, database failover, a crash between statements, other isolation levels, high load, or absence of every possible interleaving. Thread timing is not controlled, so the loser-side unique-violation path is exercised probabilistically in the HTTP tests; it is covered **deterministically** by `DatabaseConstraintsTest.uniqueClaimIsTheArbiter...`.

## Reproduce

```sh
./mvnw -B verify                                   # Docker required (Testcontainers)
TEST_DB_URL=jdbc:postgresql://host:5432/db TEST_DB_USER=u TEST_DB_PASSWORD=p ./mvnw -B test   # disposable DB, no Docker
```
