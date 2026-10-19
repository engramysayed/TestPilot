# Keel / TestPilot — engineering handoff (from Grok Maestro team)

These files capture the **Grok Bot “Keel team”** workstream (Oct 2026) so you can resume in **Cursor** without re-reading long chat logs.

| Document | Use when |
|----------|----------|
| [2026-10-19-authoring-verb-aliases.md](2026-10-19-authoring-verb-aliases.md) | Spec: read-only built-in verbs + Owner aliases (click/type/assert/ignore) |
| [2026-10-19-scoped-nested-hook.md](2026-10-19-scoped-nested-hook.md) | Shipped: unique menu row with a cloned inner hook uses a scoped inner-hook locator |
| [2026-10-19-admin-review-batch-and-browser.md](2026-10-19-admin-review-batch-and-browser.md) | Shipped: Owner review TCs per batch + Chrome/Chrome headless/Edge |
| [2026-10-19-nested-hook-and-quote-heading.md](2026-10-19-nested-hook-and-quote-heading.md) | Shipped: inner unique preferred hook on named bind; review leftover Expected quotes; Execute unique live heading wait |
| [2026-10-15-portal-session.md](2026-10-15-portal-session.md) | **Start here for the current portal** — Record, unit prices, editable price copy, owner-only admin, account alerts, layout |
| [grok-maestro-handoff-2026-10-14.md](grok-maestro-handoff-2026-10-14.md) | Earlier timeline — what landed locally, what did not, priorities |
| [per-bot-status.md](per-bot-status.md) | Who (Codegen, Frontend, Backend, Automation, QA) did what |
| [locator-and-split-roadmap.md](locator-and-split-roadmap.md) | Locator bottlenecks, borrowed ideas, portal/engine split, mobile gate |
| [decisions-and-open-questions.md](decisions-and-open-questions.md) | Your answers + still-open items |

**Not in git (Grok workspace only — export if you still have access):**

- `/workspace/research/LOCATOR_RESEARCH.md`
- `/workspace/research/DEEP_RESEARCH.md`
- `/workspace/testpilot/KNOWLEDGE.md`
- `/workspace/testpilot/SPLIT_DESIGN.md`
- `/workspace/testpilot/SPLIT_DESIGN_OPS.md`
- Patch folders under `/workspace/testpilot/patches/` (A1–A11, r1–r3, integration series)

If those paths are gone, treat the summaries in this handoff as the source of truth until you re-run research in Cursor.

**Graphify (local):** `graphify-out/graph.html`, `GRAPH_REPORT.md` — 11046 nodes / 28557 edges / 530 communities (2026-10-13).

**Mobile plan (in repo):** `docs/superpowers/plans/2026-09-27-android-mobile/` — planning complete; **do not start M0** until Keel web locator stability is confirmed.
