# Keel — host environment requirements

Single source of truth for everything the portal + runner expect to find on the host. Written 2026-11-23. If you add a new external tool to the codebase, update this doc in the same commit.

The portal is a Spring Boot app; the mobile runner is a separate JVM process. Both shell out to platform binaries for the work they don't own — adb for device discovery, Appium for driving the phone, apkanalyzer / aapt2 for APK inspection, Ollama / Cursor for model calls, and so on. A missing binary is not a Spring bean failure; it surfaces at the moment the feature is used (phone not listed, APK upload errors, Automate falls back to Keel).

---

## 1. Must-have on every host

| Tool | Minimum | Role | Check on PATH | Install (Windows / Scoop) |
|-|-|-|-|-|
| **JDK 21 LTS** | `21.0.x` | Portal + runner + customer framework | `java -version` | `scoop install temurin21-jdk` or JetBrains Runtime 21 |
| **Maven 3.9+** | `3.9.0` | Build the portal and the two customer templates | `mvn -v` | `scoop install maven` |
| **Git** | any | Checking out / updating the repo | `git --version` | `scoop install git` |
| **Node.js 18+** | `18.x` LTS | Required ONLY if `tools/cursor-heal/heal.mjs` is used (Precision picker, mobile Precision bind, Cursor Auto heal) | `node --version` | `scoop install nodejs-lts` |

That's the whole web-only install. Mobile features need the next section.

### 1.1 Required environment variables

| Variable | Set where | Role |
|-|-|-|
| `KEEL_RUNNER_TOKEN` | `api-keys.local.bat` (portal) + runner env | Bearer token for `/api/v1/runners/...`. Enroll a runner on `/projects/{id}/runners` to mint one. |
| `KEEL_PORTAL_URL` | runner env | URL the runner posts back to. Default `http://127.0.0.1:8080`. HTTPS required when off-loopback (runner refuses plain-http off-host). |
| `CURSOR_API_KEY` | `api-keys.local.bat` | Required ONLY if Precision is enabled for a project or the mobile Precision bind endpoint is in use. Portal falls back to Keel if missing. |

### 1.2 Optional but recommended

| Variable | Default | What it does |
|-|-|-|
| `KEEL_RUNNER_CAPTURE_STEP_PNG` | `true` | Each mobile step's PNG is uploaded to the portal. Set `false` to skip (saves bandwidth; disables Figma Validate per-step). |
| `KEEL_CURSOR_MODEL` | *(unset = Auto)* | Pin a specific Cursor model id across jobs. Per-project override lives under Project Settings → Cursor model. |
| `SPRING_PROFILES_ACTIVE` | *(unset)* | Load `application-product.properties` on top of base. Not needed as of 2026-11-23 — base already defaults mobile ON. |

---

## 2. Mobile (Android) extras

Everything in section 1 plus the four tools below. The portal warns at run time when any of these is missing — it does not fail boot.

### 2.1 ADB (Android Debug Bridge)

**Role**: `AdbDeviceDiscovery` runs `adb devices`, `adb -s <serial> pm path <pkg>` (Phase 9b preflight), and `adb -s <serial> shell dumpsys package …`. Without it, the Mobile Devices panel lists zero phones.

- **Install (Windows / Scoop)**: `scoop install android-clt` — bundles `adb`, `apkanalyzer`, `sdkmanager`.
- **Install (macOS)**: `brew install --cask android-platform-tools`
- **Install (Linux)**: your distro's `android-tools` / `adb` package, or unzip `platform-tools-latest-linux.zip` from the Android developer site.
- **Override path**: `delivery.mobile.adb-path` in `application.properties` if `adb` isn't on PATH.
- **Verify**: `adb --version` should print `Android Debug Bridge version 1.0.41` or newer.

### 2.2 apkanalyzer (primary APK inspector)

**Role**: `ProcessApkAnalysisBackend.analyze()` tries this first to read package name, version code, min SDK and launcher activity on every APK upload.

