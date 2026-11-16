# Unified TC Import — Verification Notes

**Date:** 2026-09-02  
**Task:** T009 Full verification bundle + manual smoke  
**Commit:** None (per plan constraints)

---

## Automated Maven bundle

**Command:**

```powershell
cd D:\priv\testpilot\TestPilot
mvn -q test "-Dtest=TcImportRepairTest,TcImportServiceTest,GenerateImportApiTest,GenerateMvcTest,GenerateJsonPromptResourceTest,GeneratePromptResourceTest,KeelPathCaseFilterTest,ExcelUploadQualityGateApiTest,FacebookLoginNegativeGenerateTest,GenerateAuthoringRulesTest,GenerateQualityGateTest,TcGenerateServiceSaveComparedGateTest"
```

**Result:** PASS (exit 0, ~17s)

| Class | Tests | Failures | Errors | Skipped |
|-------|------:|---------:|-------:|--------:|
| `TcImportRepairTest` | 3 | 0 | 0 | 0 |
| `TcImportServiceTest` | 2 | 0 | 0 | 0 |
| `GenerateImportApiTest` | 2 | 0 | 0 | 0 |
| `GenerateMvcTest` | 8 | 0 | 0 | 0 |
| `GenerateJsonPromptResourceTest` | 2 | 0 | 0 | 0 |
| `GeneratePromptResourceTest` | 2 | 0 | 0 | 0 |
| `KeelPathCaseFilterTest` | 4 | 0 | 0 | 0 |
| `ExcelUploadQualityGateApiTest` | 4 | 0 | 0 | 0 |
| `FacebookLoginNegativeGenerateTest` | 3 | 0 | 0 | 0 |
| `GenerateAuthoringRulesTest` | 8 | 0 | 0 | 0 |
| `GenerateQualityGateTest` | 9 | 0 | 0 | 0 |
| `TcGenerateServiceSaveComparedGateTest` | 2 | 0 | 0 | 0 |
| **Total (TestSuite)** | **49** | **0** | **0** | **0** |

### Coverage by phase

| Phase | Tests exercised |
|-------|-----------------|
| T001 Repair | `TcImportRepairTest` |
| T002 Import service | `TcImportServiceTest`, `FacebookLoginNegativeGenerateTest` |
| T003 Paste API | `GenerateImportApiTest` |
| T004 Generate UI | `GenerateMvcTest` (`generate_hasPasteImportControls`) |
| T005 Prompts | `GenerateJsonPromptResourceTest`, `GeneratePromptResourceTest` |
| T006 Compare save gate | `TcGenerateServiceSaveComparedGateTest` |
| T007 Excel upload gate | `ExcelUploadQualityGateApiTest` |
| T008 KeelPath blank | `KeelPathCaseFilterTest` |
| Shared gate/rules | `GenerateAuthoringRulesTest`, `GenerateQualityGateTest` |

---

## Manual smoke checklist

**Status:** BLOCKED — portal not running in this session (no live server on `/generate`).

| # | Step | Status | Notes |
|---|------|--------|-------|
| 1 | Restart portal; hard-refresh `/generate` | BLOCKED | Requires manual `mvn spring-boot:run` or deployed instance |
| 2 | Paste Cursor-style JSON or repaired CSV into **Import** | BLOCKED | Automated: `GenerateImportApiTest`, `TcImportServiceTest` |
| 3 | Confirm preview + workbook saved | BLOCKED | Automated: import API + service tests |
| 4 | Execute → **Use latest generated workbook** → job queues | BLOCKED | Automated: `UseGeneratedWorkbookApiTest` (not in bundle; prior task coverage) |
| 5 | Paste known-bad Phone-field snippet → Import shows QUALITY_GATE (no save) | BLOCKED | Automated: `GenerateImportApiTest`, gate tests |
| 6 | Optional: copy-prompt → Cursor → paste back into Import | BLOCKED | Prompt resources verified in bundle |

**To complete manual smoke:** start portal, run steps 1–5 (and optionally 6), update this table with PASS/FAIL per step.

---

## Observations

- Spring Boot logged duplicate `org.json.JSONObject` on classpath (pre-existing; tests unaffected).
- Java agent / dynamic loading warnings from Mockito/ByteBuddy (pre-existing; tests unaffected).
