# Customer Feedback Dashboard

Spring Boot app that ingests a customer feedback spreadsheet, tags each row with one or more themes, and serves an analytics dashboard on top of it.

Submitted by Mahdi Atat as a take-home assignment.

## Run it

With Docker (no setup needed):

```bash
docker build -t feedback-dashboard .
docker run --rm -p 8080:8080 feedback-dashboard
```

Or with Maven directly (needs JDK 21):

```bash
mvn spring-boot:run
```

Then open http://localhost:8080.

## What you'll see

- A management summary card with 5–7 data-driven insights
- KPIs at the top (total feedback, sentiment split, worst-performing service, spike days)
- Daily volume bar chart with spike days flagged in orange
- Per-service sentiment breakdown (horizontal stacked bars)
- Theme totals across the dataset
- Service × theme heatmap for the negative-leaning themes
- Recent feedback table with sentiment and service filters

## API

| Endpoint | Returns |
|---|---|
| `GET /api/summary` | Total counts, top theme, worst service, spike day count |
| `GET /api/insights` | Management summary entries (category, headline, detail, action) |
| `GET /api/services` | Distinct service names |
| `GET /api/volume-over-time` | Daily counts with a `spike` flag |
| `GET /api/by-service` | Per-service sentiment breakdown and negative rate |
| `GET /api/themes` | Theme counts across the whole dataset |
| `GET /api/themes-by-service` | Service × theme matrix that drives the heatmap |
| `GET /api/recent?sentiment=&service=&limit=` | Most recent feedback, filterable |

## Project layout

```
src/main/java/com/mahdi/feedback/
  FeedbackDashboardApplication.java
  model/Feedback.java
  repository/FeedbackRepository.java
  service/ThemeClassifier.java        - keyword regex buckets
  service/DataLoader.java             - reads xlsx on startup
  service/AnalyticsService.java       - aggregates + spike detection
  service/InsightsService.java        - generates the management summary
  controller/FeedbackController.java  - REST endpoints
src/main/resources/
  application.properties
  data/customer_feedback_dataset.xlsx
  static/index.html                   - Chart.js dashboard
docs/
  one-pager.md                        - approach + insights
  architecture.md                     - Task 2 write-up
  architecture.drawio                 - the diagram itself
```

## Docs

`docs/one-pager.md` is the approach write-up and the management insights. `docs/architecture.drawio` is the Task 2 diagram (open with draw.io). `docs/architecture.md` is the prose that goes with the diagram, including the offline-mode variant.
