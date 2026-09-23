# DOM extraction and binding review

> Subsequent implementation and validation are recorded in [the fixes log](2026-09-20-dom-vision-fixes.md). The findings below describe the pre-fix audit.

Reviewed 20 September 2026. Companion to [grounding review](2026-09-20-grounding-review.md). Current source inspected at HEAD `a4dc94e9fc4faffff4c8915693dd5fd2817cf7ef`; existing working-tree documentation and grounding audit additions preserved. Production code is unchanged.

## Conclusion

The DOM pipeline needs correctness work before prompt tuning. It sometimes changes the identity or structure of controls before binding, and some binding paths declare ambiguity resolved without proving uniqueness. These errors can look like weak vision or model behavior even when they originate in deterministic code.

Ten component behaviors below were reproduced with small synthetic HTML/candidate fixtures. This establishes the described extraction/binding faults, not that every fault has produced a false PASS on a customer website. Runtime execution may reject a bad locator, which still incurs failure and healing instead of correct initial binding.

## Pipeline reviewed

`PageSnapshot.html` combines page source with open shadow-root and same-origin frame markup. `HtmlSlimmer` removes hidden/nonessential content and truncates large pages. `DomCandidateExtractor` creates locator/label candidates with a 120-entry default limit. `StepIntentBinder` classifies instructions, ranks candidates, resolves ties, chooses action/value/assertion and emits steps. `AuthoringService` returns successful deterministic bindings immediately or enters healing. `TcExecutionService` executes locators, with context search and assertions.

The binder's `validated=true` does not mean the locator uniquely identifies the intended live element. Several deterministic paths set it directly. `LocatorValidator` is a syntactic allowlist and is not called uniformly by those paths.

## Reproduced findings

### D1 — High: repeated hooks collapse different controls and discard unique IDs

Source: `DomCandidateExtractor.extract`, `hasStableAttr`, `put`.

Two buttons with unique IDs but identical `data-testid=remove` produce one candidate. The hook branch suppresses ID/name alternatives; `put` deduplicates by strategy/value and retains the first label. The second control is no longer represented, and the retained locator is non-unique.

Example: Remove apple / Remove orange become one `data-testid=remove` candidate labeled Remove apple.

Repair: check uniqueness for hooks, names and preferred attributes, not just IDs. Keep each DOM node distinct; scope duplicate hooks by row/container or retain a unique ID. Deduplicate alternate locators only after establishing node identity.

### D2 — High: compressed HTML duplicates controls and destroys unique identity

Source: `HtmlSlimmer.slimPreferringControls`.

The loop selects both `[id]` parents and their interactive descendants, then appends each element's complete outer HTML. A wrapper containing `button#save` is appended, followed by the same button again. The extractor now detects a repeated ID that was unique on the actual page and loses its ID locator.

Repair: select a non-overlapping subtree set, or build structured node records instead of concatenating fragments. Preserve original identity and context. Skip oversized wrappers while continuing to consider their useful descendants; the current early `break` can omit later controls too.

### D3 — High: hidden removal shifts positional locators away from live DOM

Source: `HtmlSlimmer.slim`, `DomCandidateExtractor.emitIndexedInputs` and other indexed fallback generation.

With one hidden checkbox before a visible checkbox, slimming deletes the hidden element. The visible checkbox becomes index 1 in the snapshot, so extraction emits `(//input[@type='checkbox'])[1]`. On the live page that XPath still selects the hidden first checkbox.

Repair: never derive a live document ordinal from a filtered/reconstructed DOM. Capture original context-aware identity from the browser, use a stable/scoped locator, or refuse unsafe ordinal emission. Merely filtering hidden elements harder does not solve the index mismatch.

### D4 — High: named actions can silently bypass ambiguity

Source: `StepIntentBinder.bindSingle`, named-action resolution and `namedActionResolved`.

Two candidates labeled Add Red Backpack with different IDs are both viable. Binding “Add Red Backpack” picks the first candidate and skips the near-tie ambiguity gate because a named action matched. Matching the requested action/name does not establish that only one control matches.

Repair: require one matching control identity after context constraints; otherwise return AMBIGUOUS or use explicit row/form/section context. Apply the same rule to form-submit selection, which also bypasses ordinary ambiguity in some paths.

### D5 — Medium: preferred/custom hook CSS is incompatible with the validator

Source: `DomCandidateExtractor.cssAttrSelector` and preferred/custom hook branches; `LocatorValidator.CSS_ATTR`.

Preferred `data-cy=checkout` emits `[data-cy='checkout']`. The validator accepts only a tag-prefixed form such as `button[data-cy='checkout']`, so it rejects the extractor's candidate. Some deterministic paths bypass this validator, making behavior depend on the route used.

Repair: agree one locator contract across extraction, binding, healing and emission. Emit the supported form or extend the parser deliberately, then validate consistently. Add contract tests that every emitted supported locator can be validated and resolved against its source node.

### D6 — Medium: hidden controls are reintroduced by the checkbox/radio pass

Source: `DomCandidateExtractor.emitIndexedInputs`.

Earlier passes call `isHidden`; the ordinal checkbox/radio pass does not. A checkbox under a hidden parent still becomes an actionable candidate when extracting raw HTML. Normal slimming removes many such controls, but that introduces D3, and direct extractor use still exhibits D6.

Repair: enforce visibility eligibility in all passes while preserving original DOM positions. Runtime visibility and eligibility must be checked before action.

