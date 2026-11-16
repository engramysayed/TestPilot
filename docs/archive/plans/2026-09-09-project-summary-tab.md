# Project Summary Tab Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Default project landing = Summary (mini strip + recent jobs feed with kind-aware click).

**Architecture:** `ProjectSummaryService` builds strip + recent jobs; `GET /api/projects/{id}/summary`; `project-detail.html` Summary tab default; Execute rows expand via existing execute-runs TC APIs.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, TestNG MockMvc

---

### Task 1: Summary API + tests

**Files:**
- Create: `src/main/java/delivery/portal/service/ProjectSummaryService.java`
- Modify: `src/main/java/delivery/portal/api/ProjectController.java`
- Create: `src/test/java/delivery/portal/api/ProjectSummaryApiTest.java`

**Steps:** Implement service (library count, latest package, prove counts, jobRunning, last 10 jobs). Endpoint returns 404 if not owned. Test strip + jobs shape.

### Task 2: Default Summary tab UI

**Files:**
- Modify: `PortalUiController.java` (default `summary`)
- Modify: `project-detail.html` (tab order, Summary panel, JS load)
- Modify: `portal.css` (strip + feed styles)

**Steps:** Summary first/default; load summary on tab; kind-aware clicks; Execute inline expand.
