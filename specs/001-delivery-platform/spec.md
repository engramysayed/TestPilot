# Feature Specification: Manual TC to Automation Delivery Platform

**Feature Branch**: `001-delivery-platform`

**Created**: 2026-08-04

**Status**: Draft

**Input**: User description: "TestPilot Delivery Platform MVP — companies without SDETs upload manual test cases (fixed Excel template), provide application URL and login, choose New or Update framework, and download a ready automation project package with reporting and CI starter files. Live authoring validates steps in a real browser; passed cases become durable automated tests; failed cases appear as explicit TODOs. Authoring uses on-premises AI only (no cloud AI). Frameworks are stored on our server for Update. Derived from approved design `docs/superpowers/specs/2026-08-04-testpilot-delivery-platform-design.md` and constitution v1.0.0 (empty `/speckit-specify` used conversation-approved scope)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First-time package delivery (New) (Priority: P1)

A company without SDETs creates a project, enters their application URL and login, uploads manual test cases using our Excel template, chooses **New**, waits for conversion to finish, and downloads a ready automation project package they can run and hand to engineering/CI.

**Why this priority**: This is the core saleable promise—first value without an in-house automation team.

**Independent Test**: Using a sample Excel file and a publicly reachable demo application, complete New end-to-end and confirm the download contains runnable passed tests and/or explicit TODOs plus score summary.

**Acceptance Scenarios**:

1. **Given** a signed-in user with a new project, **When** they upload a valid Excel file, provide URL/login, and start New, **Then** the system queues a job and shows progress until complete or failed with a clear message.
2. **Given** a completed New job with at least one passed case, **When** the user downloads the package, **Then** they receive a project they can configure locally and run to execute the passed automated tests.
3. **Given** a completed job where some cases failed validation, **When** the user opens the package and score summary, **Then** every uploaded case appears—passed as automated tests, failed as TODO items with reasons—and none are silently missing.

---

### User Story 2 - Add more cases to an existing package (Update) (Priority: P1)

A returning customer opens the same project (framework already stored by us), uploads new or changed Excel rows, chooses **Update**, and downloads a new versioned package that merges new automation without starting from zero.

**Why this priority**: Repeat business and ongoing suite growth without re-buying a full rebuild.

**Independent Test**: After a successful New, run Update with one new `TC_ID` and confirm the new download includes prior passed tests plus the new case (passed or TODO).

**Acceptance Scenarios**:

1. **Given** a project with a stored framework from a prior New, **When** the user uploads Excel containing a new `TC_ID` and selects Update, **Then** only new/changed cases are authored again and unchanged cases are reused from storage.
2. **Given** a completed Update job, **When** the user downloads the package, **Then** they receive a new version that includes previous and new cases, with an updated score summary.

---

### User Story 3 - Reject bad uploads early (Priority: P2)

A user uploads a file that does not match our Excel template (missing required columns or blank case ids). The system refuses the job before long-running conversion and explains what to fix.

**Why this priority**: Protects conversion capacity and guides non-SDET users to the only supported format.

**Independent Test**: Upload a deliberately invalid spreadsheet and confirm immediate rejection with a human-readable error; no conversion job starts.

**Acceptance Scenarios**:

1. **Given** an Excel file missing required columns, **When** the user submits a job, **Then** the system rejects the upload and lists the template requirements.
2. **Given** a row with blank `TC_ID`, **When** the file is validated, **Then** that file is rejected before authoring begins.

---

### User Story 4 - Track job status and automation score (Priority: P2)

While conversion runs, the user sees status (queued, running, done, failed) and, when done, a clear pass vs TODO count before downloading.

**Why this priority**: Long jobs need transparency; score is the “time saved / quality” proof for buyers.

**Independent Test**: Start a job and poll/view status until completion; verify score counts match cases in the package.

**Acceptance Scenarios**:

1. **Given** a running job, **When** the user views the project/job page, **Then** they see current status and progress at case granularity when available.
2. **Given** a finished job, **When** the user views results, **Then** they see counts of passed vs TODO cases before download.

---

### Edge Cases

