# Locator bottlenecks, borrowed ideas, split & mobile

## Your bottlenecks (confirmed)

1. **Bad locator choice** — shortlist/scoring misses the right control.
2. **Slim HTML drops the right element** — truncation, hidden duplicate nav, offscreen sidebar.
3. **Canvas** — stripped in HtmlSlimmer; not in DOM candidate list.
4. **Shadow DOM** — partial; prove/bind/vision inconsistent.
5. **Iframes** — same-origin shallow merge; XPath wrong for frame context.

## Borrowed patterns (from Grok research + Cursor analysis)

| Source | Idea | Keel adoption |
|--------|------|----------------|
| **tester-army/e2e** | A11y snapshot, not HTML; **action replay cache** | AX channel + `LocatorPipeline` replay (code exists — wire everywhere) |
| **Midscene** | Vision locate on screenshot; aiAssert | Vision bind fallback; assert judge (not groundRank on paragraphs) |
| **Maestro** | Accessibility tree, `tapOn: "text"`, tap-effect check | Frame path + name match; **A7 no-effect** |
| **Nightwatch / WebdriverIO** | Auto-wait actionable elements | Align with settle table (A8) + Selenium probes |
| **browser-use** (extra research) | Keep offscreen if inside scrollable container | PageSerializer / HtmlSlimmer rule |

## Technical directions (priority)

### P0 — Prove path uses one snapshot pipeline

- **Full DOM or full serializer output** for `DomCandidateExtractor` (Java).
- **Slim excerpt** only for Cursor prompts.
- **PageSerializer** (`delivery/snapshot/`) → candidates with **framePath**, shadow chain, scrollable sidebar items.

### P1 — Candidate scorer (next funded pack)

Spec: `docs/superpowers/specs/2026-10-16-candidate-scorer-design.md`.  
Plan: `docs/superpowers/plans/2026-10-16-candidate-scorer.md`.

- One `CandidateScorer` for bind + heal shortlist (testid / role+name above id and CSS soup).
- `BindDecision` CLEAR / TIE / EMPTY. Ollama only on TIE (max 5). Vision only if empty or still tied.
- Catalog fingerprint ignores class-hash churn; write-back only after live click. Asserts still do not heal.
- Frozen HTML fixtures (`CandidateScorerFixtureTest` gate) + `bind-quality.json`. TIE pick card (scored shortlist, why tied, capped neighborhood). Portal/codegen out. Not vision-first.

### P1b — Matchers (still open)

- Dedupe text: ignore **hidden** nodes before “ambiguous Support” logic.
- Accept **sidebar `<li>` / menuitem** for CLICK when intent is nav label.
- **CompoundAssert** for multi-part Excel asserts (Keel + Precision) — already in repo.

### P2 — iframe / shadow

- Recursive frames; store `framePath` on candidate; codegen emits switch (Codegen patch claimed applied).
- Closed shadow: hook before load (resources in `customer-framework-template` / `keel/`).

### P3 — Canvas (A5)

- Phase 1: Flutter semantics / chart a11y.
- Phase 2: vision numbered regions + offset click + no-effect check.

### P4 — Precision

- Keep **groundRank** on shortlists only; vision when shortlist empty.
- **Precision reuse locator memory** checkbox (project setting) — Cursor session.

## Portal / engine split (safe on company PC)

**Now (code only):**

- Two modules, two `.cmd` windows, localhost HTTPS or HTTP as today.
- Engine: `EngineAgent` claims **Execute + Automate** (verify `REMOTE_KINDS`).
- Portal: DB (H2/SQL Server later), job queue, UI.

**Later (personal RDP / servers):**

- SQL Server Express on portal; engine no DB credentials.
- Engine egress-only; portal signs jobs; mTLS or signed tokens.
- Chrome: headless 1920×1080 default; headful on engine desktop if vision needs it.
- **You** turn FortiClient on for Axis; no VPN automation in Keel on company PC.

**Do not on company machine:** Squid, firewall rules, Tailscale mandate, Windows services (you chose `.cmd` only).

## Mobile phase gate

**Do not start M0** until:

- Axis (or equivalent) sidebar + login + assert path stable.
- iframe/shadow covered for your real apps.
- Customer template: **one page class per page**, reusable utils (your Maestro brief to Codegen).

Then: `docs/superpowers/plans/2026-09-27-android-mobile/` + Grok v2 (UiAutomator2, Flutter, keyboard, Unicode, crash guard).

## Framework / codegen audit (Maestro brief)

Deep-dive targets when stable:

- `customer-framework-template/templates/*.ftl` — one `*Page` per screen, shared utils.
- `PageAccumulator` / `CodeWriter` — no duplicate page classes per route.
- Bug Hunt prompts + `tools/cursor-heal/heal.mjs` modes.
- CI: META-INF.services path, nightly cron once not every minute.

## Candidate scorer (2026-10-16)

P0/P1 locator ranking: scorer shipped. Spec: docs/superpowers/specs/2026-10-16-candidate-scorer-design.md. Plan: docs/superpowers/plans/2026-10-16-candidate-scorer.md. Bind uses CandidateScorer + BindDecision; catalog write-back only after live click; Ollama pick only on TIE (max 5). Review fixes: bind-quality counters wired as **job totals** (finally flush; proveOne does not zero); Ollama gets TIE neighborhood; preferStable uses CandidateScorer.stability.
