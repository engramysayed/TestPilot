# Candidate scorer + stronger DOM shortlist

**Date:** 2026-10-16  
**Status:** Approved for planning (chat)  
**Plan:** `docs/superpowers/plans/2026-10-16-candidate-scorer.md`

## Problem

Keel already extracts DOM candidates, ranks them with token overlap plus `strategyRank / 10`, and only asks Ollama on a **1-point** near-tie. Vision and invent still paper over a weak first pick. `PageLocatorCatalog` stores the page list and can adopt a vision guess **before** a click proves it. `id` still outranks `data-testid` (40 vs 35). Two catalogs are easy to mix up: the in-run page list vs cross-job `DomainLocatorMemory`.

The last AxisPay Support miss was expected-text, not a missing locator. Bind quality is now the limiter.

## Goal

Bind picks the locator **before** Ollama or vision, from a scored shortlist of 3–5 live candidates. Ollama runs only when scores are close. Vision runs only when the shortlist is empty or still tied after Ollama. Catalog and memory update only after a live click/type succeeds. Assert failures do not heal or rewrite locators (already true; keep it). Measure bind/catalog/heal/false-bind on frozen HTML fixtures.

## Non-goals

- Portal UI, codegen, customer ZIP, or Playwright `LocatorPipeline` as the prove bind path.
- Midscene-style vision-first.
- Bigger multimodal / groundRank prompts.
- More invent.
- Slim-HTML truncation, iframe, shadow, canvas (separate packs).
- Live AxisPay Execute spend as the first acceptance gate.
- Sharing `LocatorRanker` (needs WebDriver) from bind unit tests.

## Architecture

One scorer. Bind, heal shortlist, and catalog preference call it. Models still only pick `candidateId` from that shortlist.

```text
HTML → DomCandidateExtractor (filter noise, emit stable locators)
     → PageLocatorCatalog.ensure (rebuild on URL or stable fingerprint)
     → CandidateScorer.score(intent, candidates)
     → BindDecision
          CLEAR  → use winner (no Ollama, no vision)
          TIE    → Ollama picks candidateId from top 3–5
          EMPTY  → vision (if eligible) else heal named-retry else invent last
     → live click/type succeeds
          → catalog.commitAfterProof
          → DomainLocatorMemory.remember (already proof-gated in ProvePhase)
     → ASSERT_VISIBLE fail → stop; no heal; no write-back
```

Do **not** replace `StepIntentBinder` special cases that already work (submit vs nav, ordinal checkbox, quoted accessible name). Fold their **score bonuses** into `CandidateScorer` so margin math uses the same totals. Leave phrase `textContains` xpath asserts on their current path (they do not score chrome candidates).

`delivery.snapshot.LocatorRanker` already has the right **tier order** (preferred hook → testid → role+name → label → css id → class → position). Copy that order into authoring integer bands. Do not call `LocatorRanker` from bind.

## Components

### `CandidateScore`

Per candidate totals, all ints:

| Field | Range | Meaning |
|-------|-------|---------|
| `stability` | 0–50 | Locator family (see bands) |
| `uniqueness` | −10–15 | How many other candidates share label or locator |
| `intentMatch` | 0–40 | Quoted name / token overlap with label then value |
| `roleFit` | 0–10 | Tag/role matches intent kind (click → button/link/menuitem) |
| `labelDistance` | 0–10 | Exact label 10, tokens in label 6, tokens only in locator value 2 |
| `total` | sum | Used for sort and margin |

Geometric pixel distance is out of scope: `DomCandidate` has no box or parent. Do not add `Element` to the record in this pack.

### Stability bands (higher wins)

| Band | Score | Examples |
|------|-------|----------|
| Preferred hook | 50 | Project `PreferredHooksStore` attrs (`data-axis-test-id` as CSS) |
| Test id | 45 | `data-testid`, `data-test`, `data-qa` |
| Role + name | 40 | `[role=button][aria-label='Save']`, native `button[aria-label]` |
| Aria / linked label | 36 | `aria-label`, `<label for>` xpath |
| Name attr | 30 | `name="q"` |
| Stable id | 24 | `id` that is **not** `GeneratedIdDetector.looksGenerated` |
| CSS unique attr | 12 | Non-class unique attribute CSS |
| Text xpath | 8 | `//button[normalize-space()='Save']` |
| Absolute / indexed xpath or `:nth-` CSS | 2 | Position soup |

Generated ids and hashed CSS classes (`jss-`, `css-`, `svelte-`, `ember1234` already detected) score **0** and must not be emitted as `strategy=id`. They may remain as a last-resort CSS/xpath on the same node only if no stable attr exists.

### `BindDecision`

```java
public enum Kind { CLEAR, TIE, EMPTY }

public record BindDecision(
        Kind kind,
        DomCandidate winner,          // non-null iff CLEAR
        List<DomCandidate> shortlist, // 1 if CLEAR, 2–5 if TIE, empty if EMPTY
        List<CandidateScore> scores   // best first
)
```

Constants:

- `MARGIN = 12` — CLEAR only if `best.total - runnerUp.total >= 12` **or** runner-up is the same control (`locatorTwins`).
- `MIN_TOTAL = 15` — below this → EMPTY (today’s weak match used `< 2` on a smaller scale).
- `SHORTLIST_MAX = 5`.

Two controls with the same accessible name and the same band (twin Settings buttons) stay **TIE** → `AMBIGUOUS` unless a preferred candidate id is supplied. `NearTieAmbiguousTest` must still pass.

### Extractor filters

