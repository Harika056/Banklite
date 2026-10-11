# BankLite

A small banking REST service that keeps money transfers correct when many requests hit the same accounts at once.

**Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, PostgreSQL, Redis, JUnit 5, Mockito, JaCoCo, Docker, GitHub Actions

## What it does
- Create accounts, check balances, move money between accounts, view an account ledger.
- **Optimistic locking** (`@Version` on `Account`): two transfers that touch the same account at the same time cannot both overwrite each other. The loser is retried automatically (up to 5 attempts with backoff).
- **Idempotent transfers**: send an `Idempotency-Key` header and a repeated request returns the original result instead of moving money twice. The same key with a different payload returns `409`.
- **Redis read cache** for account balances (60 s TTL), evicted after every committed transfer. If Redis is down, reads fall back to PostgreSQL.
- Each transfer is one database transaction, so a failure never leaves half a transfer behind.

## Design notes
- `TransferService` holds the transaction. `TransferFacade` is deliberately *not* transactional: it retries `TransferService` so each attempt starts in a fresh transaction with fresh versions.
- Amounts use `BigDecimal` with 2 decimal places. Floating point is never used for money.
- A unique constraint on the idempotency key covers two simultaneous requests with the same new key.

## API
| Method | Path | Notes |
|---|---|---|
| POST | `/api/accounts` | `{"ownerName":"Asha","initialDeposit":500.00}` |
| GET | `/api/accounts/{id}` | cached in Redis |
| GET | `/api/accounts/{id}/ledger` | transfers in or out |
| POST | `/api/transfers` | `{"fromAccountId":"...","toAccountId":"...","amount":25.00}`, optional `Idempotency-Key` header |

Swagger UI: http://localhost:8080/swagger-ui.html

## Run it
```bash
docker compose up --build
```
Then open http://localhost:8080/swagger-ui.html.

## Run the tests
```bash
mvn verify
```
Tests use an in-memory H2 database and need no Redis. The coverage report is in `target/site/jacoco/index.html`.

## Concurrency check
```bash
python loadtest/concurrency_check.py http://localhost:8080 500 50
```
Creates two accounts, sends 500 concurrent transfers of 1.00, and checks that no money was created or lost.

JMeter alternative (after creating two accounts and noting their ids):
```bash
jmeter -n -t loadtest/banklite.jmx -Jfrom=<ACCOUNT_A_ID> -Jto=<ACCOUNT_B_ID> -Jthreads=500 -l loadtest/results/run.jtl
```

## Results
Measured on my machine (Intel Core i5-10300H, 8 GB RAM).

| Measure | Value |
|---|---|
| Tests passing | 67, 0 failures |
| Line coverage (JaCoCo) | 88% |
| Branch coverage (JaCoCo) | 78% ||

## Possible next steps
Database migrations with Flyway, authentication, and a dead-letter store for transfers that exhaust their retries.
