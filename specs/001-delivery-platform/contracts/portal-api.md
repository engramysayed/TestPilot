# Contract: Portal HTTP API (MVP)

Base path: `/api`  
Auth: session or basic auth (MVP). All project routes require authenticated owner.

## POST /api/projects

Create project.

**Request JSON**
```json
{ "name": "Acme Checkout" }
```

**Response 201**
```json
{ "projectId": "prj_...", "name": "Acme Checkout" }
```

## GET /api/projects/{projectId}

**Response 200**
```json
{
  "projectId": "prj_...",
  "name": "Acme Checkout",
  "hasStoredFramework": true,
  "latestVersion": 2
}
```

## POST /api/projects/{projectId}/jobs

Multipart form:
- `excel` (file) — required
- `baseUrl` (string) — required
- `username` (string) — optional if app has no login
- `password` (string) — optional
- `mode` — `NEW` | `UPDATE`

**Response 202**
```json
{ "jobId": "job_...", "status": "QUEUED" }
```

**Response 400** — invalid Excel or Update without stored framework  
```json
{ "error": "INVALID_EXCEL", "message": "Missing required column: TC_ID" }
```

## GET /api/jobs/{jobId}

**Response 200**
```json
{
  "jobId": "job_...",
  "projectId": "prj_...",
  "mode": "NEW",
  "status": "RUNNING",
  "passedCount": 3,
  "todoCount": 1,
  "message": "Authoring TC_004",
  "progressCurrent": 4,
  "progressTotal": 10
}
```

## GET /api/jobs/{jobId}/download

**Response 200** — `application/zip` when `status=COMPLETED`  
**Response 409** — job not completed  
**Response 404** — unknown job

## Error conventions

- `4xx` with `{ error, message }` for client mistakes
- `5xx` with generic message; details in server logs only (no secrets)
