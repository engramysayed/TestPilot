# Feature Specification: Portal Product (Invite Auth, Dashboard, TAF Core)

**Feature Branch**: `002-portal-product`

**Created**: 2026-08-04

**Status**: Draft

**Input**: Portal v2 — TAF-Template-CI-CD as customer core; invite-only auth; dashboard with charts; project pick/create upload; Windows VPS hosting (no Docker).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Invite login and owned workspace (Priority: P1)

An invited user accepts an invite (or is created by admin), logs in, and only sees their own projects and jobs.

**Why this priority**: Security and commercial gate before any conversion UI.

**Independent Test**: Create invite → set password → login → create project; unauthenticated API calls return 401/redirect.

**Acceptance Scenarios**:

1. **Given** a valid invite token, **When** the user sets a password, **Then** they can log in and reach the dashboard.
2. **Given** no session, **When** they open `/dashboard` or `/api/projects`, **Then** access is denied.
3. **Given** user A owns a project, **When** user B requests that project, **Then** access is denied.

---

### User Story 2 - Dashboard stats and charts (Priority: P1)

A logged-in user sees project count, job counts, pass vs TODO totals, and simple charts of recent activity.

**Why this priority**: Shows product value and work done for buyers.

**Independent Test**: After one completed dry-run job, dashboard numbers match that job’s passed/todo counts.

**Acceptance Scenarios**:

1. **Given** completed jobs for the user, **When** they open the dashboard, **Then** aggregate stats and at least one chart render.
2. **Given** a new user with no jobs, **When** they open the dashboard, **Then** zeros are shown without errors.

---

### User Story 3 - Project pick/create then upload (Priority: P1)

From the portal, the user selects an existing project or creates a new one, then uploads Excel and starts NEW/UPDATE.

**Why this priority**: Core conversion journey after auth.

**Independent Test**: Create project from upload page → upload sample Excel → job completes → download ZIP.

**Acceptance Scenarios**:

1. **Given** an existing project, **When** the user selects it and uploads valid Excel, **Then** a job is queued for that project.
2. **Given** no project yet, **When** the user creates one inline and uploads, **Then** the job is tied to the new project.

---

### User Story 4 - TAF-based customer ZIP (Priority: P1)

Downloaded packages are based on the TAF-Template-CI-CD core (drivers, utilities, CI, reporting), with generated/TODO tests written into that layout and no filled secrets.

**Why this priority**: Deliverable quality matches the commercial framework promise.

**Independent Test**: Dry-run package contains TAF packages (`project.*`) and CI workflow; zip has no password-filled properties.

**Acceptance Scenarios**:

1. **Given** a completed New job, **When** the ZIP is inspected, **Then** TAF core sources and `.github` workflows are present.
2. **Given** any job password, **When** the ZIP is scanned, **Then** that password and filled `webapp.properties` are absent.

---

### User Story 5 - Windows VPS ops runbook (Priority: P2)

Operator can host on Windows Server via RDP using documented native installs and start scripts (no Docker).

**Why this priority**: Matches owner skillset for first production.

**Independent Test**: Follow `docs/ops/windows-vps-setup.md` checklist on a clean mental walkthrough; scripts exist and point at Spring Boot main.

**Acceptance Scenarios**:

1. **Given** the repo, **When** ops docs are opened, **Then** install order (JDK, Chrome, Ollama, Git) and start/backup steps are listed.
2. **Given** `start-portal.bat`, **When** run from project root with JDK/Maven available, **Then** it launches the portal main class.

## Edge Cases

- Expired or reused invite token → clear error; no account created.
- UPDATE without stored framework → 400 as today.
- Invalid Excel → 400 INVALID_EXCEL before job queue.

## Requirements *(mandatory)*

- Invite-only auth (no open self-registration).
- Session-based login; owner-scoped projects/jobs.
- File DB (H2) for MVP users/invites/projects/job history.
- Dashboard aggregates + charts.
- Customer core = TAF-Template-CI-CD lineage under `customer-framework-template/`.
- Windows ops documentation + start script; no Docker requirement.
- Local AI only on conversion path; dry-run remains default until Ollama configured.

## Success Criteria

- SC-001: Invited user completes login → dashboard → project → upload → download.
- SC-002: Cross-user project access blocked.
- SC-003: ZIP rooted on TAF layout without secrets.
- SC-004: Windows runbook + start script present.
