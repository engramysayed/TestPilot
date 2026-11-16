# Data model and interface contracts

All additions below are proposed implementation contracts. Endpoint names and schemas are not present APIs until their tasks are complete. Version mobile contracts independently; never reinterpret old web fields as Android capabilities.

## Persistence and migration

Use opaque IDs and tenant-scoped foreign-key validation. New revision rows are immutable; updating a head requires `baseRevision` and returns 409 on conflict. Every job stores references to revisions, not pointers to mutable current configuration.

- `projects.platform`: WEB or ANDROID; non-null backfill WEB. Platform immutable after creation; conversion requires a new project. Existing routes continue to load WEB when reading legacy rows.
- `mobile_apps`: app ID, tenant/project, source APK or INSTALLED, active revision, lifecycle ACTIVE/TOMBSTONED. `mobile_app_revisions`: revision ID, source, package, version code/name, min SDK, ABI list, launch/wait activity, APK blob reference and SHA-256 if uploaded, optional certificate digest, observed metadata, creator/time. Server-computed identity; user overrides do not overwrite detected evidence.
- `mobile_device_profiles`: revision, tenant, runner/device binding, display name, package/app revision, capabilities allowlist, reset/permission policy, locale/orientation/timeouts. Machine paths belong to runner-local configuration; portal sees aliases and safe metadata.
- `mobile_devices`: opaque device ID, tenant, host/runner ID, encrypted or locally resolved serial, model/API/ABI, authorized state, last seen, reservation state. Unique physical binding `(hostId, serialFingerprint)`; reject cross-tenant registration of the same binding.
- `mobile_device_leases`: device ID unique active lease, job/attempt ID, generation/fence, expiry, heartbeat, session/owned-process references, cleanup status. Compare-and-set transitions; no release on TTL alone after activation.
- `mobile_auth_profiles`: profile/revision IDs, tenant/project, login TC + library revision, session/signed-out predicates, logout/setup refs, logical account ref, secret-input declarations. Optional protected saved credential refs, no embedded password/OTP/PIN.
- `mobile_case_settings`: library revision + TC ID, auth mode/profile revision, starting-state TC IDs, permission override, context default, timeout/fail-fast override. Validate closure with the pinned library. Preserve stable case identity using `TcIdentity`.
- Job additions: platform, app/device/auth/case-settings revisions, required runner capabilities, selected TC IDs, toolchain requirement, credential revision, mobile schema version and effective configuration hash. Runtime evidence adds observed device/build/toolchain identity and lease fence; it must not mutate frozen admission inputs.
- Mobile results: case/attempt/step IDs, outcome, failure code, expected/observed references, setup outcome, actual provider/fallback, timing, sanitized evidence manifest, proof kind. Distinguish FAILED, BLOCKED, TODO, PASSED and INTERRUPTED_UNCERTAIN at the result layer; map into existing portal aggregates explicitly.

Additive migration first, then deploy code that understands it. No destructive rewrite of web IR. Test old DB load, interrupted migration and retry. Rollback disables mobile admission/UI but preserves mobile tables/artifacts; an older binary must reject mobile jobs rather than consume them. Deletion tombstones the project, cancels/fences mobile jobs, waits for teardown, deletes protected inputs and tenant artifacts, then unassigns devices. Never factory-reset hardware as project deletion.

Storage proposal under the existing tenant root:

```text
projects/{projectId}/mobile/apps/{appRevisionId}/manifest.json
projects/{projectId}/mobile/apps/{appRevisionId}/app.apk
projects/{projectId}/mobile/profiles/{profileRevisionId}.json
projects/{projectId}/mobile/ir/{attemptId}/{tcStorageKey}.json
projects/{projectId}/mobile/names/{namingRevisionId}.json
projects/{projectId}/evidence/{jobId}/{attemptId}/{tcStorageKey}/{occurrence}/...
projects/{projectId}/versions/{versionId}/mobile-package.zip
```

Use existing authorized artifact resolvers and retention/publication locks; these are logical paths, never URLs accepting arbitrary filesystem paths. Tenant-scoped blob deduplication only; a hash is not an authorization credential.

## Mobile IR v1

New `MobileCaseIr`/`MobileStep` records and strict `MobileIrCodec`. Required envelope fields: `schemaVersion`, `platform`, `tcId`, `libraryRevisionId`, `appRevisionId`, `caseSettingsRevisionId`, `proof`, `setupRefs`, `steps`, `namingRevisionId`. Unsupported versions/action types fail before execution. Unknown metadata may be retained; unknown behavioral fields must not be ignored.

