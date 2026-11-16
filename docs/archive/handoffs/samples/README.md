# Codegen samples

These `*_Page.java` files are **written by the codegen tests** (`CodegenW2EmitTest`, `CodegenW3EmitTest`, …) every time
they run, so they always show what the current generator emits. Do not hand-edit them.

Regenerate:

```bat
mvn -pl keel-portal "-Dtest=CodegenW2EmitTest,CodegenW3EmitTest" test
```

2026-11-07 changes you will see after regenerating:
- Primary locator follows the shared `LocatorStabilityPolicy` (stable-first): preferred hook → test id → unique id → name → role+name → label → text → CSS.
- Plain fields with fallbacks are one `FirstOf.of(...)` field. The old `_fb1/_fb2` fields (never used, and emitted as
  `By.css(...)`, which does not exist in Selenium) are gone.
