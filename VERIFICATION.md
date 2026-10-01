# Verification evidence

Everything here was observed by running it; nothing is inferred.

## Observed results

| What | Environment | Result |
| --- | --- | --- |
| **CI, current code** | GitHub Actions run 10 on commit `61ac2f5`: `ubuntu-latest`, Temurin 17, Testcontainers `postgres:16-alpine`, `./mvnw -B verify` ([run](https://github.com/atreyamitra/ledger-guard/actions/runs/36801224506)) | **63 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS** (read from the job log) |
| Local full suite | `mvn -B verify`, JDK 21, PostgreSQL 16.14 local server (no Docker in that sandbox), fresh database | 63/63, BUILD SUCCESS |
| Packaged app + `scripts/demo.py` | `java -jar` against PostgreSQL 16; request signed with timestamp + key | Same response twice, 1 entry, balance 1000, reconcile `ok:true` |
| Startup without a secret | `java -jar`, `WEBHOOK_SECRET` unset | Refuses to start with an explanatory error |

History: run 9 (commit `677e133`, before the replay fix) passed 45/45 on GitHub-hosted CI; the original 23-test runs are on `main`. Those figures are superseded by run 10.

The CI run above executed the code of commit `61ac2f5`; a later commit changes only documentation files.

## Mutation checks (manual, temporary, reverted)

To confirm the concurrency tests detect the bugs they claim to:

| Deliberate bug | Tests that failed |
| --- | --- |
| Account lock removed (`findById` instead of `FOR UPDATE`) | `differentKeysOnSameAccountDoNotLoseCredits`, `tenKeysWithFiveDuplicatesEachCreditExactlyTenTimes`, `reconciliationNeverSeesDriftWhileCreditsAreInFlight` |
| Duplicate recovery disabled (unique violation escapes) | `twentyParallel...`, `competingBodies...`, `tenKeys...`, `reconciliation...`, `MultiInstanceTest` |
| Timestamp freshness check disabled | stale, future-dated and window-boundary tests (3 failures) |

Note what this says: the classic "20 identical requests" test does **not** detect a missing account lock, because the claim row already serialises identical keys. The lock is covered by the different-keys tests.

## What the concurrency tests prove — and do not

Prove: under 20–50 simultaneous HTTP requests against real PostgreSQL (`READ COMMITTED`), including requests split across two independent app contexts with separate connection pools, a key is credited once, different keys are not lost, failed attempts leave no claim, and reconciliation never observes a half-applied credit.

Do **not** prove: behaviour across separate machines/networks, database failover, a crash between statements, other isolation levels, high load, or absence of every possible interleaving. Thread timing is not controlled, so the loser-side unique-violation path is exercised probabilistically in the HTTP tests; it is covered **deterministically** by `DatabaseConstraintsTest.uniqueClaimIsTheArbiter...`.

## Replay protection: what is and is not shown

Shown: a request signed over `"v1\n" + timestamp + "\n" + key + "\n" + body` is refused if the body, key or timestamp is changed, if the timestamp is malformed, stale or future-dated (default window 300 s), and a fixed known-answer vector computed by Python's `hmac` matches. Replaying the identical request inside the window returns the stored response with no second credit.
Not shown: behaviour with skewed sender clocks, secret rotation, TLS, or secret strength (the 16-character check is a sanity floor, not an entropy measure).

## Reproduce

```sh
./mvnw -B verify                                   # Docker required (Testcontainers)
TEST_DB_URL=jdbc:postgresql://host:5432/db TEST_DB_USER=u TEST_DB_PASSWORD=p ./mvnw -B test   # disposable DB, no Docker
```
