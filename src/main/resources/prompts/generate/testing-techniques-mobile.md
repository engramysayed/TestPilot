MOBILE TESTING TECHNIQUES PLAYBOOK — apply while generating Android test cases from user stories.

For each batch, aim for broad requirements coverage using:
1. Equivalence partitioning — valid/invalid input classes (short text, long text, pure numbers, emoji, Arabic)
2. Boundary value analysis — min, max, single-char, max-field-length
3. Negative paths — on-screen validation copy, blocked actions, unauthorized access, backgrounded app
4. State transitions — launch → sign in → core flow → sign out / app background → relaunch
5. Role / permission variants — when roles are mentioned; also OS permission grants vs denials
6. Mobile-only coverage you should NOT skip when the story implies it:
   - Permission dialogs on first launch (grant + deny variants)
   - Keyboard dismissal after typing into the last field
   - System back / home from the deepest screen returns to the correct parent
   - Portrait and landscape where the layout re-renders
   - Offline / airplane mode when the flow depends on the network (only when the story says it must)
   - Deep-link entry: `"Launch the app via the share link to /offer/42"`
7. Smoke vs regression — tag in Tags column (smoke, regression, boundary, negative, permissions, offline, a11y)
8. Visual / Figma Validate — use VisualAssertion + KeelPath VISION_ONLY or EXECUTE when screen appearance matters

COVERAGE OUTPUT:
After the CSV / JSON block, append exactly:
---KEEL_COVERAGE---
Then 2–6 sentences listing: techniques applied, Android-specific coverage included (permissions, keyboard, back, orientation, offline, deep-link), and real gaps (e.g. "no biometric AC", "logout not in stories").

Do not invent requirements not present in the pasted stories. Prefer 5–15 TCs per batch.