- **Install (Windows)**: bundled with `scoop install android-clt` (lives at `%USERPROFILE%\scoop\apps\android-clt\current\cmdline-tools\latest\bin\apkanalyzer.bat`).
- **Install (macOS / Linux)**: ships inside the Android `cmdline-tools` package — `brew install --cask android-commandlinetools` on macOS, then set `ANDROID_HOME` and add `$ANDROID_HOME/cmdline-tools/latest/bin` to PATH.
- **Override path**: `delivery.mobile.apkanalyzer-path`.
- **Verify**: `apkanalyzer --help` prints usage.

### 2.3 aapt2 (fallback APK inspector)

**Role**: Safety net when apkanalyzer fails on a quirky APK. Not strictly required if apkanalyzer works on your APKs.

- **Install (any OS)**: `sdkmanager "build-tools;34.0.0"` (or any 33+ version). The binary lands at `$ANDROID_HOME/build-tools/<ver>/aapt2[.exe]`. Add that directory to PATH, or point `delivery.mobile.aapt2-path` at it (see §3.1 below for the recommended per-host override).
- **Verify**: `aapt2 version` prints `Android Asset Packaging Tool (aapt) 2.19-…` or newer.
- **If missing**: APK uploads still work when `apkanalyzer` succeeds. If both are missing, every APK upload fails with `INSPECTION_FAILED`.

### 2.4 Appium server + uiautomator2 driver

**Role**: Portal's `SupervisedAppiumLifecycle` spawns a per-run Appium (one port lease per concurrent run). The runner's `W3cAppiumDriverPort` talks W3C to it.

- **Install the server**: `npm install -g appium` (Node 18+).
- **Install the driver**: `appium driver install uiautomator2`. Verify with `appium driver list --installed` — you should see `uiautomator2@8.x`.
- **Override path**: `delivery.mobile.appium-path` if `appium` isn't on PATH.
- **Port range**: `delivery.mobile.appium-port-range-start` / `-end` (defaults `4723` → `4923`). Portal reserves one port per active lease.
- **Verify**: `appium --version` prints `2.x` or newer.

### 2.5 Mobile runner process (the worker)

This is NOT a system binary — it's a JVM you build from this repo. One per attached device, started where the phone is plugged in. Env vars `KEEL_RUNNER_TOKEN` and `KEEL_PORTAL_URL` are required.

```bat
mvn -f mobile-runner/pom.xml -q package -DskipTests
java -cp mobile-runner\target\mobile-runner-1.0-SNAPSHOT.jar delivery.mobile.runner.agent.MobileRunnerAgent
```

Enroll a runner on `/projects/{id}/runners` to mint the token. The runner polls `/api/v1/runners/mobile/claim`, drives the session, and posts results back — no inbound ports.

### 2.6 Android device

- USB debugging on (`Developer options → USB debugging`).
- Host authorized the first time the cable is plugged in (confirm the on-phone dialog).
- `adb devices` should list it as `device` (not `unauthorized` or `offline`).
- At least one APK uploaded via Projects → **App packages** before you can run anything.

---

## 3. Status of THIS host (2026-11-23)

Captured from `Get-Command` on this machine; re-run the probe after any install change.

