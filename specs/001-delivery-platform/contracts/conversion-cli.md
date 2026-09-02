# Contract: Conversion CLI (pre-portal / worker entry)

## Command

```text
delivery-convert --project-id <id> --mode NEW|UPDATE --excel <path> --base-url <url> [--username <u>] [--password <p>] --work-dir <path> --store-root <path>
```

## Exit codes

| Code | Meaning |
|------|---------|
| 0 | Job completed (may include TODO cases) |
| 2 | Invalid Excel / args |
| 3 | Update with no stored framework |
| 4 | Runtime failure (login/site/AI/browser) |
| 5 | Packaging failure |

## Stdout (JSON line on success)

```json
{
  "jobId": "job_...",
  "status": "COMPLETED",
  "zipPath": ".../versions/v1.zip",
  "passedCount": 8,
  "todoCount": 2,
  "scoreReportPath": ".../docs/AUTOMATION_SCORE.md"
}
```

## Guarantees

- Does not write customer password into the ZIP
- Does not call cloud LLM endpoints
- Emits one CaseOutcome per input TC on COMPLETED
