# P2 — Projects hub v1 (full §4.1)

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P2**  
**Depends on:** P0 roadmap approved; P1 Guide AI CSV (optional overlap)  

---

## 1. Goal

Make **Projects** the **only** place customers configure a project (name, base URL, named login profiles) and manage **user-facing automation artifacts** (packages, page classes, test classes). **Upload** becomes project + credential profile + Excel only — no re-entering URL or passwords.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| P2 scope | **Full §4.1** — settings, creds, archive/delete, automation tree + selective delete, Upload wired |
| Upload config | **Hub is source of truth** — pick project + named credential only |
| Password UX | **Write-only** — masked `••••`; set new password on edit; never return stored password to browser |
| Project removal | **Archive** (hide from default list) **and** hard delete |
| Files browser | **User-facing hierarchy** — packages, pages, tests; **hide** evidence, IR, locator maps, internal JSON |
| TC detail | Keep step timeline; **remove screenshot gallery** from portal |
| UI shell | **Tabbed project detail** (Settings \| Automation \| Test cases) — extend `/projects/{id}` |
| Viewer roles / Git sync | Out of scope (roadmap phase 2) |

---

## 3. Scope

### In scope

- Extend `ProjectEntity`: `baseUrl`, `archived`, `archivedAt`
- New `project_credentials` table + encrypted passwords (`JobSecretCrypto`)
- APIs: PATCH project, credential CRUD, artifacts tree/preview/delete/download
- Projects list: archive toggle, create with optional base URL, archive + hard delete
- Project detail tabs: Settings, Automation, Test cases (no evidence)
- Upload: project + credential profile + Excel; read-only base URL summary
- Legacy: backfill `baseUrl` from latest job when missing
- Deprecate `GET /api/account/targets` password return for Upload autofill
- Tests: credentials, PATCH, artifacts allowlist, Upload job wiring, ownership

### Out of scope

- Nav IA / Automate shell (P3)
- Generate / Execute flows (P6–P8)
- Sharing (owner vs viewer)
- In-portal Java editing or re-conversion from artifact delete alone
- Evidence / IR browser
- Dashboard changes (P4)

---

## 4. Data model

### 4.1 `projects` (extend `ProjectEntity`)

| Column | Type | Notes |
|--------|------|-------|
| `baseUrl` | VARCHAR | Canonical site URL; nullable on create; **required before starting a job** |
| `archived` | BOOLEAN | Default `false` |
| `archivedAt` | TIMESTAMP | Set when archived |

Existing: `projectId`, `name`, `ownerUserId`, `latestVersion`.

### 4.2 `project_credentials` (new)

| Column | Notes |
|--------|-------|
| `id` | PK |
| `projectId` | FK → projects.projectId |
| `profileName` | Unique per project (e.g. `normal_user`, `super_admin`) |
| `username` | Plaintext |
| `passwordCipher` | AES-GCM via `JobSecretCrypto` |
| `createdAt`, `updatedAt` | Audit |

Rules:
- API never returns decrypted password.
- PATCH with omitted/blank password → keep existing cipher.
- POST requires password on create.

### 4.3 Disk layout (unchanged; visibility rules new)

```text
<domain>/<projectId>/
  project.json
  framework/          # visible: pages/*.java, tests/generated|todo/*.java
  versions/vN.zip     # visible: packages
  ir/                 # hidden from hub browser (TC API reads internally)
  evidence/           # hidden from hub; no screenshot UI
  locator-map.json, domain-locator-memory.json, tc-hashes.json  # hidden
```

`PortalStore.projectDiskRoot(projectId)` uses **`ProjectEntity.baseUrl`** as domain hint (fallback: latest job URL for legacy rows).

---

## 5. APIs

### 5.1 Projects

| Method | Path | Body / query | Response |
|--------|------|--------------|----------|
| `GET` | `/api/projects` | `?includeArchived=false` | List owned; default excludes archived |
| `POST` | `/api/projects` | `{ name, baseUrl? }` | Create |
| `GET` | `/api/projects/{id}` | — | Metadata incl. `baseUrl`, `archived`, `latestVersion`, `hasStoredFramework` |
| `PATCH` | `/api/projects/{id}` | `{ name?, baseUrl?, archived? }` | Updated project |
| `DELETE` | `/api/projects/{id}` | — | Hard delete DB + disk (existing behavior) |

### 5.2 Credentials

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/projects/{id}/credentials` | `[{ profileName, username, hasPassword: true }]` |
| `POST` | `/api/projects/{id}/credentials` | `{ profileName, username, password }` |
| `PATCH` | `/api/projects/{id}/credentials/{profileName}` | `{ username?, password? }` — blank password keeps old |
| `DELETE` | `/api/projects/{id}/credentials/{profileName}` | Remove profile |

### 5.3 Automation artifacts

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/projects/{id}/artifacts` | Structured tree (see §5.4) |
| `GET` | `/api/projects/{id}/artifacts/preview?path=` | Read-only text (Java); max size cap |
| `GET` | `/api/projects/{id}/artifacts/download?path=` | ZIP download only |
| `DELETE` | `/api/projects/{id}/artifacts?path=` | Delete one allowlisted file |

**Allowlist (delete, preview, download where noted):**

- `versions/v*.zip` — download + delete
- `framework/**/pages/*.java` — preview + delete
- `framework/**/tests/generated/*.java` — preview + delete
- `framework/**/tests/todo/*.java` — preview + delete

**Denylist:** `evidence/**`, `ir/**`, `*.json` internal maps, `project.json`, any path with `..`, symlinks, paths outside project root.

**Tree JSON shape:**

