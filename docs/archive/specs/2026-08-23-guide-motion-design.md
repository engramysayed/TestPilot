# P5 — Guide motion polish

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P5**  
**Depends on:** P1 Guide AI CSV prompt  

---

## 1. Goal

Polish `/tc-guide` with **scroll-aware motion and navigation** — active TOC highlighting, section reveals on scroll, smooth in-page jumps, and updated Automate deep links — without changing guide content or the AI prompt feature.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Motion scope | **Scroll-triggered** section reveals (IntersectionObserver) + load-time hero fade; keep existing load stagger as fallback |
| TOC | **Scroll-spy** — highlight active section link while scrolling; `aria-current="location"` on active link |
| Smooth scroll | `scroll-behavior: smooth` on `html` for TOC/hash links; disabled when `prefers-reduced-motion: reduce` |
| Sticky copy (§9) | **Out** — no floating copy button (P1 scope); keep inline Copy prompt |
| Link updates | Hero + §9 CTAs: `/upload` → `/automate`; copy text “Open Automate” |
| Mobile TOC | Keep existing 2-column grid; active state works there too |
| Content | **No** changes to prompt text, sections 1–8 copy, or prompt file |
| JS | Single inline script block in `tc-guide.html` (match existing AI prompt script pattern) |

---

## 3. Scope

### In scope

- `tc-guide.html`: `data-guide-section` on sections; hero `guide-reveal`; fix Automate links
- `portal.css`: `.guide-toc a.is-active`, `.guide-section.is-visible`, hero reveal, `scroll-behavior`
- Guide motion JS: IntersectionObserver for sections + scroll-spy for TOC
- `GuideMotionMvcTest`: page 200, contains scroll-spy hook, no stale `Go to Upload` hero CTA to `/upload`

### Out of scope

- Sticky/floating copy control
- New guide sections or prompt changes
- Upload strip changes (`upload.html` guide promo)
- P6 Generate implementation

---

## 4. UX behavior

1. **On load:** Hero fades in; sections below fold start slightly offset/hidden until scrolled into view.
2. **While scrolling:** As each `.guide-section` crosses ~20% viewport, add `.is-visible` (opacity + translateY).
3. **TOC:** Matching `#format` … `#ai` link gets `.is-active` + `aria-current="location"`.
4. **TOC click:** Smooth scroll to section (unless reduced motion).
5. **§9:** “Ready — open Automate” → `/automate`.

---

## 5. CSS additions

- `.guide-section` default: `opacity: 0.35; transform: translateY(12px)` when not `.is-visible` (or use `content-visibility` sparingly — prefer transform/opacity)
- `.guide-section.is-visible` — full opacity, translateY(0), transition 0.45s ease
- `.guide-toc a.is-active` — accent border-left, text color, background (stronger than hover)
- `.guide-hero.guide-reveal` — same `@keyframes guideReveal` as sections
- `@media (prefers-reduced-motion: reduce)` — instant visible, no smooth scroll, no IO animation delays

---

## 6. Testing

| Test | Assert |
|------|--------|
| `GuideMotionMvcTest.guidePage_renders` | GET `/tc-guide` → 200 |
| `GuideMotionMvcTest.guide_hasScrollSpyMarkup` | body contains `data-guide-section` and `guide-toc` |
| `GuideMotionMvcTest.guide_automateLinksNotUploadHero` | no `Go to Upload` with `href="/upload"` in hero; has `/automate` |
| Existing `GuideAiCsvPromptResourceTest` | still passes |

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=GuideMotionMvcTest,GuideAiCsvPromptResourceTest" test`

---

## 7. Success criteria

1. Scrolling the guide feels responsive with sections appearing as you read.
2. TOC always shows which section you are in.
3. Automate CTAs align with P3 nav (no primary Upload from guide hero).
4. `prefers-reduced-motion` users see full content immediately with no jank.

---

## 8. Spec self-review

- [x] No content/prompt changes
- [x] Sticky copy explicitly out
- [x] Link migration to Automate
- [x] Test plan included
- [x] Bounded to templates + CSS + one test class
