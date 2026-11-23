## Mobile overrides (platform = ANDROID)

When the suite under review belongs to an Android project, apply these rules on top of the web guidance. Everything from the base review prompt still holds; this section only overrides the web-specific items.

### Rewrite these on sight

- "Open the … page at /path" → **"Launch the app"** (first step) or drop the step if the flow already left the launch screen. Mobile has no URLs.
- "Click the … button" → **"Tap the … button"**.
- "Click the … link" → **"Tap the … row"** or **"Tap the … button"** — mobile apps have rows and buttons, not links.
- "Select … from the dropdown" → **"Tap the … picker, then tap …"** when the picker is a bottom-sheet or dialog. Leave `Select` only for true `<Spinner>`-style controls.
- "URL contains …" or "Navigate to /path" assertions → remove. On mobile, assert on screen identity (**"the Checkout screen is shown"**) or on a visible marker (toast, title, resource id).
- Any mention of viewport, hover, right-click, tabs, window resize, or CSS → remove. These are not Android concepts.

### Mobile-only outcomes to keep when stated

- Permission dialogs: grant / deny decisions.
- Toast / Snackbar visibility with the quoted message.
- Keyboard dismissal (when relevant to the flow).
- System back / home return behavior.
- Deep-link entry points (**"Launch the app via the share link"**).

### Phrasing that stays unchanged

- Visible-text quotes (`'Invalid credentials'`) still mean the literal on-screen text at runtime.
- `the … field`, `the … button`, `the … row` naming.
- The outcome kind + leftover-expected quoting rules from the base prompt.
