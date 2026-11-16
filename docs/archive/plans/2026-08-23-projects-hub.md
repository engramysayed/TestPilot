# Projects hub v1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not commit unless the user asks.**

**Goal:** Projects hub as the only config surface (name, base URL, named credentials, automation artifact tree) and Upload wired to project + credential profile only.

**Architecture:** Extend JPA `ProjectEntity` + new `ProjectCredentialEntity`; add `ProjectCredentialService` and `ProjectArtifactService` with path allowlists; extend `ProjectController` / change `JobController`; tabbed `project-detail.html` + simplified `upload.html`. Reuse `JobSecretCrypto` for credential passwords.

**Tech Stack:** Java 21, Spring Boot 3.3, JPA/H2, Thymeleaf, TestNG + MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-23-projects-hub-design.md`

## Global Constraints

- Upload: project + `credentialProfile` + Excel only — no client `baseUrl` / `username` / `password`.
- Credential passwords: write-only in API/UI; encrypt with `JobSecretCrypto`; never return decrypted password.
- Artifact browser: packages / pages / tests only — hide `evidence/`, `ir/`, internal JSON.
- Archive + hard delete; default list excludes archived (`?includeArchived=true` to show).
- Delete artifacts blocked when project has a **RUNNING** job (`409 JOB_RUNNING`).
- Legacy: backfill `ProjectEntity.baseUrl` from latest job when null (on GET/PATCH).
- Do not commit unless asked.
- Tests: `mvn -q "-Dmaven.compiler.release=21" -Dtest=… test`

---

## File map

| File | Responsibility |
|------|----------------|
| Modify: `ProjectEntity.java`, `ProjectRecord.java`, `ProjectRepository.java` | baseUrl, archived, archivedAt |
| Create: `ProjectCredentialEntity.java`, `ProjectCredentialRepository.java` | Named creds |
| Create: `ProjectCredentialService.java` | Encrypt/decrypt for job start only |
| Create: `ProjectArtifactService.java` | Tree, preview, download, delete with allowlist |
| Modify: `PortalStore.java` | PATCH, archive, backfill, `projectDiskRoot` from entity baseUrl, `hasRunningJob` |
| Modify: `ProjectController.java` | PATCH, credentials + artifacts endpoints, enriched GET/list |
| Modify: `JobController.java` | Resolve URL/creds from project |
| Modify: `AccountController.java` | `GET /targets` → empty list |
| Modify: `projects.html`, `project-detail.html`, `upload.html`, `portal.css` | Hub + Upload UX |
| Create: `ProjectPatchApiTest.java`, `ProjectCredentialsApiTest.java`, `ProjectArtifactsApiTest.java` | API tests |
| Modify: `PortalApiTest.java`, `AuthOwnershipTest.java` | Job wiring + ownership |

---

### Task 1: Project schema, PATCH, archive, backfill

**Files:**
- Modify: `src/main/java/delivery/portal/persistence/ProjectEntity.java`
- Modify: `src/main/java/delivery/portal/model/ProjectRecord.java`
- Modify: `src/main/java/delivery/portal/persistence/ProjectRepository.java`
- Modify: `src/main/java/delivery/portal/service/PortalStore.java`
- Modify: `src/main/java/delivery/portal/api/ProjectController.java`
- Create: `src/test/java/delivery/portal/api/ProjectPatchApiTest.java`

**Interfaces:**
- `PortalStore.updateOwnedProject(projectId, ownerUserId, PatchProjectRequest)` → `Optional<ProjectRecord>`
- `PortalStore.listProjects(ownerUserId, includeArchived)` → `List<ProjectRecord>`
- `PortalStore.ensureBaseUrlBackfill(projectId)` — persist from latest job if null
- `ProjectRecord` fields: `baseUrl`, `archived`, `archivedAt`
- `PATCH /api/projects/{id}` body: `{ "name"?, "baseUrl"?, "archived"?: boolean }`

- [ ] **Step 1: Write failing test**

```java
package delivery.portal.api;

