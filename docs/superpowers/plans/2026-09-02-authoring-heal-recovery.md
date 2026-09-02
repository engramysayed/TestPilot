# Authoring Gate + Editable TC + Heal Recovery — Implementation Plan

> **For agentic workers:** Use subagent-driven-development or execute inline. Checkbox steps.

**Goal:** Fix empty-field/testData disasters, show coverage notes, add click-to-edit TC preview, then ship structured heal recovery invent.

**Architecture:** Phase A hardens gate/invent/leave-empty. Phase B adds Generate preview editor wired to workbook save. Phase C extends Cursor invent JSON + HealCascade recovery loop with DOM validation and automation notes evidence.

**Tech Stack:** Java 21, TestNG, Thymeleaf generate.html, existing HealCascade / FreeInventHealer / CursorHealClient.

## Global Constraints

- Do not commit unless user asks.
- Blank KeelPath semantics unchanged.
- Recovery invent must validate locators against live page; never free-hallucinate selectors.
- Prefer TDD.

---

### Task A1: Coverage notes on import

**Files:** `TcImportService.java`, `GeneratedTcJsonParser`, `TcImportServiceTest`, `generate.html` if needed

- ParseResult already has coverageNotes — pass through `toImportPayload`.
- Test: import golden JSON with notes → payload contains coverageNotes.

### Task A2–A3: Gate Leave-empty + vague assert

**Files:** `GenerateAuthoringRules.java`, tests

- Reject non-blank TestData on Leave-empty steps (including `<…>`).
- Expand VAGUE_ASSERT pattern for `validation or error`, `clear validation`.

### Task A4–A5: Never type angle tokens; Leave-empty → clear

**Files:** `DummyValueInventor.java`, `StepIntentBinder.java` / prove bind path, tests

- `concreteOrNull`: values matching `^<[^>]+>$` → null (unspecified).
- Leave/keep empty → ProvenStep action `clear` (or type "") with empty value; ignore misaligned TestData.

### Task B1–B3: Editable TC modal

**Files:** `generate.html`, `portal.css`, `GeneratedWorkbookController` (save full row edits), MVC tests

- Click row → modal with all columns editable.
- Save → update lastResult + API persist.
- Coverage notes display from import.

### Task C1–C3: Recovery invent

**Files:** `CursorHealClient`, `FreeInventHealer` / new `RecoveryInventHealer`, `HealCascade`, evidence writer, tests

- Structured JSON schema `mode: recovery`.
- Execute validated recovery steps; re-run intent; write automationNotes to evidence.
- Cap: 1 recovery / intent, ≤3 steps.

### Task V: Verification bundle + notes doc

---

## Tasks checklist

See `docs/superpowers/plans/2026-09-02-authoring-heal-recovery-tasks.md`
