# Guide motion polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.  
> **Do not commit unless the user asks.**

**Goal:** Add scroll-spy TOC, IntersectionObserver section reveals, smooth scroll, and Automate link fixes on `/tc-guide`.

**Architecture:** Extend `tc-guide.html` with `data-guide-section` attributes and motion JS; CSS for `.is-active` / `.is-visible`; MVC smoke test.

**Tech Stack:** Thymeleaf, portal.css, vanilla JS, TestNG + MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-23-guide-motion-design.md`

## Global Constraints

- No guide content or prompt file changes.
- `/upload` → `/automate` for hero + §9 primary CTAs only on tc-guide.
- `prefers-reduced-motion: reduce` disables smooth scroll and scroll animations.
- Do not commit unless asked.

---

### Task 1: Guide motion CSS

**Files:**
- Modify: `src/main/resources/static/css/portal.css`

**Interfaces:**
- `.guide-section` hidden state + `.guide-section.is-visible`
- `.guide-toc a.is-active`
- `.guide-hero.guide-reveal`
- `html { scroll-behavior: smooth }` with reduced-motion override
- Keep existing `.guide-reveal` load animation or reconcile with scroll reveal

---

### Task 2: tc-guide.html markup + links

**Files:**
- Modify: `src/main/resources/templates/tc-guide.html`

**Interfaces:**
- Add `data-guide-section="{id}"` to each `.guide-section`
- Hero: `guide-reveal` class; CTA `Go to Automate` → `/automate`
- §9: `Ready — open Automate` → `/automate`
- TOC links unchanged (hash anchors)

---

### Task 3: Guide motion JavaScript

**Files:**
- Modify: `src/main/resources/templates/tc-guide.html` (script block)

**Interfaces:**
- `initGuideMotion()` — IntersectionObserver on `[data-guide-section]`, threshold ~0.15–0.2
- Scroll-spy: set `.is-active` + `aria-current` on matching TOC `a[href^="#"]`
- Reduced motion: mark all sections visible immediately, skip observer animations

---

### Task 4: GuideMotionMvcTest

**Files:**
- Create: `src/test/java/delivery/portal/web/GuideMotionMvcTest.java`

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=GuideMotionMvcTest,GuideAiCsvPromptResourceTest" test`
