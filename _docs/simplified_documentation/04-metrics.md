# 4. Metrics

Live numbers come from **Micrometer** (via Spring Boot Actuator, ready for
Prometheus, Datadog or CloudWatch). Weekly business numbers come from simple SQL
on our own tables. No metric contains personal data.

## What the service publishes

| Metric | Type | Labels |
|---|---|---|
| `notification.events.processed` | counter | — |
| `notification.customers.notified` | counter | — |
| `notification.customers.skipped` | counter | `reason` |
| `notification.dispatch.attempts` | counter | `channel`, `result`, `retry`, `error_type` |
| `notification.dispatch.latency` | timer | `channel`, `result`, `retry` |
| `notification.audit.outbox.published` | counter | — |
| `notification.audit.outbox.depth` | gauge | — |
| `notification.audit.outbox.oldest_age_seconds` | gauge | — |

## Adoption: is it used, and does it reach people?

| Question | How | Expected |
|---|---|---|
| Events sent per week | `increase(notification_events_processed_total[7d])` | 1–5 |
| Customers reached per week | `increase(notification_customers_notified_total[7d])` | 10–125 |
| **Reach rate**: are customers set up? | notified ÷ (notified + skipped) | ≥ 95 % |
| Customers with a list | `SELECT count(DISTINCT customer_reference) FROM distribution_list` | grows |
| Do customers maintain their lists? | `target-added` / `target-removed` audit events per month | > 0 |
| Which channels and message types are used? | attempts by `channel`; `notification_inbox` by `message_type` | — |

We do not track opens or clicks: that needs tracking pixels and raises privacy
questions.

## Performance: is it delivered, quickly and reliably?

| Question | How | Target |
|---|---|---|
| **Delivery success rate** | successful attempts ÷ first attempts (`retry="false"`) | ≥ 99 % |
| **Retry rate** | attempts with `retry="true"` ÷ all attempts | < 5 % |
| **Send time** | p95 of `notification_dispatch_latency` | < 2 s |
| **Time to notify** | p95 of `updated_at − created_at` for `COMPLETED` notifications | < 1 min |
| **Audit lag** | `notification_audit_outbox_oldest_age_seconds` | < 60 s |

## Alerts

With 1–5 events a week every failure matters, so alerts fire on **single**
events, not percentages.

| Alert | When | Why |
|---|---|---|
| Delivery failing | Any permanent delivery failure in 15 min | A customer did not get a warning. |
| Notification failed | A notification reaches `FAILED` | Someone must check the ledger and resend. |
| Customer not set up | `customers.skipped` increases | A customer has no distribution list. |
| Stuck notification | `DISPATCHING` for more than 10 min | Crash recovery is not working. |
| Audit backlog | Oldest outbox event > 5 min, or depth > 1,000 | Audit events are not reaching SNS. |
