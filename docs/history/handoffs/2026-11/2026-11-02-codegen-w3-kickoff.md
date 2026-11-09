# Codegen W3 kickoff — 2026-11-02

**Assigned by:** Maestro (user pick via Leader)  
**Base tip:** `e9ee274` (live `D:\Work\projects\Keel\keel\TestPilot`)  
**Prior:** W1 GREEN 47/0; W2 GREEN 56/0 QA; Phase 2 CLOSED (heal A/R portal+AE — reuse, don’t fork)  
**Hold:** P3 deferred unless user says otherwise

## Scope (DoD)

1. **Multi-locator bag per element** — emit primary + ordered fallbacks (honor W1 preferred-hook-first + W2 semantic ladder/uniqueness/merge).
2. **DOM-diff AI assert suggestions** — suggest/assert candidates from DOM diff into emit (trimmed; one class/page).
3. **Heal proposals with Accept/Reject** — align codegen emit with Phase 2 heal-proposal shape so Accept patches **existing** page Java only (no LoginPage2; no silent rewrite). Coordinate with contract `docs/history/handoffs/2026-11/2026-11-02-phase2-api-contract.md` if touching proposal fields.
4. **NL step → validated `By` cached in Java** — natural-language / intent step resolves to a validated locator persisted as field/`By` in the page class.

Still **one Java class per page** (locators + actions + asserts). Non-regress W1/W2 suites.

## Constraints

- Codegen-only paths (+ Assertion/FTL as needed). Backup `_backup\` before live-apply.
- **No push** / no main merge without user OK.
- Serialize Maven (portal may be LIVE :8080 — don’t package portal unless required for tests).
- Patch + SHA256 + samples under `docs/handoff/samples/`; handoff `docs/history/handoffs/2026-11/2026-11-02-codegen-w3.md`.

## Verify (Codegen)

Focused Surefire = W1+W2 set **plus** new `CodegenW3*` tests; report N/0/0/0 + BUILD.

## QA

After Codegen GREEN: re-gate; write `docs/history/handoffs/2026-11/2026-11-02-qa-gates-codegen-w3.md`.
