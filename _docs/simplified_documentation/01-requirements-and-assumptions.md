# 1. Requirements and assumptions

## How each requirement is met

| Req. | The brief asks | How we meet it |
|---|---|---|
| **FR.1.1** | The detection process calls a REST API | `POST /api/v1/notifications` stores the event and answers `202 Accepted`. Sending happens in the background. |
| **FR.1.2** | One message per distribution list, from the template | Each customer has a list of addresses. The text is built from a template that matches the brief's sample exactly. |
| **FR.1.3** | Include the match confidence | Shown at the end, e.g. `[80%]`. |
| **FR.1.4** | Only events from the current cycle | Only notifications not yet sent are picked up. A finished one is never sent again. |
| **FR.1.5** | 1–5 events, 10–25 customers a week | One instance is plenty. The design aims for correctness, not speed. |
| **FR.1.6** | Store every change, send and error | Every delivery attempt goes to a **dispatch ledger**. Every change also becomes an **audit event** forwarded to a tamper-proof archive. |
| **FR.1.7** | Other channels and other events later | Channels are plug-in modules. Message types are a closed list the compiler checks. Both are demonstrated: a Slack placeholder and a working *certificate expiry* message. |

## Assumptions

The brief was the only input, so every open question was answered with an
explicit assumption.

| # | Assumption | Why | If wrong |
|---|---|---|---|
| A1 | One instance runs, but two must not break anything (e.g. during a deployment). | ~125 e-mails a week. | Nothing changes; claiming is already safe for several instances. |
| A2 | Audit history is stored **centrally**. This service publishes audit events to SNS; the audit service's SQS queue receives them and the audit service stores them in S3 with Object Lock. We only guarantee nothing is lost before SNS. | Every Qualifyze service has the same ISO 27001 / SOC 2 duty; one tamper-proof archive is safer than one per service. | Add a small consumer that writes to S3; this service does not change. |
| A3 | Only internal services call the API, inside a service mesh with mutual TLS. A **BFF** logs users in, checks they may edit a customer's list, and passes the user in `X-Actor`. | The caller is an existing internal process. | Add an API gateway with OAuth2; check per-user permissions here. |
| A4 | The set of document types can grow. Unknown codes are accepted and turned into readable text (`IMPORT_ALERT` → "import alert"). | The detection team may track new types. | Reject unknown codes instead. |
| A5 | E-mail is the only working channel. Slack is accepted in lists but fails with a clear reason. | The brief says other channels come later. | Add an `adapter-slack` module. |
| A6 | A customer without a distribution list is **skipped** with a warning and a metric, not an error. We never invent an address. | A new customer may not be set up yet. | Make it fail the notification. |
| A7 | A new notification always has a new idempotency key. A repeated key returns the first result; the content is not compared. | The key is how retries are recognised. | Compare content and answer `409`. |
| A8 | A customer may very rarely get the same e-mail twice: if the service crashes after the mail server accepts a message but before we record it. | No system can avoid this without the mail server's help. A duplicate beats a missed warning. | — |
| A9 | Everything runs in AWS: EKS, Aurora PostgreSQL, SNS/SQS/S3. Only the mail server is outside. | Qualifyze runs on AWS. | Only deployment and the threat assessment change. |
| A10 | Messages are in English, dates as "12 June 2026". Texts live in a translation file. | The brief's sample. | Add a language file. |

## Acceptance criteria

Every criterion has at least one automated test (see [Testing](05-testing.md)).

### Happy paths

| # | Given / When | Then |
|---|---|---|
| AC-1 | A valid regulatory event with a new idempotency key is posted | `202`, a new `eventReference`, status `RECEIVED`, `duplicate = false`; stored once; a `notification.received` audit event. |
| AC-2 | Customers A (2 addresses) and B (1 address) are affected | Exactly 3 e-mails, each reading exactly: `Site Qualifyze Spain, located at Passeig de Gracia 19 Barcelona received a FDA warning letter on 12 June 2026. [80%]` |
| AC-3 | The 3 e-mails were sent | 3 `DELIVERED` ledger rows (recipient, channel, time, idempotency key) and 3 matching audit events. |
| AC-4 | All targets were delivered | Status `COMPLETED`; each change `RECEIVED → DISPATCHING → COMPLETED` is an audit event. |
| AC-5 | A's list `x, y` is replaced with `y, z` | `200` with the new list; exactly two audit events (`x` removed, `z` added) with the `X-Actor` user; `y` untouched. |
| AC-6 | An event was processed for two customers | "events processed" +1, "customers notified" +2, every attempt timed. |

### Edge cases

| # | Given / When | Then |
|---|---|---|
| AC-7 | The same idempotency key arrives again, even at the same moment | `202`, `duplicate = true`, the original `eventReference`; nothing stored or sent. |
| AC-8 | A field is missing, too long, or confidence is 120 | `400` listing each problem; nothing stored. |
| AC-9 | The mail server fails once, then works | Sent on the second try; ledger shows one `FAILED` and one `DELIVERED` attempt. |
| AC-10 | The mail server rejects our login or the address | Not retried; recorded `FAILED` with the reason; other targets still delivered; notification ends `FAILED`. |
| AC-11 | A notification ended `FAILED` | Not retried automatically; the ledger says which targets failed and why. |
| AC-12 | The service crashes while sending | After the 5-minute lease it is picked up again; already-delivered targets are skipped; after 5 claims it is marked `FAILED`. |
| AC-13 | Two instances run | Each notification is picked up by exactly one. |
| AC-14 | Customer C has no list | A is notified; C is skipped, logged and counted; the notification can still be `COMPLETED`. |
| AC-15 | `documentTypeCode = "IMPORT_ALERT"` | Accepted; the text reads "… received a FDA import alert on …". |
| AC-16 | A list contains a Slack target | Accepted; deliveries to it are `FAILED` ("No sender configured for channel: SLACK"). |
| AC-17 | SNS is down | Notifications are still received and sent; audit events wait in the outbox and are forwarded in order later; backlog metrics show it. |
| AC-18 | The database is down | `503` with a generic message, so the caller retries. |