```json
{
  "schemaVersion": "mobile-ir/1",
  "platform": "ANDROID",
  "tcId": "TC_LOGIN",
  "libraryRevisionId": "rev_example",
  "appRevisionId": "apprev_example",
  "caseSettingsRevisionId": "mcase_example",
  "namingRevisionId": "names_example",
  "proof": {"status": "TODO", "source": "NONE", "attemptId": null},
  "setupRefs": [],
  "steps": [{
    "stepId": "step_example",
    "screenKey": "login",
    "operation": "TYPE",
    "context": {"kind": "NATIVE", "package": "com.example.test"},
    "target": {
      "strategy": "accessibilityId",
      "value": "phone_Number_Input",
      "origin": "USER",
      "expectedMatches": 1
    },
    "input": {"kind": "SECRET_REF", "key": "auth.phone"},
    "timeoutMs": 15000,
    "retryPolicy": "LOOKUP_ONLY",
    "evidence": {"snapshotId": null, "artifactRefs": []}
  }]
}
```

Operation-specific validation:

- TYPE requires input of LITERAL, DATA_REF or SECRET_REF. LITERAL forbidden for a field marked sensitive; reject instead of hoping the sanitizer catches it.
- Locator target requires strategy/value/context, uniqueness policy and USER/HIERARCHY/PRECISION/MEMORY origin. Vision target instead requires semantic target, provider ref, minimum confidence and fresh-image requirement; saved coordinates cannot fulfill the locator schema.
- ASSERT requires type, target/collection scope, expected literal/ref, comparison policy and timeout. `CAPTURE_TEXT` includes a variable name and extraction rule; `COMPARE_CAPTURED` includes variable + destination target + EXACT/TOKEN_BOUNDARY mode. Regex allowed only as bounded capture extraction, not arbitrary executable code.
- Signed-out is an explicit predicate list with all/any semantics selected by the author. Missing identity node is distinct from an element with empty text; specify EXISTS_EMPTY, ABSENT, EXACT_TEXT or NOT_VISIBLE intentionally. Require at least one observable sign-out predicate; do not infer it from login overlay wording.
- SCROLL_UNTIL requires container, direction, target, max swipes/time; SWIPE requires bounded normalized region/direction/duration. SWITCH_CONTEXT requires native or package + WebView URL/title matcher. No volatile context handles serialized as durable selectors.
- Proven step evidence records actual match count/context, pre/post snapshot IDs, elapsed time, observed assertion value (redacted/ref when sensitive), provider used and fallback reason. `validated=true` alone is insufficient proof metadata.

Legacy compatibility: missing platform on legacy IR means WEB and uses `ProvenStepCodec`; mobile payload never falls through to that permissive parser. Mobile codegen accepts only recognized mobile IR. Web codegen refuses ANDROID. A mobile v1 reader rejects v2, rather than dropping capabilities. Codec round-trip tests cover Unicode, optional fields, secret refs and unknown operations.

## Authoring input

Keep the existing TC columns and import formats. Store mobile case settings in a companion revision; optional columns map into it: `AuthProfile`, `AuthMode`, `StartState`, `PermissionPolicy`. Existing WEB imports ignore no new semantics silently: incompatible mobile-only columns produce a platform diagnostic.

Introduce an explicit locator override editor keyed by TC ID + stable step ID. For workbook users, accept an optional `LocatorOverrides` sheet with columns `TC_ID, Step, Context, Strategy, Value`; Step resolves to the imported source-step identity and is frozen with that revision. Duplicate overrides conflict. Reordering text must not attach an override to a different step without review.

Grammar examples, to be documented and parser-tested before UI exposure:

```text
Tap "Sign in"
Type ${secret:auth.phone} into "Phone number"
Type ${secret:auth.otp} into "OTP"
Clear "Search"
Long press "Transaction"
Scroll down in "Transactions" until "Older payment" is visible
Press Android Back
Hide keyboard
Switch to WebView for package "com.example.test" URL "https://test.example/checkout"
Capture text from resourceId="com.example.test:id/order_id" as orderId
Assert text at accessibilityId="order_Reference_Text" equals captured orderId
Assert accessibilityId="session_Email_Text" exists with empty text
```

Quoted strings preserve punctuation/Arabic; `${secret:...}`, `${data:...}` and captured variables are separate namespaces. Escaping is specified once and shared by import/worker/codegen. Ordinary textContains never becomes capture/compare merely because text repeats. Structured editor is authoritative for advanced extraction and compound predicates; natural-language ambiguity returns an authoring question/TODO, not guessed semantics.

## Portal API additions

Use existing session auth, request-header/CSRF protections and project membership helpers. IDs from another tenant return 404. Authenticated insufficient roles return 403. MEMBER can read safe metadata/history; OWNER/ADMIN can upload/configure/run/export; OWNER-only device assignment and project deletion. All paginated lists default 25/max100; uploads and runs have rate/size quotas.

