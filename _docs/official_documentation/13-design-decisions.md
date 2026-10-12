# 13. Design decisions

## In short

The main choices, each with the problem it solves, the alternatives we looked at,
and what we accepted in return. The overall direction: **keep the design clean
and easy to extend, but do not build machinery the problem does not need.**

| # | Decision | In one line |
|---|---|---|
| D1 | Hexagonal modules | The compiler keeps business rules separate from technology. |
| D2 | Accept now, send later (inbox) | Callers never wait for or fail because of e-mail. |
| D3 | Audit through an outbox to a central archive | No audit record can be lost, and nobody can rewrite history. |
| D4 | Keep a dispatch ledger as well | Fast answers to "who got what" and no duplicate e-mails. |
| D5 | Sealed types for message types, plug-ins for channels | The compiler checks the closed list; the open list stays open. |
| D6 | Store the notification content as JSON | New message types need no database change. |
| D7 | Lease-based claiming | Safe with several instances and recovers from crashes by itself. |
| D8 | A simple retry loop | One retry rule does not need a library. |
| D9 | Idempotency key without content check | Simple, predictable, and enough for trusted callers. |
| D10 | Contract-first API | Docs and code cannot drift apart. |
| D11 | Translatable message texts | Wording and languages change without code changes. |

---

### D1. Hexagonal architecture in separate modules

- **Problem:** business rules mixed with web, database and mail code are hard to
  test and hard to change.
- **Decision:** business core in the middle; every technology is an adapter
  behind an interface; each adapter is its own Maven module.
- **Alternatives:** one module with packages (simpler, but nothing stops someone
  from importing the database in a business class); a classic layered app.
- **Trade-off:** ten modules is more structure than ~3,000 lines strictly need.
  We accept it because the extension points the brief asks for (channels,
  message types, audit transport) map one-to-one onto modules, and the compiler
  enforces the boundaries.

### D2. Accept now, send later

- **Problem:** sending to 25 customers, with retries, can take seconds or fail.
  The caller should not wait or see mail problems.
- **Decision:** store the notification, answer `202 Accepted`, and let a
  background job send it.
- **Alternatives:** send during the request (simple but fragile); a message
  broker such as Kafka or SQS (too much for 5 events a week).
- **Trade-off:** the caller does not get the delivery result in the response; it
  is in the ledger and the audit trail.

### D3. Audit through a transactional outbox to a central archive

- **Problem:** FR.1.6 needs every change stored for ISO 27001 / SOC 2. The
  record must never be lost and must be impossible to alter.
- **Decision:** write each audit event to an outbox table in the same transaction
  as the change; a relay forwards it to SNS; SNS delivers it to the audit
  service's SQS queue; the central audit service stores it in S3 with Object
  Lock.
- **Alternatives:**
  - An audit table in our own database: easy, but we could edit our own history.
  - Sending straight to SNS: an SNS outage would mean losing events or failing
    changes.
  - Debezium (reads the database log) or Spring Modulith (event publishing):
    good options when several services share this; to compare later.
- **Trade-off:** the archive copy arrives a second later, not instantly. Sequence
  numbers make any gap visible.

### D4. A dispatch ledger next to the audit trail

- **Problem:** the dispatcher must know "was this target already reached?" to
  avoid duplicates after a crash, and operators need quick answers.
- **Decision:** keep a permanent `dispatch_ledger` table in our database, and
  also send each attempt as an audit event.
- **Alternative:** query the archive (too slow and not built for that).
- **Trade-off:** the same facts in two places, but each has one clear job:
  running the service vs proving what happened.

### D5. Sealed types for message types, plug-ins for channels

- **Problem:** FR.1.7 asks for new channels and new notification types.
- **Decision:**
  - **Message types** are a closed list owned by us. They are a *sealed* family
    of records handled by `switch` statements that must cover every type. Add a
    type and the build fails until every place handles it.
  - **Channels** are an open list, each with its own library. They are plug-ins
    (`NotificationSenderPort`), one module each.
- **Alternative:** plug-in registries for both (a class per message type
  registered at start-up). More flexible, but a forgotten registration only
  shows up at runtime.
- **Trade-off:** adding a message type touches a few files instead of one,
  and the compiler lists them.

### D6. Notification content stored as JSON

- **Problem:** each message type has different fields.
- **Decision:** one `notification_inbox` table; the content goes in a `jsonb`
  column with a type marker.
- **Alternatives:** one table per type (a migration and a new entity for every
  type); one wide table with many empty columns.
- **Trade-off:** the database does not check the fields inside the JSON. The API
  contract and the domain classes do. We never search inside the content, so
  nothing is lost.

### D7. Lease-based claiming

- **Problem:** if the service crashes while sending, the notification must not be
  stuck forever; and two instances must not send the same notification.
- **Decision:** one SQL statement claims waiting rows with
  `FOR UPDATE SKIP LOCKED` and stamps the time. After 5 minutes, a claim
  expires and the row can be picked up again. More than 5 claims means
  something is wrong, and the row is marked `FAILED`.
- **Alternatives:** optimistic locking with a version number (prevents double
  sending, but cannot recover from a crash); a distributed lock service (extra
  infrastructure).
- **Trade-off:** one SQL statement written by hand instead of JPA. Mail calls
  need time limits so a batch always finishes within the lease.

### D8. A simple retry loop

- **Problem:** short mail failures should be retried; permanent ones should not.
- **Decision:** a small loop: up to 3 tries, a pause that doubles, a little
  randomness, and an immediate stop on permanent errors (bad login, bad address).
- **Alternative:** Resilience4j. Excellent when you need circuit breakers or rate
  limits; here it only added a dependency and more outcome states.
- **Trade-off:** if we later need a circuit breaker, we bring the library back.

### D9. Idempotency key without a content check

- **Problem:** callers retry on timeouts; customers must not get duplicates.
- **Decision:** the caller sends a unique key. If it is seen again, return the
  original result. One SQL statement (`INSERT … ON CONFLICT DO NOTHING`) makes
  this safe even for simultaneous requests.
- **Alternative:** also compare the content and answer `409 Conflict` if it
  differs. Safer against caller bugs, but needs a content fingerprint and a new
  error path.
- **Trade-off:** a caller that reuses a key by mistake gets the old result
  silently. Acceptable for trusted internal callers.

### D10. Contract-first API

- **Problem:** hand-written docs and code drift apart.
- **Decision:** write the OpenAPI file first and generate the Java interfaces and
  request classes from it. Database column sizes follow the contract's limits.
- **Trade-off:** generated classes are a bit clumsy (for example, they are not
  sealed), so the REST mapper needs a `default` branch.

### D11. Translatable message texts

- **Problem:** the wording, the document type labels and the date format will
  change, and other languages may come.
- **Decision:** all texts live in a translation file (`i18n/messages`) read
  through Spring's `MessageSource`. Unknown document types get a readable
  fallback ("IMPORT_ALERT" → "import alert").
- **Next step:** load the files from S3, so texts can change without a release,
  with the built-in file as a backup.
- **Trade-off:** one more module (`adapter-i18n`).

## What we chose **not** to build

| Not built | Why |
|---|---|
| Message broker for incoming requests (Kafka, SQS) | 5 events a week; a database table is enough and simpler to run. |
| Our own audit archive | A shared central archive is safer and serves every service. |
| Authentication in the application | The service mesh already does it, consistently for all services. |
| A Slack sender | Not asked for yet; the extension point is ready and documented. |
| Open and click tracking | Privacy questions; not needed to prove delivery. |
