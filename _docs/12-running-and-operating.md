# 12. Running and operating

## In short

- Locally, one `docker compose` command starts everything the service needs:
  PostgreSQL 18, a test mail server with a web inbox, and a fake AWS.
- Every setting can be changed with an environment variable.
- The runbook at the end says what to do when something goes wrong.

## Run it locally

**You need:** Java 25 and Docker.

```bash
# 1. Start the dependencies
docker compose -f _dev/docker-compose.yml down -v
docker compose -f _dev/docker-compose.yml up -d
```
```bash
# 2. Build (runs all tests)
./mvnw verify
```
```bash
# 3. Start the service
java -jar notification-boot/target/notification-boot-0.0.1-SNAPSHOT.jar
```

| What | Where |
|---|---|
| Service API | http://localhost:8080/api/v1 |
| Health check | http://localhost:8080/actuator/health |
| Metrics | http://localhost:8080/actuator/metrics |
| Mail inbox (Mailpit) | http://localhost:8025 |
| PostgreSQL | `localhost:5433`, database `notification`, user and password `postgres` |
| Fake AWS (LocalStack) | http://localhost:4566 (SNS topic `notification-audit-events`, plus a queue to read events) |

```mermaid
flowchart LR
    APP[Notification Service :8080] --> PG[(PostgreSQL 18 :5433)]
    APP --> MP[Mailpit SMTP :1025<br/>web inbox :8025]
    APP --> LS[LocalStack SNS/SQS :4566]
```

### Try it

```bash
# 4. Give a customer a distribution list
curl -X PUT localhost:8080/api/v1/customers/CUST-1/distribution-list \
  -H 'Content-Type: application/json' -H 'X-Actor: you@qualifyze.com' \
  -d '{"targets":[{"deliveryChannel":"EMAIL","notificationTarget":"qa@customer.example"}]}'
```
```bash
# 5. Send a notification
curl -X POST localhost:8080/api/v1/notifications \
  -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"try-1","messageType":"REGULATORY_EVENT",
       "siteName":"Qualifyze Spain","siteAddress":"Passeig de Gracia 19 Barcelona",
       "authorityName":"FDA","documentTypeCode":"WARNING_LETTER",
       "issueDate":"2026-06-12","confidenceLevel":80,
       "affectedCustomerReferences":["CUST-1"]}'
```
```bash
# 6. Open email client to see the e-mail
open "http://localhost:8025"
```
```bash
# 7. Read the audit events that reached "AWS"
aws --endpoint-url http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/notification-audit-events \
  --max-number-of-messages 10
```

### Start from a clean database

```bash
docker compose -f _dev/docker-compose.yml down -v   # -v also deletes the data
```

## Configuration

Every value has a sensible default for local use. In other environments, set the
environment variable.

### Database

| Variable | Default |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/notification` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `postgres` / `postgres` |

In AWS the URL points to the Aurora PostgreSQL cluster (writer) endpoint and
must turn on certificate checking, for example
`jdbc:postgresql://<cluster-endpoint>:5432/notification?sslmode=verify-full`.
The username and password come from AWS Secrets Manager, never the defaults.

### E-mail

| Variable | Default | Meaning |
|---|---|---|
| `NOTIFICATION_CHANNEL_EMAIL_HOST` / `_PORT` | `localhost` / `1025` | SMTP server |
| `NOTIFICATION_CHANNEL_EMAIL_FROM` | `no-reply@qualifyze.local` | Sender address |
| `NOTIFICATION_CHANNEL_EMAIL_USERNAME` / `_PASSWORD` | empty | SMTP login |
| `NOTIFICATION_CHANNEL_EMAIL_AUTH` / `_STARTTLS` | `false` / `false` | Turn on in production |
| `NOTIFICATION_CHANNEL_EMAIL_TIMEOUT` | `10s` | Time limit for connecting, reading and writing |

### Sending

| Variable | Default | Meaning |
|---|---|---|
| `NOTIFICATION_INBOX_WORKER_DELAY_MS` | `1000` | How often to look for waiting notifications |
| `NOTIFICATION_INBOX_WORKER_BATCH_SIZE` | `50` | How many to pick up at once |
| `NOTIFICATION_INBOX_WORKER_LEASE` | `PT5M` | After this, a stuck notification is picked up again |
| `NOTIFICATION_INBOX_WORKER_MAX_CLAIMS` | `5` | After this many pick-ups, give up and mark `FAILED` |
| `NOTIFICATION_DISPATCH_RETRY_MAX_ATTEMPTS` | `3` | Tries per target |
| `NOTIFICATION_DISPATCH_RETRY_INITIAL_BACKOFF_MS` | `200` | First pause between tries (then doubles) |

