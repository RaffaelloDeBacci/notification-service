# 2. Requirements and acceptance criteria

## In short

Each requirement from the brief is listed below with how we meet it. After that,
the acceptance criteria describe, in plain *Given / When / Then* form, what must
be true for the feature to count as working.

## How we meet each requirement

| Req. | What the brief asks | How we meet it | Read more |
|---|---|---|---|
| **FR.1.1** | The existing detection process calls a REST API when a warning letter, OAI or non-compliance report is matched to a site. | `POST /api/v1/notifications` with `messageType = REGULATORY_EVENT`. It answers at once with `202 Accepted`; sending happens in the background. | [API](06-api.md) |
| **FR.1.2** | Send a message to each customer's distribution list, using the given template. | Each customer has a distribution list of e-mail addresses. The message is built from a template that matches the brief's sample character for character. | [How it works](05-how-it-works.md) |
| **FR.1.3** | Include the match confidence. | The confidence is shown at the end of the message, e.g. `[80%]`. | — |
| **FR.1.4** | Notify only events from the current processing cycle. | Each notification has a status. Only those not yet sent are picked up; finished ones are never sent again. | [How it works](05-how-it-works.md#the-life-of-a-notification) |
| **FR.1.5** | 1–5 events and 10–25 customers per week. | Easily handled by one instance. The API accepts up to 50 customers per event. | [Assumptions](03-assumptions.md) |
| **FR.1.6** | Store every entity change, every successful send and every error. Track recipient, time, event, status and list changes. | Every delivery attempt is stored in the **dispatch ledger**. Every change (new notification, status change, attempt, list change) also becomes an **audit event** forwarded to a tamper-proof archive. | [Audit & compliance](08-audit-and-compliance.md) |
| **FR.1.7** | Support other channels and other kinds of notifications later. | Channels are plug-ins: a new one is a new module. Message types are a closed list the compiler checks: adding one makes the build fail until every place handles it. Both are demonstrated (Slack placeholder, certificate-expiry message). | [Architecture](04-architecture.md#adding-things-later) |

## Acceptance criteria

### Happy paths

**AC-1. A regulatory event is accepted**

- **Given** a valid regulatory event with a new idempotency key
- **When** the detection process posts it to `/api/v1/notifications`
- **Then** the response is `202 Accepted` with a new `eventReference`,
  status `RECEIVED` and `duplicate = false`
- **And** the notification is stored once
- **And** a `notification.received` audit event records what it contained.

**AC-2. Every target receives the exact message**

- **Given** customers A and B, with two and one e-mail addresses
- **When** the event is processed
- **Then** exactly three e-mails are sent, one per address
- **And** each body reads exactly:
  `Site Qualifyze Spain, located at Passeig de Gracia 19 Barcelona received a FDA warning letter on 12 June 2026. [80%]`

**AC-3. Each delivery is tracked**

- **Given** the three e-mails above were sent
- **Then** the dispatch ledger has three `DELIVERED` rows with recipient,
  channel, time and the event's idempotency key
- **And** three matching audit events are forwarded to the archive.

**AC-4. The notification finishes**

- **Given** all targets were delivered
- **Then** the notification ends as `COMPLETED`
- **And** the status changes `RECEIVED → DISPATCHING → COMPLETED` each produce an
  audit event.

**AC-5. A distribution list is changed**

- **Given** customer A has addresses `x@a.com` and `y@a.com`
- **When** someone sends `PUT /api/v1/customers/A/distribution-list` with
  `y@a.com` and `z@a.com`
- **Then** the response is `200 OK` with the new list
- **And** exactly two audit events are recorded: `x@a.com` removed and `z@a.com`
  added, each with the person from the `X-Actor` header as the actor
- **And** `y@a.com` is not touched.

**AC-6. Metrics are updated**

- **Given** an event was processed for two customers
- **Then** "events processed" goes up by 1, "customers notified" by 2, and the
  delivery-time and attempt metrics record each send.

### Edge cases

**AC-7. The same request is sent twice**

- **Given** a notification was already accepted with key `K`
- **When** a request with key `K` arrives again (even at the same moment)
- **Then** the answer is `202` with `duplicate = true` and the **original**
  `eventReference`
- **And** nothing new is stored and no one is notified again.

**AC-8. The request is invalid**

- **Given** a request with a missing field, a confidence of 120, or a value
  longer than allowed
- **Then** the answer is `400 Bad Request` listing each problem
- **And** nothing is stored.

**AC-9. The mail server has a short hiccup**

- **Given** the mail server fails once and then works
- **Then** the e-mail is sent on the second try, after a short pause
- **And** the ledger shows one `FAILED` and one `DELIVERED` attempt.

**AC-10. An address can never work**

- **Given** the mail server rejects our login or the address is malformed
- **Then** that target is **not** retried and is recorded as `FAILED` with the
  reason
- **And** all other targets are still delivered
- **And** the notification ends as `FAILED` so that someone can look at it.

**AC-11. A failed notification stays failed**

- **Given** a notification ended as `FAILED`
- **Then** it is not retried automatically
- **And** the ledger shows which targets failed and why, so an operator can fix
  the cause and resubmit with a new idempotency key.

**AC-12. The service crashes while sending**

- **Given** the service stopped in the middle of sending a notification
- **When** it (or another copy) runs again after the 5-minute lease
- **Then** the notification is picked up again automatically
- **And** targets that were already delivered before the crash are skipped, so
  nobody is e-mailed twice
- **And** if the same notification has crashed the service more than 5 times, it
  is marked `FAILED` ("max claim attempts exceeded") instead of being retried
  forever.

**AC-13. Two copies of the service run at once**

- **Given** two instances are running
- **Then** each waiting notification is picked up by exactly one of them.

**AC-14. A customer has no distribution list**

- **Given** customer C has no distribution list
- **When** an event affects customers A and C
- **Then** A is notified; C is skipped with a warning and counted in the
  "customers skipped" metric
- **And** no e-mail is sent to any made-up address
- **And** the notification can still end as `COMPLETED`.

**AC-15. An unknown document type arrives**

- **Given** `documentTypeCode = "IMPORT_ALERT"`, which we do not know yet
- **Then** the event is accepted and the message reads "… received a FDA import
  alert on …".

**AC-16. A target uses Slack**

- **Given** a distribution list with a Slack channel
- **Then** the list is accepted, but deliveries to Slack are recorded as `FAILED`
  ("No sender configured for channel: SLACK") until a Slack sender exists.

**AC-17. The audit archive is unreachable**

- **Given** AWS SNS is down
- **Then** notifications are still received and sent
- **And** audit events wait in the outbox and are forwarded **in order** once
  SNS is back
- **And** the "outbox size" and "oldest outbox event" metrics show the backlog.

**AC-18. The database is down**

- **When** a request arrives while PostgreSQL is unreachable
- **Then** the answer is `503 Service Unavailable` with a generic message (no
  internal details), so the caller knows to retry later.
