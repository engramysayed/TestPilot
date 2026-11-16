# Preferred Test Hooks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Project Settings can name extra HTML attributes (e.g. `data-axis-test-id`) that outrank `id` / `data-testid` / CSS / XPath and are shared per host folder.

**Architecture:** `PreferredHooksStore` reads/writes `delivery-store/<domain>/preferred-hooks.json`. Settings GET/PATCH expose a comma-separated string. Execute activates those names on a thread-local so every `DomCandidateExtractor.extract` call ranks them first.

**Tech Stack:** Java 21, Spring MVC, Jsoup, TestNG, existing project Settings tab.

## Global Constraints

- No Claude / Anthropic models.
- Attribute names only — no pasted CSS/XPath.
- Shared on the host folder, not per `prj_*`.
- Preferred hook beats `id` on the same node.
- Empty list = current built-in rank.

---

### Task 1: Store + sanitize

**Files:**
- Create: `src/main/java/delivery/store/PreferredHooksStore.java`
- Test: `src/test/java/delivery/store/PreferredHooksStoreTest.java`

**Interfaces:**
- Produces: `parse(String)`, `load(Path storeRoot, String baseUrl)`, `save(...)`, `activate(List<String>)`, `current()`, `FILE_NAME = "preferred-hooks.json"`

- [x] Tests + store implementation (this session)

### Task 2: Extractor ranks preferred first

**Files:**
- Modify: `src/main/java/delivery/authoring/DomCandidateExtractor.java`
- Test: `src/test/java/delivery/authoring/DomCandidateExtractorTest.java`

- [x] Preferred attr beats `id`; no text-xpath fallback on that node

### Task 3: Settings API + UI

**Files:**
- Modify: `PatchProjectRequest`, `ProjectController.projectToMap`, `PortalStore.updateOwnedProject`
- Modify: `project-detail.html` Settings form
- Test: `ProjectPatchApiTest`

- [x] PATCH/GET `preferredHooks`; writes domain JSON

### Task 4: Execute uses the store

**Files:**
- Modify: `ProvePhase.prove` activate hooks for the job thread

- [x] Load via storeRoot + baseUrl

---
