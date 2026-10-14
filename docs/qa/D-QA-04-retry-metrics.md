# D-QA-04 Retry metrics fold (2026-10-14)

Applied `retry-metrics/retry-metrics.patch` + UI parts of `retry-metrics-ui.patch` onto tip.

**Skipped:** `static/css/keel-reskin.css` (path absent on tip). Visual polish optional follow-up when reskin CSS lands.

**Green:** HonestRetryMetricsTest + RetryMetricsViewTest → 22/0/0.
**CI:** `.github/workflows/retry-metrics-green.yml` asserts firstPassRate symbols + tests.
