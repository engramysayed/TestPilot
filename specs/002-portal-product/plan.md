# Implementation Plan: 002-portal-product

**Branch**: `002-portal-product`  
**Spec**: [spec.md](./spec.md)  
**Constitution**: v1.1.0

## Technical approach

1. Vendor TAF sources into `customer-framework-template/` (replace prior thin core); keep Freemarker under `templates/` writing into `project.pages` / `project.tests.generated|todo`.
2. Convert TAF `webapp.properties` → `webapp.properties.example`; packager skips filled secrets.
3. Add Spring Security + JPA + H2; entities User, Invite, Project, JobHistory; seed admin from properties; invite accept + login.
4. Persist projects/jobs for ownership and dashboard stats; wire existing ConversionWorker.
5. Thymeleaf: login, invite, dashboard (Chart.js), projects, upload with select/create, status.
6. `docs/ops/windows-vps-setup.md` + `start-portal.bat`.

## Out of scope

Open registration, OAuth, billing, Docker Compose, Postgres migration.
