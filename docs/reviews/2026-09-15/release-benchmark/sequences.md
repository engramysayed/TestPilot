# P0-03 sequences

Run against the static pages in `pages/` and `datasets/cases.csv`. Capture ZIP identity and TestNG output each time.

1. **NEW** — import CSV, Automate NEW, live (not dry-run). Save version A.
2. **Downloaded replay #1** — unzip version A, `mvn test` with `BASE_WEB` pointing at the fixture server. TC_ASSERT_FALSE must fail the suite.
3. **UPDATE unchanged** — same CSV, same environment. Save version B. Unchanged TODO must stay TODO.
4. **Downloaded replay #2** — unzip version B; compare TestNG verdicts to step 2 except documented UPDATE effects.
5. **UPDATE changed prerequisite** — change TC_CART expected cart text. TC_CHECKOUT must not keep stale PASS proof.
6. **Repeated UPDATE** — unchanged after step 5. Emission of identical inputs is stable aside from documented metadata (P1-03).
7. **Case removal** — drop TC_LOGIN_OK from the CSV; its generated class must disappear.
8. **PASS→TODO** — make TC_URL_OK unprovable (wrong URL in data). Stale passed class must not remain.
9. **Changed environment** — point BASE_WEB at a second fixture port; prior PASS is not reused.
10. **Concurrent same-host** — two jobs, overlapping TC IDs, distinct sentinels in expected text. After P2-02 they must not share work files. Until P2-02, treat a collision as the known F01 risk.
