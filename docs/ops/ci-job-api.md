# CI job API reference

Use this when a pipeline should start an Execute job against a project library and fail the build on a real assertion failure. Public endpoints live under `/api/v1` and require a workspace **service identity** (`tp_svc_…`), never an OWNER token.

## Credentials

1. In the project **Workspace members** panel, create a service identity with role `ADMIN` (operate) or `MEMBER` (read-only).
2. Store the one-time token as `KEEL_SERVICE_TOKEN`. Revoking the identity immediately rejects `/api/v1/**`.
3. Send `Authorization: Bearer $KEEL_SERVICE_TOKEN` and `X-Keel-Requested-With: Keel` on every call.
4. Rate limit: **60 requests per tenant per minute**. Documented on `GET /api/v1/whoami` as `rateLimit`.

MEMBER tokens can poll status; they cannot submit or cancel.

## Idempotent submit

`POST /api/v1/projects/{projectId}/jobs`

```json
{ "kind": "EXECUTE", "credentialProfile": "qa", "environmentRevisionId": "envrev_optional" }
```

- Repeat the same `Idempotency-Key` header to reuse the first job id (`idempotentReplay: true`).
- A different body with the same key returns `409 IDEMPOTENCY_CONFLICT`.
- The job pins the current library (and environment, when supplied). A later library edit does not rewrite this run.

## Poll and cancel

- `GET /api/v1/jobs/{jobId}` — `status`, `parentJobId`, `libraryRevisionId`, `environmentRevisionId`
- `POST /api/v1/jobs/{jobId}/cancel` — ADMIN only

Treat terminal states:

| Status | CI |
|---|---|
| `COMPLETED` | Pass only if the job is not a dry-run simulation. Inspect `/api/jobs/{jobId}` `proofKind` / `proofSource` when using a session cookie; `/api/v1` reports status plus pins. |
| `COMPLETED_WITH_BLOCK` | Fail the build (blocked/unchecked work stayed in the denominator). |
| `FAILED` | Fail the build. |
| `CANCELLED` | Fail unless the pipeline itself cancelled. |
| `QUEUED` / `RUNNING` / `CANCELLING` | Keep polling. |

Do not treat a simulated dry-run pass as green.

## GitHub Actions example

Save the token and portal URL as repository secrets. This workflow is a reference; it is not a launch certification of isolation or Phase 5 gates.

```yaml
name: Keel execute
on:
  workflow_dispatch:
jobs:
  execute:
    runs-on: ubuntu-latest
    steps:
      - name: Whoami
        env:
          KEEL_BASE_URL: ${{ secrets.KEEL_BASE_URL }}
          KEEL_SERVICE_TOKEN: ${{ secrets.KEEL_SERVICE_TOKEN }}
        run: |
          curl -fsS -H "Authorization: Bearer $KEEL_SERVICE_TOKEN" \
            -H "X-Keel-Requested-With: Keel" \
            "$KEEL_BASE_URL/api/v1/whoami"
      - name: Submit execute job
        env:
          KEEL_BASE_URL: ${{ secrets.KEEL_BASE_URL }}
          KEEL_SERVICE_TOKEN: ${{ secrets.KEEL_SERVICE_TOKEN }}
          KEEL_PROJECT_ID: ${{ secrets.KEEL_PROJECT_ID }}
        run: |
          JOB=$(curl -fsS -X POST \
            -H "Authorization: Bearer $KEEL_SERVICE_TOKEN" \
            -H "X-Keel-Requested-With: Keel" \
            -H "Idempotency-Key: ${GITHUB_RUN_ID}" \
            -H "Content-Type: application/json" \
            -d '{"kind":"EXECUTE"}' \
            "$KEEL_BASE_URL/api/v1/projects/$KEEL_PROJECT_ID/jobs")
          echo "$JOB"
          echo "JOB_ID=$(echo "$JOB" | jq -r .jobId)" >> "$GITHUB_ENV"
      - name: Wait for terminal status
        env:
          KEEL_BASE_URL: ${{ secrets.KEEL_BASE_URL }}
          KEEL_SERVICE_TOKEN: ${{ secrets.KEEL_SERVICE_TOKEN }}
        run: |
          for i in $(seq 1 60); do
            BODY=$(curl -fsS -H "Authorization: Bearer $KEEL_SERVICE_TOKEN" \
              -H "X-Keel-Requested-With: Keel" \
              "$KEEL_BASE_URL/api/v1/jobs/$JOB_ID")
            STATUS=$(echo "$BODY" | jq -r .status)
            echo "$STATUS"
            case "$STATUS" in
              COMPLETED) exit 0 ;;
              COMPLETED_WITH_BLOCK|FAILED|CANCELLED) echo "$BODY"; exit 1 ;;
            esac
            sleep 10
          done
          echo "timed out waiting for $JOB_ID"
          exit 1
```

Webhooks (HMAC-SHA256, bounded retries, delivery ids) are available for outbound notification once an explicit destination is configured. Do not send job payloads to an unspecified URL.