- `POST /api/projects`: add `platform=ANDROID|WEB`, default WEB. Reject mutable platform changes.
- `POST /api/projects/{p}/mobile/apps`: multipart APK upload or JSON installed-app registration `{source, deviceId, package, activityOverride}`; 201 app revision or 422 inspection/selection-required detail. Upload remains quarantined until validation completes; optional asynchronous inspection returns 202 with status URL.
- `GET /api/projects/{p}/mobile/apps` and `GET .../apps/{revision}`: safe app identity/history; no raw upload path. Binary download uses existing operate-role artifact authorization.
- `GET /api/projects/{p}/mobile/devices`: authorized inventory/readiness only. `POST .../preflight` accepts pinned device/app/profile refs, returns diagnostics; no install/reset/session creation side effects.
- `GET/POST /api/projects/{p}/mobile/device-profiles` and `.../auth-profiles`: read/create revisions; writes carry `baseRevision`. Secret fields accept references/input declarations, never echo plaintext.
- `POST /api/projects/{p}/mobile/jobs`: Execute or Automate NEW/UPDATE with frozen revision refs, selected TC IDs, runner/device ID, authoring engine, provider/budget settings, secret envelope reference and fail-fast flag. Idempotency-Key + tenant + request hash; same key/different request is 409.
- Existing status/cancel/evidence/download APIs gain platform-specific result sections; authorization and job-bound artifacts remain unchanged. No new unauthenticated device command endpoint.

Job request example:

```json
{
  "operation": "AUTOMATE",
  "mode": "UPDATE",
  "libraryRevisionId": "rev_example",
  "appRevisionId": "apprev_example",
  "deviceProfileRevisionId": "devrev_example",
  "caseSettingsRevisionId": "mcase_example",
  "selectedTcIds": ["TC_CHECKOUT"],
  "deviceId": "dev_example",
  "runnerId": "runner_example",
  "authoringEngine": "precision",
  "precision": {"enabled": true, "maxCalls": 50, "allowKeelFallback": true},
  "secretEnvelopeRef": "input_example",
  "stopOnFirstFailure": false
}
```

## Runner protocol extension

Extend existing `/api/v1/runners` routes compatibly. Registration/heartbeat advertises protocol version, platform capabilities, worker/toolchain version and authorized device inventory. Claim filters jobs server-side by tenant, platform, device ownership and supported schema; a web-only runner cannot claim mobile.

Claim reply adds `platform`, mobile input manifest hash, required capabilities, device lease fence and pinned app/profile refs. Frozen input ZIP includes non-secret manifest, library and referenced APK only if source=APK; validate archive limits and checksum before install. Secrets arrive through an authenticated attempt-bound short-lived envelope, not the ZIP. Stage vocabulary adds DEVICE_ACTIVE/MOBILE_EXECUTING with the same uncertain-outcome semantics as browser-stage work; old clients must not map unknown stages to safe-to-retry.

Heartbeat, stage update, artifact upload, completion and teardown acknowledgment carry `(jobId, attemptId, deviceId, leaseFence, inputHash)`. Reject stale/mismatched values even with a valid runner token. Authenticate every upload, verify HMAC/checksum/size/path rules, validate result schema and counts from case results before publication. Duplicate completion is idempotent. A completed job cannot be changed by a later worker. Redact tokens and secret envelopes from all request logs.

## Failure codes and status mapping

- Preflight/admission: DEVICE_BUSY, DEVICE_OFFLINE, DEVICE_UNAUTHORIZED, RUNNER_CAPABILITY_MISSING, APPIUM_UNAVAILABLE, TOOLCHAIN_UNQUALIFIED, APK_INVALID, APP_ID_MISMATCH, ACTIVITY_REQUIRED, SECRET_REQUIRED.
- Setup/binding: AUTH_FAILED, AUTH_RESET_UNCONFIGURED, SESSION_CHECK_UNCERTAIN, CALL_BEFORE_CYCLE, CALL_BEFORE_BLOCKED, LOCATOR_AMBIGUOUS, LOCATOR_NOT_FOUND, USER_LOCATOR_REJECTED, WEBVIEW_UNAVAILABLE, CONTEXT_AMBIGUOUS, PERMISSION_UNSUPPORTED.
- Execution: ASSERTION_FAILED, ACTION_FAILED, SCROLL_LIMIT, VISION_UNAVAILABLE, VISION_UNCERTAIN, DEVICE_LOST, INTERRUPTED_UNCERTAIN, CANCELLED, CLEANUP_UNCERTAIN.
- Infrastructure/publication: STALE_ATTEMPT, STALE_DEVICE_LEASE, INPUT_HASH_MISMATCH, ARTIFACT_INVALID, COMPILE_FAILED, LIBRARY_CONFLICT.

Fallback event is not itself a test failure: record provider reason and actual result. Admission rejection creates no runnable job or charged reservation. Setup failure means dependent test body BLOCKED/SKIP with failed setup, not PASS. Interrupted device operation stops the job; failed compile retains the last good package. Persist code + stage + safe message + evidence references, not stack traces with secrets in user-facing messages.
