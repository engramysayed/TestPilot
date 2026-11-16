# Phase 7 — Suites schedule UX (2026-11-16)

## Shipped

- **`suites-schedule.html`** — stack layout, human suite picker (Smoke/Regression/… + tags; id under **Technical id**), cron **presets** (daily 2am, weekly Mon 2am, interval, custom cron), credential **dropdown** (same API as Execute), Account alert checkbox (replaces channel/severity dropdowns), watch-after-green copy + `?` help.
- **`keel-suites-schedule.css`** — picker cards, sticky **Save schedule settings** bar.
- **`suites.js`** — schedule-page detection (`suites-schedule-page`); unified `saveAllScheduleSettings()` (schedule + clear suite alert binding + monitor promote/demote); credential and account alert summary loaders.

## Verify

1. `/suites/schedule` — pick project → suites show friendly names, not raw ids in the list.
2. Choose preset **Daily at 2:00 AM**, save → sticky bar shows success; reload confirms cron.
3. **Use my alert preferences from Account** — summary reflects `/api/account/alerts`; save clears per-suite channel binding.
4. **Run pack now** and **Stop watching** still work from sticky bar / monitor section.

## Next

Phase 8 — Execute tunnel user-facing copy.