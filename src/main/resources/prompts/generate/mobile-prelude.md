## Mobile overrides (platform = ANDROID)

The project is an Android app, not a web site. Everything below replaces the web-specific guidance above when it conflicts; everything else from the base prompt still holds.

### Phrasing

- Never say "Open the … page at /path". The app has no URLs. Start a flow with **"Launch the app"** or **"Return to the home screen"**.
- Use **Tap** for touch, not **Click**. Example: "Tap the Sign in button".
- Use **Type** for text entry: "Type in the Email field".
- Use **Scroll until** for lists: "Scroll until the Settings row is visible".
- Use **Swipe left/right** for pagers / dismissible rows.
- Use **Press back** and **Press home** for system gestures.
- Use **Grant** / **Deny** for permission dialogs: "Grant location access", "Deny notifications".
- Use **Toast** / **Snackbar** for transient messages: "The toast 'Saved' is visible".
- Use **Screen** (not "page"): "the Checkout screen is shown".
- Use **the … field** for input controls same as web. **the … button** for buttons. **the … row** for list items.

### Steps mobile tests must cover when relevant

- Permission prompts on first launch (grant or deny).
- Keyboard dismissal after typing into the last field.
- System back from a nested screen returns to the correct parent.
- Portrait and landscape where the layout is known to re-render.
- Offline / airplane mode when the flow depends on the network (only when stated).

### What NEVER to emit for mobile

- URLs or paths (`/login`, `https://…`). Launch + navigate on-screen instead.
- Browser-only concepts: tabs, right-click, hover, resize window, viewport width, CSS selectors.
- `Click` or `click the …` phrasing. Always `Tap`.

### Locator hints (steps only, not generated code)

When the step text names a visible control, the engine will prefer **accessibility id → resource id → visible text → label-sibling → xpath** at bind time. Mentioning the exact label helps. Do NOT invent a resource id in step text (leave that to the framework).

### Example

```
"steps": "1. Launch the app\n2. Tap the Sign in button on the welcome screen\n3. Type in the Email field\n4. Type in the Password field\n5. Tap the Log in button\n6. Confirm the toast 'Invalid credentials' is visible",
"expectedResult": "1. App opens on the welcome screen\n2. The Sign in screen is shown\n3. Email is accepted\n4. Password is accepted\n5. Submit is attempted\n6. The toast 'Invalid credentials' is shown; the Sign in screen stays visible"
```
