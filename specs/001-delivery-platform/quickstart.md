# Quickstart Validation: 001-delivery-platform

Validate the feature without implementing full portal first. Follow constitution phases.

## Prerequisites

- JDK 24, Maven 3.x
- Chrome or Edge installed
- Ollama running locally with configured model (`curl http://127.0.0.1:11434/api/tags`)
- Sample app reachable (e.g. SauceDemo) OR customer test env
- Blank/filled Excel conforming to [contracts/excel-template.md](./contracts/excel-template.md)

## Phase A — Template builds

```bash
cd customer-framework-template
mvn -B test
```

**Expect:** BUILD SUCCESS (empty or sample tests).

## Phase B — Excel + validators (unit)

```bash
mvn -B -Dtest=ExcelTcReaderTest,LocatorValidatorTest test
```

**Expect:** PASS; invalid fixture rejected.

## Phase C — CLI New ZIP (engine smoke)

1. Configure `delivery.properties` with `LOCAL_LLM_*`, browser, store root.
2. Run conversion CLI per [contracts/conversion-cli.md](./conversion-cli.md) with `--mode NEW`.
3. Unzip artifact.

**Expect:**
- ZIP contains `docs/AUTOMATION_SCORE.md`, CI workflow, `webapp.properties.example`
- No real password file inside ZIP
- `tests/generated` and/or `tests/todo` cover every Excel `TC_ID`
- After copying example config and filling secrets locally: `mvn test` runs generated tests

## Phase D — Update

1. Run CLI `--mode UPDATE` with Excel adding one new `TC_ID`.
2. Download/unzip new version.

**Expect:** Prior passed tests still present; new case added as generated or TODO; version bumped under store.

## Phase E — Portal (after CLI works)

1. Start portal app.
2. Create project → upload Excel → NEW → poll job → download (see [contracts/portal-api.md](./portal-api.md)).

**Expect:** SC-001/SC-005/SC-006 style behavior: one session to package; invalid Excel rejected fast; status updates visible.

## Mapping to success criteria

| Criterion | How to verify here |
|-----------|-------------------|
| SC-001 | Phase C or E happy path |
| SC-002 | Count Excel rows vs generated+todo classes |
| SC-003 | `mvn test` after local config |
| SC-004 | Phase D |
| SC-005 | Upload bad Excel |
| SC-006 | Job status polling |
| SC-007 | Inspect ZIP for password properties |
| SC-008 | Stakeholder demo checklist |

## References

- Spec: [spec.md](./spec.md)
- Plan: [plan.md](./plan.md)
- Data model: [data-model.md](./data-model.md)
- Detailed engineering tasks: `docs/superpowers/plans/2026-08-04-testpilot-delivery-platform.md`
