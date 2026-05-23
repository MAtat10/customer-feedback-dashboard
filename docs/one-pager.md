# Customer Feedback Dashboard

Mahdi Atat · May 2026

## Approach

The assignment is two tasks. Build a dashboard from the feedback data, and design how the same system would look at production scale. I built both as one thing. The running app is the architecture diagram, just collapsed into a single JVM instead of split across services. Same components, same data flow, smaller scale. The benefit is I can point at any box on the diagram and show you the code that plays that role today.

Stack is Spring Boot 3.3 on Java 21, H2 in memory for the database, Apache POI to parse the Excel sheet, and a single Chart.js page served from the classpath. No network calls, which doubles as the answer to the Task 2 offline-mode bonus.

On startup `DataLoader` reads the bundled xlsx into H2. Each row goes through `ThemeClassifier`, which tags it with zero or more themes (support_delay, outage, usability, reporting, onboarding, performance, pricing, positive_experience) using keyword regex buckets. `AnalyticsService` builds the daily-volume, by-service, and theme aggregates on demand and flags any day whose count is at least mean + 2σ as a spike. The REST controller exposes the aggregates as JSON, and `index.html` renders them with Chart.js.

I went with regex buckets instead of an ML classifier because with a thousand short feedback strings there isn't enough signal to defend a model. Rules are auditable, the reviewer can read the patterns and check them against examples. The classifier interface is small enough that swapping in an embedding model later is a one-class change.

The dashboard at `/` shows: a management summary card with the live insights, KPIs, a daily volume bar chart with spike days flagged, a per-service sentiment breakdown, a theme totals bar, a service × theme heatmap, and a filterable recent-feedback table.

## How I would turn this into a live tracker

1. Replace the one-shot loader with an authenticated batch upload endpoint and a few scheduled connectors (CSV pulls from S3, webhook receivers for the survey tools the business already uses).
2. Move storage from embedded H2 to PostgreSQL. The code change is one property line because everything goes through JPA.
3. Run the classifier asynchronously. Ingestion writes the raw row and emits an event. A worker pool picks it up and writes the themes back. That way the classifier can be re-run without re-ingesting.
4. Add an event bus, Kafka or even Postgres LISTEN/NOTIFY for a smaller setup, so the dashboard can subscribe and refresh in near real time.
5. Add a scheduled rollup job that pre-computes the per-service and per-theme aggregates into summary tables. Keeps the dashboard fast as the row count grows past what an in-memory aggregation can handle.
6. Add alerting when a service crosses the spike threshold, fire a webhook to Slack or whoever is on call.
7. Put OIDC auth in front of the API and add multi-tenancy if more than one team is going to use the same instance.