| Tool | Present | Where | Notes |
|-|-|-|-|
| `java` 21.0.13 LTS | ✅ | `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe` | Matches the `<release>21</release>` pin in every pom |
| `mvn` 3.10.0 | ✅ | `D:\progs\Maven\apache-maven-3.10.0-bin\…\mvn.cmd` | Above the 3.9+ floor |
| `git` | ✅ | `C:\Program Files\Git\cmd\git.exe` | |
| `node` + `npm` | ✅ | `C:\Program Files\nodejs\` | `tools/cursor-heal/node_modules/` already installed |
| `adb` | ✅ | `%USERPROFILE%\scoop\apps\android-clt\current\platform-tools\adb.exe` | |
| `apkanalyzer` | ✅ | `%USERPROFILE%\scoop\apps\android-clt\current\cmdline-tools\latest\bin\apkanalyzer.bat` | |
| `aapt2` | ✅ | `%USERPROFILE%\scoop\apps\android-clt\current\build-tools\35.0.0\aapt2.exe` | `keel-env.local.bat` adds that folder to PATH *and* passes `--delivery.mobile.aapt2-path=<absolute path>` via `KEEL_EXTRA_ARGS` as a belt-and-braces override. Version `2.19-11948202`. |
| `appium` | ✅ | `%APPDATA%\npm\appium.ps1` | |
| Appium `uiautomator2` driver | ✅ | installed (`8.7.2`) | |
| `cursor-agent` (Cursor CLI) | — | not needed; `tools/cursor-heal/heal.mjs` is the sidecar | |
| `ANDROID_HOME` | ✅ | `C:\Users\PC\scoop\apps\android-clt\current` | Picked up by apkanalyzer / sdkmanager |

Everything needed to run mobile features end-to-end is in place.

### 3.1 How to pin `delivery.mobile.aapt2-path` without committing a user-specific absolute path

Preferred pattern (matches the existing `KEEL_EXTRA_ARGS` convention in `keel-env.local.bat`, which is gitignored):

```bat
set "KEEL_EXTRA_ARGS=%KEEL_EXTRA_ARGS% --delivery.mobile.aapt2-path=C:\Users\You\scoop\apps\android-clt\current\build-tools\35.0.0\aapt2.exe"
```

`start-portal.cmd` sources `keel-env.local.bat` and appends `KEEL_EXTRA_ARGS` to the JVM args, so the override only applies to **this host**. Never commit an absolute path to `application.properties` — it will break every other dev's build.

Equivalent env-var form (relaxed binding; Spring Boot translates automatically):

```bat
set "DELIVERY_MOBILE_AAPT2_PATH=C:\Users\You\scoop\apps\android-clt\current\build-tools\35.0.0\aapt2.exe"
```

---

## 4. Quick smoke check

After a fresh install run these in order — each one should print a version or a success line, not an error.

```powershell
java -version
mvn -v
node --version
adb --version
apkanalyzer --help | Select-Object -First 1
aapt2 version
appium --version
appium driver list --installed
```

If any of them fails, go back to the matching section and fix that tool before starting the portal.

---

## 5. What breaks when each piece is missing

| Missing | Symptom | Where the user sees it |
|-|-|-|
| JDK 21 | portal fails to boot with `UnsupportedClassVersionError` | shell |
| Maven | `start-keel.cmd` / `start-portal.cmd` prints "mvn: command not found" | shell |
| Node | Cursor heal sidecar can't start; Precision and mobile Precision fall back to Keel | job status page shows `PRECISION_FALLBACK` banner |
| adb | Mobile Devices panel shows zero phones even when a phone is plugged in | `/projects/{id}#mobile-devices-panel` |
| apkanalyzer AND aapt2 | APK upload returns `INSPECTION_FAILED` | drag-drop on `/projects/{id}` |
| appium | Mobile run starts, then fails at `session start` with `Could not start Appium` | `/mobile/runs/{runId}` status |
| uiautomator2 driver | Appium starts but refuses the capability set | run status |
| Mobile runner process | Runs stay stuck in `QUEUED` because nothing claims them | `/mobile/runs/{runId}` status |
| `CURSOR_API_KEY` | Precision calls return `providerAvailable=false`; `/admin/mobile-precision` dashboard shows `keel` picks everywhere | `/admin/mobile-precision` |
| `KEEL_RUNNER_TOKEN` | Runner exits at start with "KEEL_RUNNER_TOKEN is required" | runner stdout |

---

## 6. CI / testing note

The repo's test suite runs with `delivery.mobile.enabled=true` (flipped 2026-11-23) and `delivery.mobile.live-tests=false`. No real Appium or device is needed to run `mvn test`. Set `delivery.mobile.live-tests=true` only on a host where a phone and Appium are permanently attached. The dual-opt-in path (`delivery.mobile.enabled=true` + `keel.mobile.ci-opt-in=true`) still exists for shell scripts; see `scripts/ci/check-mobile-flag-off.sh`.