The lease must be longer than the slowest possible batch. With the defaults, one
target takes at most 3 tries × (10 s to connect + 10 s per read or write) plus
pauses, about 1–2 minutes, well below 5 minutes.

### Audit

| Variable | Default | Meaning |
|---|---|---|
| `NOTIFICATION_AUDIT_BROKER` | `sns` | `sns`, or `logging` to just print events |
| `NOTIFICATION_AUDIT_SNS_TOPIC_ARN` | LocalStack topic | Where to publish |
| `NOTIFICATION_AUDIT_AWS_REGION` | `us-east-1` | |
| `NOTIFICATION_AUDIT_AWS_ENDPOINT` | `http://localhost:4566` | Leave **empty** in AWS |
| `NOTIFICATION_AUDIT_AWS_ACCESS_KEY` / `_SECRET_KEY` | `test` / `test` | Leave **empty** in AWS: the service then uses its IAM role |
| `NOTIFICATION_AUDIT_OUTBOX_WORKER_DELAY_MS` | `1000` | How often to forward audit events |
| `NOTIFICATION_AUDIT_OUTBOX_RETENTION` | `P7D` | Keep forwarded events this long |

### Metrics

To get percentiles (p95) in Prometheus, turn on the histogram:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.distribution.percentiles-histogram.notification.dispatch.latency=true
```

## Production checklist

The service runs in Amazon EKS with Aurora PostgreSQL (see
[Assumption A10](03-assumptions.md#a10-everything-runs-in-aws)). Each item
below is a control from the [threat assessment](09-security-threat-assessment.md).

- [ ] Database, SMTP and AWS settings come from AWS Secrets Manager.
- [ ] AWS keys and endpoint are **empty**, so the pod's IAM role (IRSA or EKS
      Pod Identity) is used. The role may only `sns:Publish` to the audit
      topic.
- [ ] Pods cannot use the EKS node's IAM role: IMDSv2 is required with a hop
      limit of 1.
- [ ] The SNS topic and the audit SQS queue are FIFO, encrypted with KMS, and
      reached through VPC endpoints.
- [ ] Aurora: encrypted with KMS, not public, its security group only accepts
      this service's pods, TLS with `sslmode=verify-full`, its own database
      user (not the master user), snapshots encrypted and access limited.
- [ ] SMTP uses authentication and STARTTLS, and the connection fails if
      STARTTLS is not available. This needs `mail.smtp.starttls.required`,
      which the service does not set yet (see the
      [threat assessment](09-security-threat-assessment.md#t10-e-mail-read-or-changed-on-the-way)).
- [ ] The service mesh enforces strict mTLS, an allow list per path
      (detection process → `POST /notifications`, BFF →
      `PUT /customers/.../distribution-list`, monitoring → `/actuator`), and
      `SANITIZE_SET` for the client certificate header.
- [ ] Kubernetes NetworkPolicies only allow traffic through the mesh.
- [ ] The demo seed data does not run (Liquibase context `local` only).
- [ ] The alerts in [Metrics](10-metrics.md#alerts) are in place.

## Runbook

### A notification is `FAILED`

1. Find out why:
   ```sql
   SELECT customer_reference, notification_target, attempt_number, outcome, error_details, dispatched_at
   FROM   dispatch_ledger
   WHERE  idempotency_key = '<key>'
   ORDER  BY dispatched_at;
   ```
2. Fix the cause (wrong address → update the distribution list; SMTP login →
   fix the configuration).
3. Ask the caller to send the notification again with a **new** idempotency key.

### A customer was skipped

The `notification.customers.skipped` alert fired. The customer has no
distribution list. Ask the account owner for the addresses and set them with
`PUT /api/v1/customers/{customerReference}/distribution-list`.

### A notification stays in `DISPATCHING`

It should be picked up again within 5 minutes. If not:

```sql
SELECT event_reference, claimed_at, claim_count
FROM   notification_inbox
WHERE  status = 'DISPATCHING' AND claimed_at < now() - interval '10 minutes';
```

- Check the service is running and the scheduler is enabled
  (`notification.inbox.worker.enabled`).
- If `claim_count` keeps rising, the notification crashes the service; it will
  be marked `FAILED` after 5 tries. Check the logs for that `event_reference`.

### The audit backlog grows

The "audit backlog" alert fired: events are not reaching SNS.

1. Check the logs for SNS errors (permissions, wrong topic, network).
2. Fix the cause. **No data is lost**: events wait in `audit_outbox` and are sent
   in order once SNS is reachable.
3. Watch `notification.audit.outbox.depth` go back to zero.

If our outbox is empty but events are missing in the archive, the problem is
after SNS: ask the audit team to check their SQS queue and its dead-letter
queue.

### The mail server is down

Deliveries fail after 3 tries and notifications become `FAILED`. Once the server
is back, resend the affected notifications with new idempotency keys (see the
first runbook entry).
