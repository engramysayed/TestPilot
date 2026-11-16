# Sidebar discovery fixes — 2026-10-11

Working-tree changes; no commit or release-candidate change.

The Oct 11 Axis screenshot showed an independently scrolling sidebar. The historical run did not retain raw HTML, so the exact historical snapshot omission cannot be established from that run alone. Local fixtures reproduced extraction and binding defects that can produce the same failure.

Implemented:

- Reveal a uniquely named sidebar control before authoring/healing, without clicking it. Bound discovery is limited to eight attempts and restores scroll positions when unsuccessful.
- Grounding scrolls visible nested containers, prioritizing navigation; unsuccessful sweeps restore nested scroll positions. Observation versions include nested scrolling.
- Slim HTML retains framework navigation list items and their nested text, preserving navigation context when flattened. Unique navigation labels precede ambiguous repeated labels under the character budget. Finite budgets still cannot guarantee retaining every control on arbitrarily large pages.
- Navigation list items use their own accessible text instead of IDs or adjacent captions. Exact text locators preserve distinct-control ambiguity.
- Matching treats “Clicks” and “sub-menu” as instructional wording rather than required control-name tokens. Regressions cover both original sidebar step phrases.
- Compound assertions no longer add fixed action settling after each assertion, and unbound required clauses prevent a pass.
- Ollama ranking requests disable thinking; truncated or empty final ranking output remains an error. No live Ollama timing measurement was made in this batch.

Verification:

`mvn -B -Pdeterministic "-Dtest=SidebarSnapshotTest,AccessibleName*Test,DomCandidateExtractorTest,StepIntentBinderTest,ViewportSweepTest,OllamaRequestBudgetTest,ProvePhase*Test,Precision*Test,Compound*Test,HealCascadeTest" test`

144 tests, zero failures/errors/skips; BUILD SUCCESS, 2026-10-11T16:11:33+03:00. Log: `target/sidebar-fix-regression.log`.

`mvn -B "-Dtest=SidebarDiscoveryLiveSmokeTest" "-Dkeel.performance.chromedriver=C:/Users/rsayed/.cache/selenium/chromedriver/win64/155.0.8059.39/chromedriver.exe" test`

One test, zero failures/errors/skips; BUILD SUCCESS, 2026-10-11T16:11:55+03:00. Local headless Chrome verified independently scrolling navigation, restoration, reveal, extraction under a 1,200-character budget, binding, and resolving the resulting locator against the live DOM. Log: `target/sidebar-discovery-live.log`.

Earlier fixture failures exposed budget crowd-out and instructional-token matching and were repaired before these final passes. Axis was not rerun; the full repository suite was not run. Restart the portal to load the changed classes before the next application run.
