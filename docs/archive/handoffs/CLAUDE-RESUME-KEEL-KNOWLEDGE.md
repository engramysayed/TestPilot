# Keel — resume here (Claude Code / Cursor)

**Repo:** `D:\Work\projects\Keel\keel\TestPilot` (Maven multi-module; app sources in `src/`, packaged by `keel-portal`).

Read in this order:

| # | File | Why |
|---|------|-----|
| 1 | [`docs/knowledge/CURRENT.md`](/docs/knowledge/CURRENT.md) | **Current truth**: architecture, flows, locator policy, jobs, credits/refunds, tunnel, config, status |
| 2 | [`docs/handoff/README.md`](/docs/handoff/README.md) → latest session | What changed last and what is unverified |
| 3 | [`docs/knowledge/REVIEW-2026-11-07.md`](/docs/knowledge/REVIEW-2026-11-07.md) | Open risks and next work |
| 4 | [`CHANGELOG.md`](/CHANGELOG.md) | Dated releases |
| 5 | [`docs/ENGINEERING_HANDOFF.md`](/docs/ENGINEERING_HANDOFF.md) | Feature → code/spec index |
| 6 | [`docs/history/HISTORY.md`](/docs/history/HISTORY.md) | Every past session, newest first |
| 7 | `graphify-out/GRAPH_REPORT.md` | Code graph hubs and communities |

**Private resume (machine):** `D:\priv\testpilot\handoffs\RESUME-2026-10-15.md` — short "where we left off" (may be ahead of git).

## Graphify (code knowledge graph)

```bat
REM Python 3.12 required (3.15 may fail to build tree-sitter)
py -3.12 -m pip install graphifyy
scripts\graphify-update.cmd
py -3.12 -m graphify path "ProvePhase" "CodeWriter"
py -3.12 -m graphify explain "LocatorStabilityPolicy"
```

Ignore rules: `.graphifyignore` (skips `delivery-store/`, `target/`, `customer-framework-template/`, `delivery-work/`, …).
`graphify-out/` is gitignored — regenerate after large refactors. Last refresh: 2026-11-07, 30420 nodes / 105506 edges /
1142 communities, built from `43922ae` (before the 2026-11-07 Claude session changes — refresh it).

## Build & test

```bat
mvn -pl keel-portal -am -DskipTests package
mvn -pl keel-portal test
powershell -File scripts/tunnel-live-smoke.ps1   & rem optional live tunnel
```

Portal: `start-portal.cmd` → `http://localhost:8080` (needs `KEEL_DB_PASSWORD` for the `product` profile).

## Not product code

`customer-framework-template/` (ZIP template), `delivery-work/`, `delivery-store/`, `target/` (runtime/build output),
`_backup/`, `_scratch/`, `BOOT-INF/`, `META-INF/`, `api-keys.local.bat` (secrets / local env).

## House rules

- No commit/push unless asked. Tree may be dirty with unrelated local files — stage only what the task touched.
- New session → `docs/history/handoffs/YYYY-MM/YYYY-MM-DD-topic.md` + CHANGELOG line; update `docs/knowledge/CURRENT.md`
  when current behaviour changes.
