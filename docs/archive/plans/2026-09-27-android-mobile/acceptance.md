# Acceptance and operator validation guide

This is a future execution guide, not a record of tests already run. Proposed class/profile/script names below must be created by the tasks before commands become runnable. No Android tools or APKs were installed as part of planning.

## Preconditions and bounded setup

Operator supplies an authorized synthetic Flutter APK (or installed package), emulator and physical device, test accounts and declared OTP/PIN values. Prefer a dedicated test device rather than a personal device with unrelated sensitive data. Record exact toolchain lock, code/template revisions, OS/API/ABI, locale/orientation and app identity.

Read-only prerequisite commands, once the tools are intentionally installed:

```text
java -version
mvn -version
node --version
npm --version
adb version
adb devices -l
appium --version
appium driver list --installed
appium driver doctor uiautomator2
apkanalyzer manifest application-id <authorized.apk>
apkanalyzer manifest version-code <authorized.apk>
```

ADB reports exactly the selected serial as authorized/online. No tests choose another device after disconnect. Appium binds to loopback for managed local jobs. Operator provides missing dependencies explicitly; do not put automatic installers into these checks. Check APK hash and package/activity selection before allowing install. Installed-app mode must not install/reset the app as a side effect of preflight.

Future bounded commands:

```text
mvn -B -f mobile-contracts/pom.xml clean verify
mvn -B -f mobile-runner/pom.xml -Pdeterministic clean verify
mvn -B -Pdeterministic test
mvn -B -f mobile-runner/pom.xml -Pmobile-live -Dtest=MobileNativeAcceptanceTest test
mvn -B -f mobile-runner/pom.xml -Pmobile-live -Dtest=MobileAuthAcceptanceTest test
mvn -B -f mobile-runner/pom.xml -Pmobile-live -Dtest=MobileHybridProviderAcceptanceTest test
mvn -B -f mobile-runner/pom.xml -Pmobile-live -Dtest=MobileDownloadedReplayTest test
```

Live profiles require explicit enablement, local non-secret configuration and protected secret inputs; no secrets in command-line arguments. Per-command/job deadlines and output draining use existing supervisor semantics. A timeout is a failed/uncertain gate, not an approved miss. Do not launch the full acceptance pack while debugging one small failed scenario.

## Controlled fixture application requirements

Build or obtain an authorized Flutter test APK with stable semantic IDs and independently verifiable state. Include login with password and phone→supplied OTP→PIN alternatives, two accounts, visible session identity, expiry/logout, cart/order reference, nested semantics, merged keypad, repeated labels, Arabic text, permissions, long list, keyboard, and an embedded test WebView. Provide a distinct vision-only target plus post-action state verification. Record fixture version/source/checksum; production app modifications are not part of fixture setup.

Negative controls are intentional: wrong expected order ID, duplicate visible label, absent/hidden controls, stale screen, bad OTP, wrong account, dropped connection and non-debuggable WebView. A suite with only successful taps is insufficient.

## Acceptance cases and required outcomes

### A. Device and app gate

- A01 Uploaded valid APK: manifest identity detected, activity confirmed, install/session on selected device only.
- A02 Installed app: observed package/version used, no claim of exact APK checksum, no upload/install required.
- A03 Missing/unauthorized/offline serial: fail preflight; zero app commands on another connected device.
- A04 Two jobs/tenants contend for one physical device: exactly one admission/lease; loser receives DEVICE_BUSY or tenant denial, no interleaved actions.
- A05 Corrupt/oversized/mismatched APK, incompatible ABI/API or signature/downgrade issue: precise rejection, previous app not silently removed.
- A06 Activity ambiguity: manual override required; launch activity and wait activity tested separately.

### B. Binding and native execution gate

- B01 User selector vs stronger automatic candidate: valid user selector wins and origin is USER.
- B02 Invalid user selector: rejection reason and chosen fallback shown; fallback action still verified.
- B03 Accessibility/resource ID with underscore, mixed case and Arabic label: selector unchanged through import → IR → Java → replay.
- B04 Duplicate labels: distinct controls reject ambiguity; selector twins of one control do not create false ambiguity.
- B05 Merged Flutter semantics: unavailable child is not fabricated; use verified alternative or explicit blocked outcome.
- B06 Tap/type/clear/long press/swipe/scroll/back/keyboard/restart: intended state changes, finite bounds and no implicit clear-data.
- B07 Intentional false exact assertion, all-match absence and dead session: case fails, errors do not become successful absence.
- B08 Capture/compare uses fresh runtime order ID across setup/body, rejects prefix collisions and missing capture, clears between cases.

### C. Auth and prerequisite gate