import delivery.portal.PortalApplication;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:projectpatch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-patch",
        "delivery.work-dir=./target/test-delivery-work-patch"
})
@AutoConfigureMockMvc
public class ProjectPatchApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Patch Me\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void patch_renameAndBaseUrl() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"baseUrl\":\"https://app.example.com/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.baseUrl").value("https://app.example.com/"));
    }

    @Test
    public void archive_hidesFromDefaultList() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId=='" + id + "')]").doesNotExist());

        mockMvc.perform(get("/api/projects?includeArchived=true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId=='" + id + "')].archived").value(true));
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=ProjectPatchApiTest test
```

Expected: FAIL (`patch` unsupported / missing fields).

- [ ] **Step 3: Implement**

Add to `ProjectEntity`:
```java
@Column(length = 2048)
private String baseUrl;
@Column(nullable = false)
private boolean archived = false;
private Instant archivedAt;
// getters/setters
```

`ProjectRepository`: add `findByOwnerUserIdAndArchivedOrderByIdDesc(ownerId, archived)`.

`PortalStore`:
- Persist `baseUrl` in `createProject` when provided in request (today only used for name hint).
- `updateOwnedProject` — rename, set baseUrl, archive (set `archivedAt` when true, clear when false); reject archive if `hasRunningJob(projectId)`.
- `ensureBaseUrlBackfill` — if entity.baseUrl blank, copy from `latestBaseUrlHint`, save.
- `projectDiskRoot` — prefer entity `baseUrl` over latest job hint.

`ProjectController`:
- Add `@PatchMapping("/{projectId}")` with record `PatchProjectRequest(String name, String baseUrl, Boolean archived)`.
- `GET` list accepts `@RequestParam(defaultValue = "false") boolean includeArchived`.
- Enrich GET responses with `baseUrl`, `archived`.

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=ProjectPatchApiTest test
```

- [ ] **Step 5: Commit only if asked**

---

### Task 2: Named credentials API

**Files:**
- Create: `src/main/java/delivery/portal/persistence/ProjectCredentialEntity.java`
- Create: `src/main/java/delivery/portal/persistence/ProjectCredentialRepository.java`
- Create: `src/main/java/delivery/portal/service/ProjectCredentialService.java`
- Modify: `src/main/java/delivery/portal/api/ProjectController.java` (credential routes)
- Create: `src/test/java/delivery/portal/api/ProjectCredentialsApiTest.java`

**Interfaces:**
- `ProjectCredentialService.list(projectId)` → `List<CredentialSummary>` (`profileName`, `username`, `hasPassword=true`)
- `ProjectCredentialService.create(projectId, profileName, username, password)`
- `ProjectCredentialService.update(projectId, profileName, username, optionalPassword)`
- `ProjectCredentialService.delete(projectId, profileName)`
- `ProjectCredentialService.resolveForJob(projectId, profileName)` → `ResolvedCredential(username, passwordPlain)` — internal only
- Routes under `/api/projects/{id}/credentials`

- [ ] **Step 1: Write failing test**

```java
@Test
public void createList_neverReturnsPassword() throws Exception {
    String id = createProjectWithBaseUrl();
    mockMvc.perform(post("/api/projects/" + id + "/credentials")
                    .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                    .header("X-Keel-Requested-With", "Keel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"profileName\":\"normal_user\",\"username\":\"alice\",\"password\":\"Secret1!\"}"))
            .andExpect(status().isCreated());

    mockMvc.perform(get("/api/projects/" + id + "/credentials")
                    .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                    .header("X-Keel-Requested-With", "Keel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].profileName").value("normal_user"))
            .andExpect(jsonPath("$[0].username").value("alice"))
            .andExpect(jsonPath("$[0].hasPassword").value(true))
            .andExpect(jsonPath("$[0].password").doesNotExist());
}

@Test
public void patch_blankPassword_keepsExisting() throws Exception {
    // create profile, PATCH with username only, then job-start test in Task 4 verifies password still works
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=ProjectCredentialsApiTest test
```

- [ ] **Step 3: Implement entity + service + controller**

`ProjectCredentialEntity` unique constraint on `(projectId, profileName)`. Encrypt password with `JobSecretCrypto.encrypt` on write; decrypt only in `resolveForJob`.

