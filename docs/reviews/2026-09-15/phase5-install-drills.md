# Phase 5 isolated-install drills (2026-09-17)

Workstation `Ramy-Sayed`. Public launch remains **HOLD**.

These are **two Spring Boot test installations** with distinct store roots and `delivery.install.mode`. They are not production VMs, not a kernel firewall, and not a signed P0-01.

`InstallNetworkBridge` writes **JVM-wide** system properties. Install mode/CIDRs are installation-wide and must not vary by tenant or job. Shared and dedicated drills therefore run in **separate Maven JVMs**. Do not pass both test classes in one `-Dtest=`.

## Commands (separate JVMs)

```
mvn "-Dtest=delivery.ops.SharedInstallDrillTest" "-Ddelivery.install.drill=shared" "-Dinstall.drills.skip=true" test
mvn "-Dtest=delivery.ops.DedicatedInstallDrillTest" "-Ddelivery.install.drill=dedicated" "-Dinstall.drills.skip=true" test
```

Default `mvn test` excludes these classes so they cannot share a Surefire fork. CI runs the two commands above. Opt-in combined `mvn -Dinstall.drills.skip=false test` still forks each execution separately.

Recorded 2026-09-17 on workstation `Ramy-Sayed`:

- Shared: PID **21452**, 3 tests, 0 failures, 2026-09-17T13:20:24+03:00
- Dedicated: PID **6732**, 2 tests, 0 failures, 2026-09-17T13:20:46+03:00
- Combined `-Dtest=SharedInstallDrillTest,DedicatedInstallDrillTest` with `-Ddelivery.install.drill=shared`: BUILD FAILURE (`DedicatedInstallDrillTest.requireOwnJvm` expected `dedicated` but found `shared`)

## Shared drill (`delivery.install.mode=shared`)

Store: `./target/p5-shared-install/store`. Poison `delivery.install.private-cidrs=10.0.0.0/8` is present and **must not** apply.

- `/api/health` reports `installMode=shared`; `/api/ready` store writable.
- `TargetNetworkPolicy.forJob` after `InstallNetworkBridge` blocks `http://10.0.0.8:8080`.
- Two tenants: Bob cannot list Alice artifacts (404). Snapshot/restore of this store-root restores sentinel ZIP bytes and SHA-256.
- Generate **import** (JSON library, not live LLM stories) → dry-run Execute COMPLETED → dry-run Automate NEW COMPLETED → download ZIP → `mvn test-compile` on the unzipped customer POM.

## Dedicated drill (`delivery.install.mode=dedicated`, `10.0.0.0/8`)

Store: `./target/p5-dedicated-install/store`.

- `/api/health` reports `installMode=dedicated`.
- `forJob("http://10.0.0.8:8080/app")` allows that origin; `192.168.0.4` stays blocked.
- Same tenant-boundary + snapshot/restore on **this** store-root.

## Still open

- Production shared/dedicated hosts
- Kernel / worker-network isolation on those hosts
- Restore on those hosts' FileStores
- Live LLM Generate and live browser Automate/Execute against representative apps
- Named product/security/privacy approvals
