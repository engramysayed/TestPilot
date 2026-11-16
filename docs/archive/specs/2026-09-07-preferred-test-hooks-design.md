# Preferred test-hook attributes (project Settings)

## Goal

A site can name extra HTML attributes that must be treated as first-class locators — ahead of `id`, `data-testid`, CSS, and XPath. AxisPay’s `data-axis-test-id` is the motivating case; the names are not hardcoded.

## Why

Built-in rank is `id → data-test / data-testid / data-qa → name → CSS → XPath`. Vendor hooks like `data-axis-test-id` are only seen as generic CSS (low rank), so bind can prefer a text XPath such as `//button[contains(.,'Verify OTP')]`.

## Design

- **UI:** Project Settings (same form as name + base URL). New field **Preferred test hooks**. Placeholder `data-axis-test-id`. Hint: comma-separated attribute names, shared for every project on this base-URL host.
- **Storage:** `delivery-store/<domain>/preferred-hooks.json` next to shared `domain-locator-memory.json`. Not a per-`prj_*` file and not a portal DB column (two Axis projects must share one list).
- **Shape:** `{ "attributes": ["data-axis-test-id"] }`. Save from Settings using the project’s base URL to resolve `<domain>`. Load on Settings GET and on every Execute/Automate job.
- **Sanitize:** lowercase; allow `[a-z0-9_-]`; reject blanks, `javascript:`, CSS selectors, XPath, spaces inside a name. Dedupe. Cap at 8 names.
- **Extract:** those attributes are emitted first as CSS `*[attr='value']` (already executable). They count as stable hooks (`hasStableAttr`) so the control does not also fall through to a text XPath.
- **Rank:** preferred hook **45** → id **40** → data-testid / data-test / data-qa **35** → name → CSS → XPath. Tie-break uses this rank so `data-axis-test-id` beats `id` on the same node.
- **Empty list:** today’s built-in behavior only.
- **Job wiring:** `ConversionJobRequest` carries the resolved attribute list (from the shared file via store root + base URL). `DomCandidateExtractor.extract` accepts the list; existing no-arg `extract(html)` stays empty-preferred.

## Non-goals

Per-control overrides, pasting full CSS/XPath as a “preferred selector”, ranking UI drag-and-drop, Claude/Anthropic models.

## Tests

- Settings PATCH writes/reads the domain file for `https://opssit.axispay.app` → `opssit-axispay-app/preferred-hooks.json`.
- Extract `<button id="x" data-axis-test-id="verify_Otp_Button">` with preferred `data-axis-test-id` → bind/click prefers the hook over `id` and does not emit the innermost text XPath.
- Invalid tokens are dropped; empty input leaves rank unchanged.