Keep one locator family per node (hooks already win). Add:

1. Emit a role+name CSS/xpath when `role` + (`aria-label` or accessible name) is unique.
2. Do not emit CSS whose only discriminator is a generated class.
3. Continue dropping generated `id` via `usableIdentifier` (already true).

### Catalog fingerprint (in-run only)

`PageLocatorCatalog` is **per job**. It cannot survive a redeploy. Cross-job stability stays `DomainLocatorMemory` after proof.

Change fingerprint from nav **text** hash to:

- URL
- interactive count by tag/role (existing count is fine)
- sorted test-hook `attr=value` list (existing)
- sorted test-hook values **inside nav/aside** (not `.text()`, so a badge number in the sidebar does not bust the cache)

Rebuild when a new hook appears (submenu expand adding KYC) — `PageLocatorCatalogTest` must still pass.

Ignore class-hash churn on non-hook nodes: adding `class="css-1abc2de"` to a `div` must **not** rebuild.

### Write-back only on proof

| Store | Today | This pack |
|-------|-------|-----------|
| `DomainLocatorMemory.remember` | After successful execute (`rememberSuccessfulLocator`) | Unchanged |
| `PageLocatorCatalog.adoptOrPreferStable` | Prefers catalog **or adopts** a new vision node immediately | Prefer existing only. New nodes go to a pending list. `commitAfterProof(DomCandidate)` copies pending → catalog after execute PASS |
| Assert fail | `ProvePhase.mayHeal` false | Unchanged; no commit, no remember |

`stabilizeSteps` may rewrite a vision xpath to a catalog hook. It must not grow the catalog.

### Heal / Ollama / vision

1. Bind CLEAR → execute. No heal.
2. Bind TIE → `AMBIGUOUS` with shortlist ids → HealCascade **pick** on **that** shortlist (max 5), not 12.
3. Bind EMPTY → vision if `VisionTriggers` eligible, else named-retry, else invent (unchanged last hope). Do **not** send a pick card with a page-sized neighborhood.
4. ASSERT_VISIBLE never enters HealCascade.

Named-retry after a burned locator stays: try next scored named row **before** paying for Ollama.

#### Pick payload (TIE only)

Ollama and Cursor **pick** share one card. Not `DomCandidateExtractor.formatTable`, not a slim-HTML dump, not Cursor `solve`/`invent`.

| Field | Content |
|-------|---------|
| `quotedName` | `StepIntentBinder.quotedVisibleName` (empty if none) |
| `whyTied` | `TIE margin=<best-runner> best=<id>=<total> runner=<id>=<total> same-label=<label>` |
| `scoredTable` | ≤5 rows: `id \| strategy \| value \| tag \| label \| total \| stability \| uniqueness \| intentMatch \| roleFit \| labelDistance` |
| `neighborhood` | Parent + sibling outer HTML of the shortlisted nodes only, truncated to 1500 characters |

Ollama system prompt (exact contract): pick `{"candidateId":"<id>"}` only. Include the scored table. No Thought. No locators. No invent in that call. Invent stays a later `tryInvent` with `mode=invent`.

Cursor on TIE uses `mode=pick` with the same card. Do not call `solve` (solve may write a locator). `acceptWrittenLocator` is not on the TIE pick path.

### Measurement

`BindQualityMetrics` counters (per job):

- `bindClear`, `bindTie`, `bindEmpty`
- `catalogHit` (ensure reused), `catalogRebuild`
- `ollamaAsked`, `visionAsked`, `healEntered`
- `falseBind` — fixture-only; production has no oracle

Write both:

1. One log line `BIND_QUALITY clear=.. tie=.. empty=.. catalogHit=.. catalogRebuild=.. ollama=.. vision=.. heal=..`
2. Job evidence file `bind-quality.json` with those ints (easy to open; no portal page)

Acceptance is **`CandidateScorerFixtureTest` green**. Do not start a live AxisPay Execute until that class passes.

## Fixture suite (false-bind gate)

Synthetic HTML only. No production hostnames. One page `scorer-sidebar.html` must cover every row. `falseBind` in the test is 1 when the winner is not the expected locator (or CLEAR/TIE kind is wrong).

| Intent | Expected |
|--------|----------|
| `Clicks "Support" in the sidebar` | **CLEAR**, value contains `side-nav-support-submenu-button` |
| `Click Save` | **CLEAR**, preferred hook (`save-hook`) or `data-testid=save`, not text xpath, not `id=btn-save` as first choice |
| `Click Refresh` | **CLEAR**, not `id=ember1234` |
| `Click Delete` | **TIE** (two same-name buttons) |
| `Click Settings` | **TIE** (two Settings buttons) |
| catalog.ensure twice with hashed class on a wrapper | same generation (Task 5) |

Reuse `src/test/resources/fixtures/a2/ranking.html` only as extra coverage if useful. The gate class does not need Chrome.

## Tests that must still pass

- `NearTieAmbiguousTest`
- `PageLocatorCatalogTest`
- `AssertHealPolicyTest`
- `GeneratedIdDetectorTest`
- Preferred-hook extract/bind tests

## Out of pack (parked)

Do not start these in Tasks 1–9:

- Pixel / ancestor label distance (needs more candidate fields).
- Slim HTML keeping off-screen nav.
- iframe / shadow / canvas.
- Precision `groundRank` neighborhood (same card shape later; its own pack).
- Wiring `LocatorPipeline` replay into prove.
- Live AxisPay Execute spend / bind-rate notebook (only after `CandidateScorerFixtureTest` is green).
- Portal UI for bind quality.