### D7 — Medium: case-sensitive identifiers are treated as identical

Source: `StepIntentBinder.selectorFingerprint`, used by `describesSameControl` and tie handling.

`id=Save` and `id=save` on two buttons yield the same lowercased fingerprint. Distinct controls can therefore be treated as alternate locators of one control and removed from ambiguity consideration. Lowercasing selector literal values is not safe identity normalization.

Repair: preserve case-sensitive attribute values. Normalize only syntax components whose semantics permit it; prefer browser node identity over text-normalized selector equivalence.

### D8 — Medium: stable IDs/hooks lose ARIA control semantics

Source: `DomCandidateExtractor.extract`, `controlKind`; `StepIntentBinder.resolveFieldAction`.

`<div id='country' role='combobox' aria-label='Country'>` produces candidate tag `div`. Only fallback extraction uses `controlKind`. The same combobox can therefore be recognized differently depending on whether it has an ID. Field-action resolution for “Fill Country with France” returns type, and normal field ranking can omit the div entirely.

Repair: carry actual tag, role, input type, editable state and supported actions as separate fields for every candidate. Never infer input type solely from label/locator words. Preserve those semantics in vision-derived candidates too.

### D9 — Medium: valid apostrophe-containing text locators are rejected

Source: `DomCandidateExtractor.innermostTextXpath`, `XpathLiterals`, `LocatorValidator.XPATH_TEXT`.

A button labeled `Save customer's address` yields a quoted XPath via `XpathLiterals`, but the text XPath validator only accepts a single-quoted literal. This makes ordinary customer-facing labels invalid on validation paths. Attribute selector colon/escaping rules deserve the same contract review.

Repair: support the actual safe literal forms generated by the code (including double quotes/concat as applicable), rather than maintaining mismatched regex subsets. Test labels containing either/both quote types, colons, Unicode and backslashes.

### D10 — Medium: decorative stable IDs can evict the actual action

Source: `DomCandidateExtractor` first pass and `capped`.

The first pass collects IDs on arbitrary elements, not only controls. With 121 decorative ID-bearing divs before `button#checkout`, the 120-entry cap omits checkout. Reserved fallback capacity does not help because checkout is another stable candidate.

Repair: prioritize interactable controls and intent-relevant candidates, retain essential assertion landmarks separately, and make truncation observable. Use row/section context retrieval so large real applications do not depend on document order.

## Additional risks needing live validation

- **Frame/shadow identity flattening:** `PageSnapshot` appends embedded markup under generic context wrappers without a unique frame/host path. Identical IDs in separate contexts become duplicates; runtime context search cannot recover which original context the instruction meant from an unscoped locator alone. Keep context identity through extraction, IR and replay.
- **Computed visibility and interactability:** Jsoup sees attributes, not stylesheet results, overlays, geometry, disabled fieldsets or current browser state. Add a live browser eligibility pass rather than claiming the HTML filter proves visibility.
- **Accessible labels:** `AccessibleName` and `labelOf` use different precedence and truncation. `labelOf` prefers own text and placeholder before some richer name sources. Keep full labels and separate accessible/visible/context text; do not truncate identity to a display preview.
- **Snapshot lifetime:** DOM mutation after extraction can invalidate rank/context/ordinal assumptions. Verify the selected node immediately before action and re-extract when necessary.
- **Repeated field use:** audit spent-control filtering against workflows that intentionally edit the same field twice; previously used must not automatically mean forbidden.

These are static concerns from the inspected paths; they were not demonstrated through live browser executions in this review.

## Tests and limitations

Added `src/test/java/delivery/authoring/DomBindingAuditCharacterizationTest.java` with ten focused reproductions. These tests deliberately assert current faulty behavior and must become safe-behavior regressions when the code is fixed.

Final command:

`mvn -B "-Dtest=DomBindingAuditCharacterizationTest,DomCandidateExtractorTest,StepIntentBinderTest,LocatorValidatorTest,AccessibleNameTest,HtmlSlimmerTest" test`

**68 tests, 0 failures, 0 skips; BUILD SUCCESS, 20 September 2026 13:56:30 +03:00.** Installation drills did not run. An earlier audit-test compile typo was corrected. An initial hypothesis about a wrapped sibling producing a broken XPath was not reproduced (the extractor omitted the field); it is not reported as a confirmed finding.

No live LLM calls, customer application operations, or production code fixes were performed. Existing passing tests plus passing characterization tests do not certify these defects as resolved.

## Recommended next implementation batch

1. Preserve source DOM identity/context through slimming and extraction; remove fragment duplication and filtered-DOM ordinal generation (D2/D3).
2. Require unique live target identity; retain duplicate-hook controls and reject ambiguous named actions (D1/D4/D7). Combine with the grounding review's wrong-target fixes.
3. Unify candidate semantics and locator contracts (D5/D6/D8/D9), then prioritize candidates without starving actionable controls (D10).
4. Validate on the Medusa local storefront/admin: duplicate Add/Remove buttons, repeated fields, dynamic dialogs, variants, hidden responsive markup, and UPDATE/replay. Measure first-bind accuracy, wrong-target rejection, heal rate and replay survival separately.

A broader candidate representation should hold node/context identity, alternate locators with match counts, actual tag/role/type, full accessible name, row/form context and snapshot version. The same representation should be shared by DOM binding and visual grounding so one path cannot bypass the other's target checks.