```json
{
  "packages": [{ "path": "versions/v3.zip", "label": "v3", "sizeBytes": 123456 }],
  "pages": [{ "path": "framework/src/main/java/project/pages/LoginPage.java", "label": "LoginPage" }],
  "tests": {
    "generated": [{ "path": "...", "label": "TC_01_Generated" }],
    "todo": [{ "path": "...", "label": "TC_02_Todo" }]
  }
}
```

Server scans disk under allowlist roots only — never expose raw directory listing.

### 5.4 Jobs (Upload)

**Change** `POST /api/projects/{id}/jobs`:

| Field | Required | Notes |
|-------|----------|-------|
| `excel` | yes | Multipart |
| `credentialProfile` | conditional | Required if project has ≥1 profile; optional if zero profiles (public sites) |
| `mode`, `finalRevise` | as today | — |

**Removed from client:** `baseUrl`, `username`, `password`.

Server resolves URL from `ProjectEntity.baseUrl` and credentials from selected profile. Returns `400` if project has no `baseUrl`. Job entity still stores resolved values (encrypted) for audit/history.

### 5.5 TC / evidence

- Keep `GET .../tcs` and `GET .../tcs/{tcId}` for step timeline.
- **Remove screenshot UI** from `project-detail.html`.
- `GET .../screenshots/{file}` → **`410 Gone`** for portal (or remove route); evidence remains on disk for engine use.

### 5.6 Deprecated

- `GET /api/account/targets` — remove or return empty list in P2 (Upload no longer uses saved targets).

---

## 6. UX

### 6.1 `/projects`

- List active projects; toggle **Show archived**.
- Row: name, projectId, base URL (truncated), version, last modified.
- Actions: **Open** | **Archive** | **Delete permanently** (strong confirm).
- Create: name + optional base URL → open project **Settings** tab (not redirect to Upload).

### 6.2 `/projects/{id}` — tabs

**Settings**
- Rename, base URL editor.
- Credential profiles table (add / edit / delete).
- Optional **Import from last job** — username only; user must set password.
- Archive project | Delete permanently.

**Automation**
- Sections: **Packages** | **Pages** | **Tests** (Generated / Todo).
- Per row: Preview | Download (ZIP) | Delete (confirm).
- Delete disabled when job **RUNNING** (tooltip).
- Preview: read-only first ~80 lines in slide-over or panel.

**Test cases**
- Existing TC accordion: title, status, proven steps, failure reason.
- **No screenshots.**

### 6.3 `/upload`

- Project dropdown (required).
- Credential profile dropdown (from selected project).
- Read-only base URL when project selected (+ link to Settings).
- Excel file only.
- Block submit if project has no base URL.
- Remove: base URL / username / password fields, saved-targets picker, inline create-project.

---

## 7. Security & errors

| Case | HTTP | User message |
|------|------|--------------|
| Not owner | `404` | Unknown project |
| Invalid artifact path | `400 BAD_PATH` | Path not allowed |
| Delete during running job | `409 JOB_RUNNING` | Wait for job to finish |
| Duplicate profile name | `409 DUPLICATE_PROFILE` | Name already used |
| Job without project baseUrl | `400 MISSING_BASE_URL` | Set base URL in Settings |
| Archive with running job | `409 JOB_RUNNING` | — |

- Passwords never logged or returned in API JSON.
- Path validation: normalize, resolve real path, ensure under project root.

---

## 8. Legacy migration

- On `GET /api/projects/{id}` or first `PATCH`: if `baseUrl` null, set from **latest job** `baseUrl` for that project (persist once).
- Credentials: no auto-import of passwords from job history; optional UI pre-fill username from last job only.

---

## 9. Testing

| Test class | Covers |
|------------|--------|
| `ProjectCredentialsApiTest` | CRUD; no password in GET; job uses profile |
| `ProjectPatchApiTest` | Rename, baseUrl, archive list filter |
| `ProjectArtifactsApiTest` | Tree shape; allowlist delete; deny evidence/ir |
| `UploadJobApiTest` (extend `PortalApiTest`) | Rejects client baseUrl; uses project config |
| `AuthOwnershipTest` | Credentials + artifacts cross-user denied |

Manual: Settings → add creds → Upload → convert → Automation tab shows pages/tests; delete old ZIP; archive project hidden from default list.

---

## 10. Key files (implementation)

| Area | Paths |
|------|-------|
| Entity / repo | `ProjectEntity.java`, new `ProjectCredentialEntity.java`, repositories |
| Services | `PortalStore.java`, new `ProjectCredentialService.java`, `ProjectArtifactService.java` |
| API | `ProjectController.java`, `JobController.java`, deprecate `AccountController` targets |
| UI | `projects.html`, `project-detail.html`, `upload.html`, `portal.css` |
| Security | `JobSecretCrypto.java` (reuse) |
| Tests | `src/test/java/delivery/portal/api/*` |

---

## 11. Success criteria

1. User never types base URL or password on Upload after configuring a project.
2. User can manage multiple named credential profiles per project with write-only password UX.
3. User sees packages / pages / tests hierarchy and can delete selected artifacts.
4. Evidence and internal store files are not browsable in the hub.
5. Archive hides project from default list; hard delete still available.
6. Existing projects gain `baseUrl` from latest job where possible.

---

## 12. Spec self-review

- [x] Locked decisions reflected (full §4.1, Upload A, passwords A, archive B, hierarchy per user intent)
- [x] No TBD on scope boundaries
- [x] Consistent with roadmap “config only in Projects”
- [x] Security: allowlist paths, no password leak, owner-only
- [x] Legacy backfill defined
- [x] TC screenshots explicitly removed from UI; evidence hidden
- [x] Single-phase spec sized for one implementation plan (may be multi-task plan)