- Application URL unreachable or login fails → job fails with a clear message; no incomplete “success” package presented as done.
- Empty Excel (headers only, zero cases) → reject or complete with zero tests and explicit empty-score messaging (MUST NOT claim success with hidden work).
- Duplicate `TC_ID` rows in one file → reject or fail validation with a clear duplicate-id error before authoring.
- User loses connection during a long job → job continues on the server; user can return and download when done.
- Update when project has no prior framework → system tells user to run New first.
- Customer app credentials must not appear inside the downloaded package; user fills secrets locally after download.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a customer user to create and open a project that represents one application under test.
- **FR-002**: System MUST accept manual test case uploads only via the company-owned Excel template (required: case id, title, steps, expected result; optional: preconditions, priority, tags).
- **FR-003**: System MUST reject non-conforming uploads before starting conversion and explain the template rules.
- **FR-004**: Users MUST be able to supply application base URL and login credentials for live authoring for the duration of a job.
- **FR-005**: Users MUST be able to choose **New** (first package) or **Update** (merge into the server-stored package for that project).
- **FR-006**: System MUST convert each case by authoring steps against the live application, executing them, and validating outcomes before treating a case as passed.
- **FR-007**: System MUST produce durable automated tests only for cases that pass live validation.
- **FR-008**: System MUST include every uploaded case in the delivered package; failed cases MUST appear as explicit TODO items with reason and evidence references.
- **FR-009**: System MUST package results as a downloadable automation project that includes reporting readiness and continuous-integration starter files.
- **FR-010**: System MUST keep the delivered framework structure domain-agnostic: shared core for all customers; customer-specific content limited to page definitions, test cases, and configuration placeholders.
- **FR-011**: System MUST store each project’s latest framework and locator/step knowledge on our servers to support Update without requiring the customer to re-upload a prior package.
- **FR-012**: On Update, system MUST reuse unchanged cases from storage and only re-author new or changed cases (detected by case id and content change).
- **FR-013**: System MUST show job status (at least queued, running, completed, failed) and an automation score (passed vs TODO counts) when complete.
- **FR-014**: System MUST NOT embed customer application passwords in the downloadable package; configuration examples MUST be provided for local secret entry.
- **FR-015**: Authoring assistance for delivery jobs MUST run on our infrastructure only (no cloud AI vendor calls on the delivery conversion path).
- **FR-016**: System MUST enforce locator quality rules during authoring (prefer stable identifiers, reject known-bad patterns, require uniqueness) and fail a case to TODO when rules or execution cannot be satisfied.
- **FR-017**: Portal experience MUST remain limited to project setup, upload, mode selection, status, score, and download—users MUST NOT operate browser automation through the website UI.
- **FR-018**: Users MUST be able to download versioned packages after New and Update jobs complete successfully (including partial success with TODOs).

### Key Entities

- **Customer Account**: Who can sign in and own projects.
- **Project**: One customer application engagement; holds URL metadata, stored framework, locator/step map, and job history.
- **Manual Test Case**: One Excel row (`TC_ID`, title, steps, expected result, optional fields).
- **Conversion Job**: A New or Update run with status, progress, score, and artifact links.
- **Automation Package**: Downloadable project version containing core framework, generated pages/tests, TODO stubs, score summary, and CI starter files.
- **Case Outcome**: Passed (durable automated test) or TODO (stub + reason + evidence).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new customer can go from valid Excel upload + URL/login to a downloadable package in one session without writing automation code themselves.
- **SC-002**: 100% of uploaded cases in a completed job appear in the package either as automated tests or as explicit TODO items (0% silent drop).
- **SC-003**: After filling local configuration, the customer can run the package’s automated tests for all passed cases without contacting us for a “missing framework.”
- **SC-004**: An Update that adds one new case preserves previously passed cases in the new download (no full wipe of prior suite content).
- **SC-005**: Invalid Excel uploads are rejected with an actionable message before conversion starts in under 30 seconds for typical file sizes (≤5 MB).
- **SC-006**: Job status is visible to the user within 10 seconds of submission and remains accurate through completion.
- **SC-007**: Downloaded packages never contain the customer’s application password as a filled secret file.
- **SC-008**: Pilot customers report they can demonstrate automation of at least one happy-path case from their Excel without hiring an SDET for framework setup.

## Assumptions

- Feature description was empty on the command line; scope is taken from the approved Delivery Platform design (2026-08-04) and constitution v1.0.0.
- Target buyers are organizations with manual tests and little or no SDET capacity.
- Customers can provide a reachable test environment URL and credentials for the conversion window.
- Authentication for the portal is simple account/session-based for MVP (SSO/SAML later).
- Billing, Git repository sync, and arbitrary file formats are out of scope for this feature.
- One supported customer automation stack is defined by the constitution/product owner for MVP packages; this spec describes outcomes, not vendor brand names.
- On-premises authoring AI capacity is available on our servers for conversion jobs.
- English Excel template and UI copy are sufficient for MVP.
