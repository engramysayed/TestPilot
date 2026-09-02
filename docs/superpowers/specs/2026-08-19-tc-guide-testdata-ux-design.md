# TC guide + Upload strip for TestData / VisualAssertion

**Date:** 2026-08-19  
**Status:** Approved (user chose approach C)  
**Goal:** Clients see the same Excel rules the converter uses, with clearer visuals on `/tc-guide` and a compact strip on `/upload`.

## Locked decisions

| Topic | Decision |
|---|---|
| Approach | **C** — `/tc-guide` is source of truth; Upload shows a compact strip that deep-links into the guide |
| New optional columns | `VisualAssertion`, `TestData` (aliases `Test Data`, `StepData` already in reader) |
| TestData | Line-aligned with Steps (blank lines count). Concrete → type. Blank → invent. Never type `Test` / `User` / `foo` / `fname` |
| Selects | Prefer in the step or TestData |
| VisualAssertion | Optional; low-confidence FAIL / UNCERTAIN alone does not demote |
| Unsupported | File-upload-only flows (unchanged) |
| Scope | Thymeleaf templates + `portal.css` only. No new APIs |
| Out of scope | Downloadable blank `.xlsx` template button (unless added later) |

## Content updates (`tc-guide.html`)

1. Sheet mock adds optional **VisualAssertion** and **TestData** header cells.
2. Column legend / bullet list documents both.
3. New section **Test data** (`#testdata`): Steps | TestData alignment example (Open → blank; Enter First name → `Nora`).
4. Avoid / Prefer cards for embedded `Test` vs field-only Enter + TestData.
5. Short **Visual assertion** note (`#visual`) under expected or as its own subsection.
6. TOC links for the new sections.
7. AI tip: mention TestData / VisualAssertion headers must stay unchanged.

## Upload strip (`upload.html`)

Upgrade `guide-promo`:

- Mini column legend including TestData / VisualAssertion as optional
- Three chips: line-align TestData · blank invents · no Test/User literals
- Primary CTA → `/tc-guide#testdata`
- Secondary / existing → full guide

## Visual / motion

- Keep existing guide layout and portal tokens
- Stagger fade-in on guide sections; short highlight on TestData mock column
- Honor `prefers-reduced-motion: reduce` (no animation)

## Non-goals

- Portal backend / Excel reader changes
- Amazon workbook (already generated separately)
- Changing Facebook Excel further

## Success

- A first-time author opening Upload sees TestData rules without reading the whole guide
- `/tc-guide` sheet mock and Test data section match converter behavior
- No visual regression on mobile guide layout
