# Keel redesign — first interactive concept

Status: **design preview complete** (Phases A–E). Product decisions recorded in [`IA-DECISION.md`](IA-DECISION.md). **Production portal parity** shipped 2026-09-22 on the Thymeleaf app (this folder remains the interactive mock reference).
Direction chosen by the user: light workspace, dark sidebar, restrained teal accents.

## Open

From this directory:

```powershell
.\serve.ps1
```

Then open [http://127.0.0.1:8769/](http://127.0.0.1:8769/) in your browser.

Alternatively, run `python -m http.server 8769 --bind 127.0.0.1` or open `index.html`
directly (double-click). No external assets, packages, API connections or application
credentials are required.

All data is illustrative. UI actions are local mock interactions: no real jobs,
uploads, invitations, authentication, project writes or settings changes are performed.

## Review route

1. Overview: outcome summary, attention items, recent runs.
2. Projects: Web and Android concepts; open Axis Mobile or Commerce Web.
3. Test library: search/filter, case selection, editor and revision history.
4. **Generate** (project tab): Ollama stories → live status → preview table with KeelPath, row editor, AI review, save to library.
5. **Execute / Automate**: workbook source, case picker, pre-run review, exports, Automate soft-block; all start jobs that open the **status** page (sidebar chip while running).
6. Execute/Automate tabs: workbook upload, case picker, pre-run review, exports, Automate soft-block.
7. Mobile Execute: APK/installed app, device, package/activity, properties preview.
8. Runs and status: live job polling page (Generate / Execute / Automate), sidebar job chip.
9. Runs and evidence: tabbed assertion views, explicit IR metadata, logs and compare.
10. Automation: separate package concepts and framework structure inspection.
11. Devices and runners: reservation/availability and connection concept.
12. Settings: account/password, profiles, environments, usage, members, integrations.
13. Administration and sign-in preview.

**Phase B:** Project **Summary** tab · **Project settings** (7 tabs) · **TC guide** · **Change email** · **Admin domains** · **Invite acceptance** preview.

**Phase C:** Shared **`runRegistry`** · attention queue · evidence triage · compare/rerun · devices enroll tab.

**Phase D:** **Hybrid IA** — workspace **Generate / Execute / Automate** launchers + project tabs · see [`IA-DECISION.md`](IA-DECISION.md) · **Status vs Evidence** banners · **TC guide** 9 sections · **Hunt** status job · **Package** source viewer · **History** diff.

**Phase E:** **UI parity closure** (M1–M4) · **Product decisions** N1–N8 approved · walkthrough additions below.

### Phase E walkthrough (new since Phase D)

| Item | Where | Action |
|------|--------|--------|
| TC guide promo | **Overview** | Test data rules → TC guide §5 · Full guide |
| Post-save Automate | **Generate** | Save preview → **Open Automate** banner |
| Bulk generate | **Generate** (scroll down) | CSV file → **Queue bulk generate** → status · recent jobs table |
| Delete package | **Automation** | Row **Delete** or Inspect → **Delete artifact** |
| Clear IR drafts | **Automation** (below packages) | **Clear leftover proven cases** on `ir/` section |

Generate and Bug Hunter are reachable inside the Web project. Mobile Hunt is absent
because it is outside the approved initial mobile scope.

## Design intent

- Keep workspace navigation separate from project-specific testing actions.
- Make failed and blocked outcomes actionable instead of presenting only totals.
- Keep proof status distinct from case authoring and package compilation.
- Group advanced execution settings behind disclosure; show a concise run summary.
- Treat Android as a first-class project type while marking it as planned today.
- Preserve library revisions, roles, explicit Hunt acceptance, provider/fallback
  visibility and properties-based mobile configuration in the eventual product.
- Use consistent spacing, restrained color, semantic labels and visible focus states.

## Current prototype limits

This is a direction-setting preview. Forms, sample totals, filters and run summaries
are illustrative, not a complete client-side application. Run rows open representative
evidence views; use the evidence tabs to switch between checkout failure, order
capture and session signed-out examples. Case dialogs load sample explicit-assertion
steps for TC_ORD_01, TC_SES_01 and TC_014. Some secondary controls explain their
intended behavior rather than executing it. Screenshots in evidence views are drawn
illustrations, not actual run artifacts.

Guide content, archived-project states, full permission variants, exhaustive
validation/error states and production integrations still need screen-by-screen
specification after visual direction approval. Existing features must be mapped to
final routes before any current production UI is replaced.

## Verification performed

- JavaScript parsed successfully with Node's VM compiler.
- Local preview served at `http://127.0.0.1:8769/` via `serve.ps1`.
- Browser verified Overview attention links, tabbed evidence views (checkout,
  order capture, session signed-out), project breadcrumbs, library case editor
  for TC_SES_01, Settings Account tab, and representative navigation routes.
- Navigation buttons have accessible names, including compact icon-only navigation.
- Dialogs include labels, Escape handling, focus restoration and a basic focus trap.
- Source is independent of production templates/CSS and makes no external API calls.

The production site has not been redesigned yet. No commit or publication was made.

## Related approved mobile design

`../../superpowers/specs/2026-09-22-mobile-execution-automation-design.md`

The mobile design sections were approved in conversation; the written specification
is ready for review before implementation planning. UI prototype approval is separate.
