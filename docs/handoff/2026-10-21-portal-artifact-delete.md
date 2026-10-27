# Portal artifact delete (2026-10-21)

**Commits:** portal delete `7eb71ed`; codegen page-under-test merge `b46c012` on `main`.

## Shipped

- Members with project access can delete `versions/vN.zip` via Automation tab or **Packages** page (`canDeletePackages` / `canDeletePackage`).
- Owner/Admin: `DELETE /api/projects/{id}/framework` removes live `framework/` only; version ZIPs + IR remain.
- Owner/Admin: delete individual `framework/...` Java files via `DELETE .../artifacts?path=...` (non-ZIP paths).
- UI: hint text, **Delete current framework** + modal on Automation tab; delete buttons gated on API flags, disabled while `jobRunning`.

## Verify

```text
cd keel-portal
mvn test -Dtest=ProjectStoreTest#deleteLiveFrameworkKeepsVersionZip
rebuild-portal.cmd && start-portal.cmd
```

Manual: Member sees Delete on v1/v2 ZIPs; Owner sees **Delete current framework** when `framework/` exists.

## Known gaps

- Does not clear automate proven-case IR or test-case library — only disk framework / chosen artifact.
