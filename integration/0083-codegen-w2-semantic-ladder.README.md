# 0083 — Codegen W2 semantic ladder + uniqueness + merge + ARIA

**Status:** GREEN (live dirty; not committed/pushed)
**Tip:** 83b9077f537693d24e473554179225e68cdb3756
**When:** 2026-11-02 Africa/Cairo

## Ships
1. Semantic locator ladder role → label → testid → CSS (after preferred-hook-first)
2. Pick-locator uniqueness reject (bare role without accessible name)
3. Merge re-generate Login/LoginPage/LoginForm → one Login_Page (no LoginPage2)
4. Trimmed ARIA/region asserts (elementAriaRole / elementAriaLabel / elementRegion)

## Apply
From TestPilot root (on top of W1 dirty tree):
```
git apply --whitespace=nowarn integration/0083-codegen-w2-semantic-ladder.patch
```

## Verify
See docs/history/handoffs/2026-11/2026-11-02-codegen-w2.md
