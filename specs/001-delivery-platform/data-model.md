# Data Model: 001-delivery-platform

## Entities

### Account
- **Fields**: `accountId`, `email`, `passwordHash` (or external auth id), `createdAt`
- **Rules**: Must authenticate to access projects
- **Notes**: MVP simple session auth

### Project
- **Fields**: `projectId`, `accountId`, `name`, `baseUrl` (last used), `createdAt`, `updatedAt`
- **Relationships**: owns many Jobs; has one current StoredFramework; has LocatorMap
- **Rules**: Update forbidden if no StoredFramework exists

### ManualTestCase (from Excel row)
- **Fields**: `tcId`, `title`, `preconditions`, `steps`, `expectedResult`, `priority`, `tags`, `contentHash`
- **Rules**: `tcId`, `title`, `steps`, `expectedResult` required; `tcId` unique within upload; header set must match template
- **Relationships**: input to ConversionJob; maps to CaseOutcome

### ConversionJob
- **Fields**: `jobId`, `projectId`, `mode` (`NEW` | `UPDATE`), `status` (`QUEUED` | `RUNNING` | `COMPLETED` | `FAILED`), `excelPath`, `baseUrl`, `passedCount`, `todoCount`, `errorMessage`, `createdAt`, `startedAt`, `finishedAt`, `artifactZipPath`, `scoreReportPath`
- **Rules**: Credentials used at runtime are not persisted into ZIP; optional encrypted job-scoped secret store is out of MVP unless needed for retry
- **Transitions**: `QUEUED → RUNNING → COMPLETED | FAILED`

### CaseOutcome
- **Fields**: `tcId`, `status` (`PASSED` | `TODO`), `failureReason`, `evidenceDir`, `provenSteps[]`
- **Rules**: Every uploaded case produces exactly one CaseOutcome for a completed job
- **Relationships**: feeds CodeWriter

### ProvenStep
- **Fields**: `tcId`, `pageName`, `actionType`, `action`, `locatorStrategy`, `locatorValue`, `value`, `assertionType`, `assertionExpected`, `validated` (bool), `rationale`
- **Rules**: Locators must pass LocatorValidator before `validated=true`; only validated steps become durable test actions

### StoredFramework
- **Fields**: `projectId`, `rootPath`, `version`, `updatedAt`
- **Rules**: Created/replaced on successful New; merged on Update
- **Contains**: copy of customer-framework-template + generated pages/tests

### LocatorMap
- **Fields**: map of keys → `{ strategy, value, fallbacks[], pageName, tcIds[], updatedAt }`
- **Rules**: Consulted before AI on Update for unchanged content hashes

### AutomationPackage (ZIP artifact)
- **Fields**: `version`, `zipPath`, `passedCount`, `todoCount`, `createdAt`, `jobId`
- **Rules**: Must include score doc; must not include filled password properties

## Validation summary

| Rule | Source |
|------|--------|
| Excel schema | Constitution II / FR-002–003 |
| All cases in package | Constitution I / FR-008 |
| Pass only after live validate | Constitution IV / FR-006–007 |
| No password in ZIP | FR-014 / SC-007 |
| Update requires prior framework | FR-005 / edge cases |

## State diagram (Job)

```text
QUEUED --start--> RUNNING --success--> COMPLETED
                     |
                     +--error------> FAILED
```