Duplicate profile → `409` with code `DUPLICATE_PROFILE`.

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit only if asked**

---

### Task 3: Automation artifacts API

**Files:**
- Create: `src/main/java/delivery/portal/service/ProjectArtifactService.java`
- Modify: `src/main/java/delivery/portal/api/ProjectController.java` (or `ProjectArtifactsController.java`)
- Modify: `src/main/java/delivery/portal/service/PortalStore.java` — `hasRunningJob(projectId)`
- Create: `src/test/java/delivery/portal/api/ProjectArtifactsApiTest.java`

**Interfaces:**
- `ProjectArtifactService.buildTree(Path projectRoot)` → JSON map with `packages`, `pages`, `tests.generated`, `tests.todo`
- `ProjectArtifactService.validateRelativePath(String path)` → normalized relative path or throw `BadPathException`
- `GET /api/projects/{id}/artifacts`
- `GET /api/projects/{id}/artifacts/preview?path=…` (max 64 KiB)
- `GET /api/projects/{id}/artifacts/download?path=…` (ZIP only)
- `DELETE /api/projects/{id}/artifacts?path=…` — `409` if running job

- [ ] **Step 1: Write failing test**

Seed fixture under `./target/test-delivery-store-artifacts/`:
```text
example-com/prj_test/
  versions/v1.zip
  framework/src/main/java/project/pages/LoginPage.java
  framework/src/test/java/project/tests/generated/TC_01.java
  evidence/hidden.png
  ir/draft.json
```

