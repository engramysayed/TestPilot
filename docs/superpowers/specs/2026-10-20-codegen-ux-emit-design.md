# Codegen UX emit (2026-10-20)

## Goals

- Drop echo/html and Expected-only field visibility asserts from emitted framework code.
- Collapse login to `login(user, pass)` when asserts sit between type/click steps.
- Keep proven locators for text assertions (no whole-body fallback).
- Title-based test class (`*Test`) and full-title test method names.
- Class-level page fields; assign in `@Test`; `@BeforeMethod` may run prerequisite login via a private helper with local page vars only.
- Test data keys: `{env}_username`, `{env}_password`, `{env}_otp` when project **Test data env** is set; otherwise `username`, `password`, `otp` (no env prefix). Values in `delivery-testdata.properties`; read via `PropertyReader.getProperty` / `Secrets.property` for secrets.

## Non-goals

- Changing prove/heal IR retention (filter at emit only).
