# Page planning batches

Implemented on the working tree; not committed or release-certified.

- Precision can plan consecutive typing, clicks and assertions together, retaining the existing five-intent request limit. Explicit open/navigation instructions start a new planning snapshot.
- Execution remains sequential. URL changes, document replacement, ambiguous selectors, replaced elements, changed control identity or hidden action targets discard only the unexecuted suffix. Input values, CSS classes and enabled-state changes do not invalidate identity.
- Later disabled controls may be shortlisted during planning; normal execution owns action readiness waits. No action is marked passed merely because it was planned.
- If a future control is not available in the snapshot, the available prefix can still be planned. No future-page control is invented.
- Known-memory prefixes execute before unresolved actions are sent for ranking. Memory is saved against the source URL rather than the post-click destination.
- Existing failures and cancellation stop execution without replaying completed actions.

Conservative limits: model-time HTML drift still rejects that model result before execution. Multi-match assertions, absent controls, iframe/shadow targets and replaced-but-equivalent nodes may use the single-intent path. This is not unlimited whole-page batching or cross-page speculative execution.

Validation: `mvn -B -Pdeterministic "-Dtest=Precision*,GroundRank*,ProvePhase*Test,DomainLocatorMemoryTest,TenantScopedHooksAndMemoryTest,TcExecutionServiceTest,PostActionSettleTest,ReuseEligibilityTest" test` — 97 tests, zero failures/errors/skips, 2026-09-29 13:49:27 +03:00. Includes mixed typing/click ranking with a disabled later button, target identity guards, partial execution, navigation, cancellation and failure handling. No live Axis or Cursor request was made. Restart the portal to load changes.