- C01 Single authenticated TC with valid selected-account session: check passes, unnecessary login omitted.
- C02 Expired session: user-defined login steps run, account verified before test body.
- C03 Wrong account/profile switch: logout and new login, no execution under old identity.
- C04 Fresh login vs logged-out vs login-under-test: exact requested setup; login-under-test has no hidden successful prelude.
- C05 Phone/static supplied OTP/PIN: declared values used; absent/incorrect/expired values fail honestly. No default retained OTP or background retrieval.
- C06 One selected leaf with transitive/shared Call-before: prerequisites included once per leaf, stable order; cycle/missing/auth-conflict rejected.
- C07 Failed login/prerequisite: body unexecuted; dependent BLOCKED, unrelated case may proceed only after its baseline restoration.
- C08 Permission-test override: denial actually exercised; ordinary grant policy cannot pre-grant it. App-data clearing occurs only when explicit.

### D. Hybrid and provider gate

- D01 Native → intended WebView → native: exact context identity, no first-WebView assumption, downloaded replay equivalent.
- D02 Multiple/missing/non-debuggable WebViews or mismatched Chromedriver: blocked with diagnostic, no silent native substitute/download.
- D03 Keel hierarchy path with providers off: passes stable accessible cases without hidden model dependency.
- D04 Real Cursor Precision: actual provider call + valid candidate + action + business assertion; record calls/latency/model, not just invocation.
- D05 Cursor unavailable/disallowed/cap exhausted: Keel fallback reported with reason and actual used provider. Counts do not label fallback as Cursor proof.
- D06 Vision-only target: fresh screenshot resolved on live proof and downloaded replay; exact intended postcondition verified. Static coordinates fail this gate.
- D07 Low-confidence/stale/rotated/blank screenshot or unavailable vision: no guessed tap and no PASS. Required vision target remains failed/blocked.

### E. Framework and UPDATE gate

- E01 Actual portal/runner NEW path → download ZIP → fresh directory `mvn -B clean test`: all expected positives pass; case IDs exactly match package manifest.
- E02 Real UPDATE path after TC edit: changed and retained selected cases re-proven; full generated layer contains retained helpers, no obsolete classes/data.
- E03 New APK: selected cases re-prove; unselected old-build cases are stale/TODO, original package/result preserved with original hash.
- E04 False action/assertion ZIP: Maven nonzero; setup failure skips body, cleanup succeeds. Separate body failures from configuration failures.
- E05 Stable naming: reorder cases/steps where identity persists; unchanged screen/locator symbols remain stable, collision names remain deterministic.
- E06 Missing local secret/config/provider: precise failure, never silently substitute sample credentials/device/coordinates.
- E07 Compile/publication failure: prior package bytes and job-specific downloads unchanged; late fenced worker cannot publish.

### F. Fault, privacy and web-regression gate

- F01 Cancel before claim, during login, during wait and during mutation: no later commands, bounded cleanup, uncertain mutation not retried.
- F02 Kill worker/Appium or unplug device mid-action: INTERRUPTED_UNCERTAIN; device quarantined until old session cannot act. Late heartbeat/upload rejected.
- F03 Second agent process cannot steal device after portal lease expiry while old process still holds physical session/lock.
- F04 Secret canary: absent from provider request except permitted protected input handling, IR, logs, Allure parameters, Surefire XML, Java, locator map, package and sanitized screenshot pixels. Inspect expected-string/failure paths too.
- F05 Foreign tenant cannot list/claim/configure/download device/app/evidence; MEMBER cannot operate; OWNER/ADMIN and deletion restrictions match existing product roles.
- F06 Existing web deterministic suite and web generated NEW/UPDATE replay remain green; no Appium tooling needed to run web-only projects.

Run A–F across emulator and physical device for supported capabilities. At least one API-26 baseline device/emulator and one recent supported API should be qualified before advertising an API range; record exactly which combinations ran. Do not expand support to every OEM/Android release from those samples.

## Evidence and scoring

Every run creates an immutable UTC+UUID evidence directory, with prewritten expected outcomes and a final record only after all checks finish. Record command/exit/deadline, source/template/toolchain revisions, clean/dirty state, APK hash or observed identity, device type/API, provider/model, library/config/profile hashes, per-case proof/setup/body outcome, screenshots/IR manifests, teardown acknowledgment and ZIP hashes.

Proven rate numerator is live assertion-backed PASSED cases; denominator includes blocked/TODO/unproven requested cases. SIMULATED is separately labeled, not a positive proof sample. Do not mark required vision/WebView/OTP cases approved misses to make a suite pass. Contract/mock tests, controlled live APK tests, actual model tests and representative customer acceptance are separate evidence categories.

The completion report must say whether downloaded replay was actual NEW/UPDATE execution or merely re-emitting fixture IR. No all-green claim when required environment tests were not run. Public rollout remains governed by its own named release candidate and approvals.
