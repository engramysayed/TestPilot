# Grok Maestro handoff — 2026-10-14

## What you asked for (current priorities)

1. **Fix Keel locator bottlenecks** (slim HTML, sidebar, iframe, shadow DOM, canvas) — borrow from e2e, Midscene, Maestro, Nightwatch/WebdriverIO (+ research).
2. **Defer mobile** until web Keel is stable; mobile plan v2 was drafted in Grok — align with `docs/superpowers/plans/2026-09-27-android-mobile/`.
3. **Split portal vs engine** — two folders/apps, `.cmd` launchers, later two RDP servers; **no network/firewall changes on company machine**.
4. **Committing** — explicitly parked; large uncommitted tree remains.

## What Grok claimed was applied to `D:\priv\testpilot\TestPilot`

Maestro reported **45 patches** applied (locator serializer, ranking/replay, settle, engine runner, UX copy, etc.) with:

- Backups: `backup-before-patches-*`, `backup-before-integration-*`, `db-backup-20261013-214547\`
- Launchers: `start-portal.cmd`, `start-engine.cmd`, `start-keel.cmd`
- **Nothing committed**; your prior edits preserved

**Verify on your machine:** run `mvn -Pdeterministic test` (or your usual profile), restart portal, rerun **Axis TC_02 (Support sidebar)**.

### What this repo scan shows (2026-10-14)

| Signal | Present locally? |
|--------|------------------|
| `start-keel.cmd` / `start-portal.cmd` / `start-engine.cmd` | Yes |
| `keel-common/`, `keel-contract/`, `keel-engine/` modules | Partial (multi-module started) |
| `PageSerializer`, `LocatorPipeline`, contract v2 tests | Yes (under `delivery/snapshot/`, tests) |
| `CompoundAssert`, Precision locator memory, batch groundRank | Yes (Cursor session + your tree) |
| `SPLIT_DESIGN.md`, `KNOWLEDGE.md`, research MD on disk | **No** — Grok workspace only |
| Git commits for patch series | **No** — last commit `de7f579` (graphify audit); large **uncommitted** diff |

**Conclusion:** A **large portion** of Grok’s engineering likely exists as **uncommitted files** in TestPilot; design docs and patch READMEs may **only** exist in Grok unless you export them.

## Axis sidebar case (exec-style TC_02)

**Job:** `exec_a74997b2c66c` (Oct 11, opssit.axispay.app) — click **Support** in sidebar.

**Root causes (Automation Engine, per Grok):**

1. **Not primarily “sidebar dropped from slim HTML”** on that run — item was kept.
2. **Matcher:** “Hidden Support” substring made **Support** look ambiguous → **no candidates**.
3. **Vision:** UI-TARS often hit Support on full screenshot; **Java rejected `<li>` / menu-item** as click targets.
4. **Coordinate bug (later found):** some model answer formats parsed as pixels vs 0–1000 grid → ~150px error (fixed in Grok patches with 36 tests).

**Your action:** Portal restart after patches; rerun TC_02; confirm `LOCATOR_MEMORY` / `COMPOUND_ASSERT` / new serializer wired into **ProvePhase** (Grok said serializer existed but was not always wired to prove).

## Recommended order of work (Cursor / you)

### Phase A — Stabilize & verify (1–2 days)

1. Confirm which Grok patches are **actually** in your tree (`git diff --stat`, spot-check `PageSerializer`, `EngineAgent`, settle policy JSON).
2. Full or targeted test run; fix **line-ending / Python / flaky** issues Maestro noted.
3. Rerun **Axis TC_02** with VPN on manually.
4. Export or recreate **LOCATOR_RESEARCH** + **DEEP_RESEARCH** into `docs/research/` if Grok still accessible.

### Phase B — Locator bottlenecks (highest product value)

See [locator-and-split-roadmap.md](locator-and-split-roadmap.md). Short list:

- Wire **PageSerializer** into **ProvePhase** / **Precision** path (if not already).
- **iframe:** frame path on candidates + generated switch helper.
- **Shadow:** open roots + closed-shadow hook (tests exist in repo).
- **AX / scrollable offscreen:** keep sidebar items in snapshot (Maestro + browser-use idea).
- **Canvas:** A5 two-phase plan (a11y first, vision numbered boxes second).
- **Replay cache** (e2e-style): route-scoped locator replay before Cursor.

### Phase C — Portal UX (after reskin)

Frontend redid UX patches on **Keel reskin** (plain language, More options, Results page). **Verify templates** match current `keel-reskin.css` — Grok admitted first screenshots were pre-reskin.

### Phase D — Split portal/engine (code only on company PC)

- Modules: contract, common, portal, engine.
- Engine pulls jobs via runner API; **Execute/Automate** on engine first.
- **SQL Server Express** chosen (not Mongo); PostgreSQL was Grok default before you corrected.
- **No** Squid, VPN automation, or RDP hardening on company machine.

### Phase E — Mobile

Only after Phase B acceptance. Use existing Android plan pack + Grok mobile v2 recommendations (UiAutomator2, Flutter, keyboard, crash detection).

## What to tell Cursor (one message)

> Read `docs/handoff/*`. Do not commit unless I ask. Priority: wire locator pipeline (PageSerializer, iframe/shadow, sidebar), verify Axis TC_02, then audit customer-framework-template for one-page-one-class. Mobile and RDP infra later.

## Risks / lies to double-check

- “All 45 patches applied” vs **your** disk — reconcile with tests.
- “9 pre-existing failures now pass” — re-run on **your** JDK/Chrome.
- UX patches on reskin — visual check in browser.
- **qwen2.5vl:7b** license (research-only for 3b; 7b Apache per Grok) — confirm before production.
- **H2 open port** (issue #2) — password required; stop portal before DB tools.
