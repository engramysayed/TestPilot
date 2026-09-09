# Bug Hunter — live smoke checklist

Use after portal restart with `delivery.dry-run=false` and a multimodal model (e.g. `gemma4:e2b`).

1. Create/open a project with base URL + credentials if needed.
2. Ensure the generated library has 1–2 TCs covering the feature under test.
3. Open **Bug Hunter** → select those TCs → DOM mode Auto → strategies on → Start.
4. When the job completes, download the hunter pack ZIP and confirm:
   - `brief.md`, `SUMMARY.md`, `steps-journal.md`, `coverage-map.md`, `coverage-map.json`
   - `cycles/cycle-01/page-map.md`, `dom-slim.txt`, `screenshot.png`
   - `cycles/cycle-01/planner-prompt.txt` (includes locator rules + preferred hooks section)
   - `cycles/cycle-01/planner-response.txt`, `oracle.json`
5. Spot-check `actions-log.json` for any `ungrounded_locator` rejects (expected if the model invents selectors).
6. If the planner prompt is still huge or actions ignore the page map → open  
   `docs/superpowers/plans/2026-09-10-bug-hunter-phase3-twopass-dom.md`.
