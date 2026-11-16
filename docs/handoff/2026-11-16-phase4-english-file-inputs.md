# Phase 4 — English file inputs (2026-11-16)

## Shipped

- **`keel-file-input.js`** — wraps visible `<input type="file">` with **Choose file** + English **No file chosen** / filename (hides OS-localized native button).
- **`keel-file-input.css`** — layout for wrap + screen-reader-safe clipped native input.
- **`lang="en"` / `dir="ltr"`** reaffirmed on `<html>` at load.
- Top bar **EN** pill renamed to **Account** (menu was always account/logout, not a language switch).

## Skipped by design

- Hidden file inputs (`hidden`, `data-keel-file-skip`) are not wrapped; programmatic uploads unchanged.
- Date fields stay text `yyyy-mm-dd` (no native `type="date"`).

## Verify

Hard refresh on **Generate** (bulk CSV), **Execute** (workbook), **Automate**, **GFK** (upload tabs) — no Arabic on file row; only English labels.

## Next

Phase 5 — remove duplicate Recent * tables (see `2026-11-16-phase5-dedupe-recent-tables.md`).