Test:
- `GET /artifacts` lists zip + LoginPage + TC_01; does **not** list evidence or ir.
- `DELETE ?path=evidence/hidden.png` → `400`
- `DELETE ?path=versions/v1.zip` → `200` (no running job)

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=ProjectArtifactsApiTest test
```

- [ ] **Step 3: Implement allowlist scanner**

Path rules (relative to project root):
- `versions/v*.zip`
- `framework/**/pages/*.java`
- `framework/**/tests/generated/*.java`
- `framework/**/tests/todo/*.java`

Use `Path.normalize()`, reject `..`, resolve under root with `startsWith` check, reject symlinks (`Files.isSymbolicLink`).

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit only if asked**

---

### Task 4: Job start from project config + deprecate targets

**Files:**
- Modify: `src/main/java/delivery/portal/api/JobController.java`
- Modify: `src/main/java/delivery/portal/api/AccountController.java`
- Modify: `src/main/java/delivery/portal/api/ProjectController.java` — screenshot → `410 Gone`
- Modify: `src/test/java/delivery/portal/api/PortalApiTest.java`
- Modify: `src/test/java/delivery/portal/api/AuthOwnershipTest.java`

**Interfaces:**
- `POST /api/projects/{id}/jobs` params: `excel`, `credentialProfile` (optional if zero profiles), `mode`, `finalRevise`
- Server reads `baseUrl` from project entity (after backfill); `400 MISSING_BASE_URL` if blank
- If project has ≥1 credential profile, `credentialProfile` required; resolve username/password via `ProjectCredentialService.resolveForJob`

- [ ] **Step 1: Update failing `PortalApiTest`**

Replace job multipart:
```java
// Before starting job, PATCH baseUrl
mockMvc.perform(patch("/api/projects/" + projectId) ... content("{\"baseUrl\":\"https://example.com/\"}"));

// Optional: add credential + use credentialProfile param instead of username/password
mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
        .file(excel)
        .param("credentialProfile", "normal_user")
        .param("mode", "NEW") ...);
```

Add test: job without baseUrl → `400`.

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=PortalApiTest test
```

- [ ] **Step 3: Implement JobController changes**

Remove `@RequestParam baseUrl/username/password`. Inject `ProjectCredentialService`. Call `store.ensureBaseUrlBackfill(projectId)` before validation.

`AccountController.targets()` → `return Map.of("targets", List.of());`

Screenshot route → `ResponseEntity.status(HttpStatus.GONE).body(new ApiError("GONE", "Screenshots removed from portal").asMap())`

- [ ] **Step 4: Extend `AuthOwnershipTest`**

Cross-user cannot GET/POST/DELETE another user's credentials or artifacts.

- [ ] **Step 5: Run suite**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=PortalApiTest,AuthOwnershipTest,ProjectPatchApiTest,ProjectCredentialsApiTest,ProjectArtifactsApiTest test
```

Expected: PASS.

- [ ] **Step 6: Commit only if asked**

---

### Task 5: Projects list UI

**Files:**
- Modify: `src/main/resources/templates/projects.html`
- Modify: `src/main/resources/static/css/portal.css` (minimal row/archive styles)

**Interfaces:** Consumes Task 1 APIs (`GET /api/projects`, `PATCH` archive, `POST` create with optional baseUrl).

- [ ] **Step 1: Update create form** — name + base URL fields; on success navigate to `/projects/{id}?tab=settings` (not `/upload`).

- [ ] **Step 2: Add archive toggle** — checkbox “Show archived”; refetch with `?includeArchived=true`.

- [ ] **Step 3: Row actions** — Open | Archive | Delete permanently (confirm modals).

- [ ] **Step 4: Display** base URL (truncated), version, last modified from enriched list payload.

- [ ] **Step 5: Manual verify** on `/projects`.

- [ ] **Step 6: Commit only if asked**

---

### Task 6: Project detail — tabbed hub

**Files:**
- Modify: `src/main/resources/templates/project-detail.html`
- Modify: `src/main/java/delivery/portal/web/PortalUiController.java` (optional `tab` query param)
- Modify: `src/main/resources/static/css/portal.css`

**Interfaces:** Consumes Tasks 1–3 APIs.

- [ ] **Step 1: Add tab bar** — Settings | Automation | Test cases (`?tab=settings|automation|tcs`).

- [ ] **Step 2: Settings tab** — PATCH name/baseUrl; credentials table (add/edit/delete modals); archive + delete; optional “Import username from last job” (GET project metadata or small helper endpoint returning username only from latest job — implement as part of Settings JS calling existing job list filtered by projectId, or add `GET /api/projects/{id}/last-job-hints` returning `{ username }` only).

- [ ] **Step 3: Automation tab** — fetch `/artifacts`; render Packages / Pages / Tests sections; Preview panel; Delete with confirm; Download for ZIP; disable delete when `GET /api/jobs` shows RUNNING for this projectId.

- [ ] **Step 4: Test cases tab** — move existing TC accordion here; **remove screenshot rendering** from `loadDetail` JS (delete `<img>` blocks / screenshot fetch).

- [ ] **Step 5: Manual verify** all three tabs.

- [ ] **Step 6: Commit only if asked**

---

### Task 7: Upload simplification

**Files:**
- Modify: `src/main/resources/templates/upload.html`

**Interfaces:** Consumes Task 1–4 APIs.

- [ ] **Step 1: Remove** base URL, username, password inputs, saved-targets `<select>`, inline create-project block.

- [ ] **Step 2: Add** credential profile dropdown; populate on project change via `GET /api/projects/{id}/credentials`.

- [ ] **Step 3: Show read-only base URL** from `GET /api/projects/{id}` with link to Settings when missing.

- [ ] **Step 4: Submit** multipart with `credentialProfile` only (no baseUrl/username/password).

- [ ] **Step 5: Manual E2E** — Settings (URL + cred) → Upload → job queued.

- [ ] **Step 6: Commit only if asked**

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| baseUrl on project | 1 |
| Archive + hard delete | 1, 5 |
| Named credentials write-only | 2, 6 |
| Artifacts tree + delete | 3, 6 |
| Upload project + profile only | 4, 7 |
| Hide evidence/IR | 3, 6 |
| TC no screenshots | 4, 6 |
| Legacy baseUrl backfill | 1, 4 |
| Deprecate `/api/account/targets` | 4 |
| Running job guard | 3, 6 |
| Auth ownership | 4 |

**Placeholder scan:** none.  
**Type consistency:** `credentialProfile` param name used in JobController, Upload JS, and tests.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-23-projects-hub.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — implement in this session with checkpoints  

Which approach?
