# 3. Assumptions

## In short

The brief leaves some questions open. Instead of guessing silently, we wrote
down every assumption, why we made it, and what would change if it turned out to
be wrong.

## Main assumptions

### A1. One running copy is enough, but two must not break anything

- **Why:** 125 e-mails a week is very little work.
- **What it means:** we run one instance, but the code that picks up waiting
  notifications is safe if a second one starts (e.g. during a deployment).
  Two copies never send the same notification.
- **If wrong:** nothing to change; we can simply run more copies.

### A2. Audit history is stored centrally, not in this service

- **Why:** every Qualifyze service has the same ISO 27001 / SOC 2 duty to keep
  history. One shared, tamper-proof archive is safer and cheaper than each
  service building its own.
- **What it means:** this service sends every change as an *audit event* to a
  message topic (AWS SNS). The topic delivers each event to an **SQS queue**
  owned by the central audit service. The audit service reads the queue and
  stores the events in S3 with Object Lock, where they cannot be changed or
  deleted. The path is **SNS → SQS → audit service → S3**. This service only
  guarantees that **no event is lost before it reaches SNS**; the queue
  keeps events safe while the audit service is busy or down.
- **Order:** to keep events in order end to end, the topic and the queue are
  both FIFO (`.fifo`). The service already sets the message group and a
  deduplication ID (the event ID) when the topic name ends in `.fifo`.
- **If wrong:** if no central archive exists, we add a consumer that writes the
  events to S3; the service itself does not change.

### A3. The list of document types can grow

- **Why:** the detection team may start tracking new document types.
- **What it means:** we accept any document type code. Known codes get a nice
  label ("warning letter"); unknown ones are turned into readable text
  (`NEW_TYPE` → "new type"). Labels and translations belong to this service.
- **If wrong:** if the list is fixed, we could reject unknown codes instead.

### A4. E-mail is the only channel for now

- **Why:** the brief asks for e-mail and says other channels will come later.
- **What it means:** e-mail is fully working. **Slack** is already a valid
  choice in a distribution list, to show how a channel is added, but it has no
  sender yet, so deliveries to Slack fail with a clear reason. A second message
  type, **certificate expiry**, works end to end to show that new kinds of
  message are easy to add.

### A5. Only internal services call the API

- **Why:** the caller is an existing internal process.
- **What it means:** the service runs inside a *service mesh* that encrypts
  traffic and checks the identity of the calling service (mutual TLS).
- **Notification ingestion:** only the internal detection process calls
  `POST /api/v1/notifications`. Being reachable only inside the mesh is enough;
  the service does no authentication or authorisation of its own.
- **Distribution lists:** people never call
  `PUT /api/v1/customers/{customerReference}/distribution-list` directly. A
  **BFF** (backend for frontend) in front of this service logs users in and
  checks that they may edit that customer's list, then passes the logged-in
  user in the `X-Actor` header. We record it as the actor of each change. This
  service only trusts the BFF, through the mesh.
- **If wrong:** if the API is exposed outside, we add an API gateway with
  OAuth2 in front of it. If there is no BFF, this service must check per-user
  permissions on distribution lists itself.

### A6. A customer may very rarely get the same e-mail twice

- **Why:** a mail server can accept a message and the service can crash before
  writing down that it was sent. No system can fully avoid this without the mail
  server's help.
- **What it means:** we remember every successful delivery and never resend it.
  Only that one crash window can cause a duplicate. We prefer one rare duplicate
  to a missed warning.

### A7. A customer without a distribution list is skipped, not an error

- **Why:** a new customer may not have set up their list yet. That is a
  configuration gap, not a failure of the notification.
- **What it means:** we skip that customer, log a warning and count it in a
  metric, and still notify everyone else. We **never invent an address**.

### A8. A new notification always gets a new idempotency key

- **Why:** the key is how we recognise a repeated request.
- **What it means:** if the same key arrives twice, we return the first result
  and do not send again. We do not compare the content; reusing a key for a
  different event is a caller mistake.

### A9. The audit "outbox" will become a shared library

- **Why:** other services need the same "record and forward audit events" logic.
- **What it means:** the code is kept self-contained so it can later move into a
  shared Spring Boot library. Ready-made tools (Debezium, Spring Modulith) are
  options we will compare then.

### A10. Everything runs in AWS

- **Why:** Qualifyze runs its services on AWS.
- **What it means:**
  - The service and the services that call it (detection process, BFF) run
    as pods in the same **Amazon EKS** cluster, inside the service mesh.
  - The database is **Amazon Aurora PostgreSQL**. Locally and in tests we use
    plain PostgreSQL 18 in Docker; the service uses only standard PostgreSQL
    features, so both behave the same.
  - The audit path (SNS, SQS, the audit service and S3) is in AWS too.
  - Only the **mail server** is outside AWS.
  - The pod gets AWS access through an IAM role tied to its Kubernetes
    service account, never through stored keys.
- **If wrong:** the code does not change. Only the deployment and the
  [threat assessment](09-security-threat-assessment.md) need updating.

## Smaller assumptions

| # | Assumption |
|---|---|
| S1 | "Current processing cycle" (FR.1.4) means: only notifications that have not been sent yet are processed. Nothing old is ever re-sent. |
| S2 | The caller decides which customers are affected. This service does not look up site-to-customer links. |
| S3 | Messages are in English and dates are written as "12 June 2026". The text lives in a translation file so other languages can be added. |
| S4 | The confidence level is shown to the customer as-is; we do not filter out low-confidence matches. |
| S5 | Audit history must be kept for 7 years; the central archive enforces this. |
| S6 | A notification is **completed** when every reachable target received it; if any target failed for good, it is **failed** so someone can look at it. |
| S7 | Customer references are short IDs (at most 64 characters) and one notification affects at most 50 customers (the brief says 10–25). |
