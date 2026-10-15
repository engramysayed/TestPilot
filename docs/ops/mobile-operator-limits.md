# Mobile operator limits

This is the setup and limit note for Android Execute and Automate. It does not say a device, model, or WebView was qualified on this machine.

## What you need before a live run

- A test phone or emulator you are allowed to use. One device per job.
- An APK you are allowed to install, or an app that is already installed.
- The phone's serial. Keel uses that serial only. It will not pick another phone.
- Appium and UiAutomator2, already installed. Keel does not install them.
- A Chromedriver file whose version matches the WebView on the phone, and a path you configured. Keel does not download Chromedriver.
- Login values, OTP, and PIN supplied for that run. Keel does not fetch OTP.
- UI-TARS in Ollama when a step needs vision. Cursor credentials when a step needs Precision.

## What a run does

- Native steps use the accessibility tree.
- A WebView step matches one debuggable view by package, URL, and title. The process name (`WEBVIEW_<pid>`) is not saved.
- If the match is missing, ambiguous, or not debuggable, the step is blocked.
- A system permission dialog stays in the native context.
- Vision taps only a fresh screenshot with matching size, rotation, and insets. A blank or secure screen, low confidence, or a missing model is a failure. Old coordinates are not replayed.
- Precision may pick one candidate. If Cursor is off, capped, or rejected, the step falls back to Keel and the result says so. That fallback is not a Cursor pass.
- A missing vision or Cursor provider is not a pass.

## Troubleshooting

| What you see | What to check |
|--------------|----------------|
| `WEBVIEW_INACCESSIBLE` | The WebView must be debuggable. A release WebView cannot be driven. |
| `CHROMEDRIVER_MISMATCH` | The Chromedriver version must match that WebView. |
| `RUNTIME_DOWNLOAD_BLOCKED` | Put the approved Chromedriver on disk. Do not expect a download. |
| `SYSTEM_DIALOG` | Accept or deny the permission in the native dialog, then continue. |
| `MISSING_PROVIDER` | Start the vision model, or allow Cursor, before a step that needs it. |
| `STORED_COORDINATES` | The generated test must call vision again. Saved x/y is not a pass. |
| `DEVICE_BUSY` | Another job holds the phone. Wait until it releases the device. |

## What is not guaranteed

- Flutter controls that are not exposed to accessibility.
- A WebView that is not debuggable.
- OTP that you did not type in for this run.
- Clearing app data unless the case asks for a reset.
- Network isolation of the app under test. An installed app can still open its own connections.
- iOS, several phones at once, or mobile Bug Hunter.

## Rollback

Stop the job. Keel should drop the Appium session it started and release the device lock. Do not run `adb kill-server`. The previous downloaded ZIP stays in place if a new package fails to compile.
