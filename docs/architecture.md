# Task 2 — Backend Architecture

The diagram is in `architecture.drawio` (open with draw.io / Lucidchart). This file explains what the boxes mean and how the running prototype maps onto them.

## What the diagram shows

Five lanes left to right: Sources, Ingestion, Processing, Storage, Serving. Feedback enters from the left and ends up rendered as charts on the right.

**Sources** are the three realistic ways feedback arrives: an analyst uploading an Excel or CSV file through a web form, webhooks pushed from survey tools (Typeform, Intercom, etc.), and scheduled connectors that pull files from S3 or SFTP on a cron. All three end at the same destination.

**Ingestion** is a single API that authenticates the caller, validates the schema, writes the raw file to object storage so we have an untouched copy for replay, and emits a `feedback.received` event. Keeping the raw file means a bug in any downstream step is recoverable, we re-process from the bucket.

**Processing** is two workers reading from a message bus. The cleaning worker strips PII, normalises whitespace, and drops empties. It emits `feedback.cleaned`. The theme and sentiment service picks that up, classifies the row, and emits `feedback.enriched`. This is the box where the regex classifier lives today and where an ML model would slot in later, same interface.

**Storage** has three pieces. PostgreSQL holds every row with its themes column, indexed by service and timestamp. A rollup cache (a Redis box or just a `feedback_daily_rollup` table) holds the pre-computed daily, service, and theme aggregates so the dashboard never scans the raw table for chart data. A scheduled rollup job rebuilds those aggregates on an interval and also checks for new spike days.

**Serving** is the analytics API the dashboard talks to. Same endpoints the prototype already exposes. The dashboard itself is a static SPA. There's also an alerting hook on the rollup job, when a service crosses the spike threshold it fires a Slack or PagerDuty webhook.

## Offline / on-prem variant

The brief asks how the backend could run offline, without sending feedback to external services. The architecture doesn't change. Each cloud-flavoured box has a self-hosted equivalent:

| Component | Cloud option | Offline equivalent |
|---|---|---|
| Object storage | S3 | MinIO on a VM |
| Message bus | Cloud Kafka, SQS | RabbitMQ, or skip the bus and use a DB-backed work queue (Postgres LISTEN/NOTIFY) |
| Database | RDS Postgres | Postgres on a VM |
| Sentiment / theme model | OpenAI, Bedrock | The rule-based classifier and no model at all |
| Dashboard hosting | Hosted SaaS | Static SPA served by the same Spring Boot jar (which is what the prototype does) |
| Auth | Cloud IdP | Self-hosted Keycloak, or basic auth on a private network |

The running prototype is the most extreme version of this. One JVM, embedded database, rules-based classifier, no network calls. The production offline variant is the same components spread across VMs.

## How the prototype maps to the diagram

| Box in the diagram | Where it lives in the code |
|---|---|
| Ingestion API + Cleaning worker | `DataLoader.java`, all in one class on startup |
| Theme & Sentiment service | `ThemeClassifier.java` |
| PostgreSQL feedback table | H2 with the same JPA entity (`Feedback.java`) |
| Rollup cache + rollup job | `AnalyticsService.java` computes aggregates on demand |
| Analytics API | `FeedbackController.java` |
| Dashboard SPA | `src/main/resources/static/index.html` |