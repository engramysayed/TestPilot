TESTING TECHNIQUES PLAYBOOK — apply while generating from user stories.

For each batch, aim for broad requirements coverage using:
1. Equivalence partitioning — valid/invalid input classes
2. Boundary value analysis — min, max, just inside/outside limits
3. Negative paths — error messages, validation, unauthorized access
4. State transitions — empty → filled → submitted → success/failure
5. Role/permission variants — when roles are mentioned in stories
6. Smoke vs regression — tag in Tags column (smoke, regression, boundary, negative, a11y)
7. Visual/regression — use VisualAssertion + KeelPath VISION_ONLY or EXECUTE when UI appearance matters

COVERAGE OUTPUT:
After the CSV block, append exactly:
---KEEL_COVERAGE---
Then 2–6 sentences listing: techniques applied, areas covered, and gaps (e.g. "no mobile AC", "logout not in stories").

Do not invent requirements not present in the pasted stories. Prefer 5–15 TCs per batch.
