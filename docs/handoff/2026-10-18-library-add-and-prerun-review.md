# 2026-10-18 — Add test case + review cases before run

Shipped in repo at HEAD after Tasks 1–7 (`f6abdb5`). Rebuild the portal jar and Ctrl+F5 before trusting the UI on a running server.

## What shipped

- Project **Test cases** tab: **Add test case** opens the existing editor; Keel assigns the next `TC_` id. **Save** writes the project library workbook (empty library OK — first save creates the workbook).
- **Execute** and **Automate**: optional **Review cases before run** with **Cursor** or **Keel**. Selected cases go out as JSON plus a prompt of Keel authoring rules. **Accept & run** writes edits into the library (version history) and then starts the job. Nothing runs until Accept when review is checked.
- Pre-run review is **not** billed. **Execute** / **Automate** unit prices still apply when the job **starts** (after Accept when review is on).
- Runtime **OutcomeKind** quote-wins-first is **unchanged** (intentional): quoted strings in Steps/Expected still mean visible text at execute time.

## How to use

1. Rebuild and restart the portal (`start-portal.cmd` or the `keel-portal` jar), then Ctrl+F5.
2. Open a project → **Test cases** → **Add test case**, fill Steps/Expected, **Save**. Confirm the new row and a history entry.
3. On **Execute** or **Automate**, select cases, enable **Review cases before run**, pick Cursor or Keel, run review, edit the proposal if needed, then **Accept & run**. Re-open **Test cases** — the library should match what you accepted.
4. Turn review off to run the library as-is with no authoring pass.

## Known defects

- A portal already running on `:8080` serves an old jar until rebuild + restart + Ctrl+F5.
- Empty-state copy on Test cases may still say upload or Generate first even though **Add test case** works.
- Generic review failure fallback text may still say “Cursor” when Keel was selected.
- **OutcomeKind** was not changed by this feature; mismatches between quoted Expected and the live UI can still fail asserts on purpose.

## Spec / plan

- `docs/superpowers/specs/2026-10-18-library-add-and-prerun-review-design.md`
- `docs/superpowers/plans/2026-10-18-library-add-and-prerun-review.md`
