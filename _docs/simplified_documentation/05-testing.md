# 5. Testing

One command runs everything:

```bash
./mvnw verify
```

GitHub Actions runs the same `verify` on every push to `main` and every pull
request.

## Layers

| Layer | Proves | Tools | Examples |
|---|---|---|---|
| **Unit** | Rules and decisions | JUnit, Mockito | Message text matches the brief exactly · confidence 0–100 · no retry on permanent errors · list diff finds added and removed targets |
| **Integration** | Our code works with real infrastructure | Testcontainers (PostgreSQL 18), GreenMail, LocalStack | Liquibase runs on an empty DB · one row per idempotency key under concurrency · two workers never claim the same row · expired leases are reclaimed · audit events reach SNS in order |
| **API contract** | The HTTP API behaves as documented | Real HTTP calls | `400` with details · `202` with `duplicate = true` · limits pass at the maximum and fail one above · `500`/`503` show no internals |
| **End-to-end** | The whole feature | All of the above | `POST` a warning letter → exact e-mail text → ledger row → audit event read from the SQS queue |

Every acceptance criterion in
[Requirements](01-requirements-and-assumptions.md#acceptance-criteria) has at
least one test. Tests assert **exact** outcomes (the exact e-mail text, the
exact audit events), not only "no exception".

## Test data for a clean environment

Only Docker is needed.

- Each run starts a fresh PostgreSQL 18 container; Liquibase creates the schema
  from scratch.
- `002-seed-initial-data.sql` adds one demo list (`demo-customer`). Every other
  piece of data is created by the test itself, so each test stands alone.
- GreenMail is an in-test SMTP server whose received e-mails we read.
- LocalStack provides SNS and an SQS queue subscribed to it.
- Retry pauses use an injectable sleeper, so tests do not wait for real.

## Coverage

JaCoCo merges unit and integration coverage of all modules into one report
(`notification-boot/target/site/jacoco-aggregate/index.html`). The build
**fails below 90 % line coverage**. Code generated from the OpenAPI file is
excluded.
