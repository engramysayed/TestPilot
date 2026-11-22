KEEL MOBILE CAPABILITY MANIFEST — classify every generated test case with KeelPath (Android apps).

AUTOMATE (codegen conversion to generated Appium Screen + Test classes):
- Linear flows on screens with stable accessibility IDs, resource IDs, or on-screen text
- Tap, Type, Scroll until, Swipe, Press back / home, Launch the app
- Login with credentials; permission dialogs grant/deny; toast / snackbar with quoted text
- Lab static OTP (requirement names the code) — Type OTP + Tap Verify OTP
- No camera / fingerprint / phone-call capture, no drag-drop across apps, no email magic links

EXECUTE (live Appium prove — snapshot + vision):
- Flows that need settle timing (animations, keyboard dismissal, splash screens, loading spinners)
- Weak or dynamic locators but still on-screen
- Visual assertion on the device screenshot (Qwen / UI-TARS)
- Figma Validate compare per TC or per step when a Figma PNG is uploaded

VISION_ONLY (assert-heavy mobile execute):
- Primary value is VisualAssertion or Figma compare
- Steps minimal (Launch the app, wait, assert the "Home" screen is shown)
- Little or no complex gesture / form interaction

MANUAL (not runnable on Keel today for mobile):
- Camera, QR scan, fingerprint / FaceID / biometric
- SMS / email OTP without a lab code, deep link via SMS click
- Native share sheet to an installed external app; other-app navigation (settings, dialer)
- Push notifications arriving mid-run (only taps on an on-screen notification can be scripted)
- File upload via native picker; media gallery insert
- Rooted-device-only behaviour; USB / Bluetooth / NFC hardware
- API-only or database-only checks with no UI

RULE: Every row MUST set KeelPath to exactly one of: AUTOMATE, EXECUTE, VISION_ONLY, MANUAL.
When unsure between AUTOMATE and EXECUTE, prefer EXECUTE.
When a real native dialog or hardware is required, use MANUAL — do not script a workaround that will drift from the real flow.
