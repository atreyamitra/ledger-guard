# Handoff — ledger-guard

## Checkpoint
Phase E: CI, documentation and verification evidence

## Done
Phases A–E implemented and committed. All requested endpoints, Flyway schema, five required integration test classes, additional race/rollback tests, CI, signed demo and documentation are present. Twelve offline cryptographic checks passed. Two resume bullets cover only verified crypto behavior. Full integration verification and public remote publication remain pending.

## Not done / next work
- [x] Phase A: scaffold and domain model
- [x] Phase B: HMAC and happy path
- [x] Phase C: persisted idempotency and tests
- [x] Phase D: concurrency and reconciliation implementation/tests
- [x] Phase E: CI and final documentation
- [ ] Run ./mvnw -B test on a Docker-enabled host with Maven Central access; resolve any compile or integration failures
- [ ] Run scripts/demo.py against the local application
- [ ] Publish to a user-owned public GitHub repository and inspect the CI run (no remote created here)
- [ ] Upgrade resume claims only from observed passing integration tests

## Exact next command
```sh
cd /workspace/scratch/aaa2824b67be/ledger-guard && ./mvnw -B test
```
On another machine, replace the absolute directory with the checkout path.

## Run tests
Java 17, Docker with a running daemon and access to Maven Central / Docker Hub are required.
`./mvnw -B test` (Windows: `mvnw.cmd -B test`). Tests must fail if Docker is unavailable, never silently skip.

## Known failures / evidence
Maven and Docker are not installed here. Java 17 has the compiler module but no javac launcher. Initial Maven Central download did not complete and network approval was cancelled. Production crypto compiled and all 12 offline checks passed; 27 Java sources passed syntax-only parsing. Full dependency/type compilation and integration tests have not run. GitHub Actions is defined but has not run. No public remote repository has been created. See VERIFICATION.md.

## Decisions
Java 17, Spring Boot 3.3.13, PostgreSQL, Flyway, JPA, Maven. Amounts are long paise; INR only.
Unique insert-only claim is flushed before locking the account. The claim, entry, balance and response commit in one transaction. Duplicate recovery is outside the failed transaction. Reconciliation is a single-snapshot SQL aggregate. Ledger mutation is blocked by PostgreSQL triggers. The Maven scripts are documented lightweight distribution launchers.
No user-provided handoff template followed the template heading, so this file supplies the required six handoff sections.

## File tree
```text
.github/workflows/ci.yml
.gitignore
.mvn/wrapper/maven-wrapper.properties
.mvn/wrapper/mvnw.ps1
HANDOFF.md
README.md
RESUME_BULLETS.md
STATUS.md
TODO.md
VERIFICATION.md
mvnw
mvnw.cmd
pom.xml
scripts/OfflineCryptoCheck.java
scripts/check-offline.sh
scripts/demo.py
src/main/java/com/atreyamitra/ledgerguard/LedgerGuardApplication.java
src/main/java/com/atreyamitra/ledgerguard/api/AccountController.java
src/main/java/com/atreyamitra/ledgerguard/api/ApiErrors.java
src/main/java/com/atreyamitra/ledgerguard/api/ApiException.java
src/main/java/com/atreyamitra/ledgerguard/api/ApiModels.java
src/main/java/com/atreyamitra/ledgerguard/api/ReconciliationController.java
src/main/java/com/atreyamitra/ledgerguard/api/WebhookController.java
src/main/java/com/atreyamitra/ledgerguard/crypto/WebhookCrypto.java
src/main/java/com/atreyamitra/ledgerguard/domain/Account.java
src/main/java/com/atreyamitra/ledgerguard/domain/LedgerEntry.java
src/main/java/com/atreyamitra/ledgerguard/domain/ProcessedWebhook.java
src/main/java/com/atreyamitra/ledgerguard/repository/AccountRepository.java
src/main/java/com/atreyamitra/ledgerguard/repository/LedgerEntryRepository.java
src/main/java/com/atreyamitra/ledgerguard/repository/ProcessedWebhookRepository.java
src/main/java/com/atreyamitra/ledgerguard/repository/WebhookClaimRepository.java
src/main/java/com/atreyamitra/ledgerguard/service/AccountService.java
src/main/java/com/atreyamitra/ledgerguard/service/PaymentWriter.java
src/main/java/com/atreyamitra/ledgerguard/service/ReconciliationService.java
src/main/java/com/atreyamitra/ledgerguard/service/WebhookService.java
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_ledger.sql
src/test/java/com/atreyamitra/ledgerguard/ConcurrencyTest.java
src/test/java/com/atreyamitra/ledgerguard/IdempotencyTest.java
src/test/java/com/atreyamitra/ledgerguard/PostgresIntegrationTest.java
src/test/java/com/atreyamitra/ledgerguard/ReconciliationTest.java
src/test/java/com/atreyamitra/ledgerguard/ValidationTest.java
src/test/java/com/atreyamitra/ledgerguard/WebhookCryptoUnitTest.java
src/test/java/com/atreyamitra/ledgerguard/WebhookHmacTest.java
src/test/resources/junit-platform.properties
```
