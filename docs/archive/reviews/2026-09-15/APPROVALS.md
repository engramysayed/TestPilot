# Named approvals (P0-01)

These slots must be signed by a **named human**. An agent commit is not an approval. Packet: [APPROVAL-PACKET.md](APPROVAL-PACKET.md).

First-release scope is **shared hosting only**. Dedicated/private-runner rows are deferred, not first-launch blockers.

| Gate | Role | Name | Date | Decision | Notes |
|------|------|------|------|----------|-------|
| First-release result / dry-run / invention contract | Product | _unsigned_ | | | Shared launch product |
| Hosted-target and tenant isolation model | Security | _unsigned_ | | | Shared staging isolation still missing |
| AI provider / data / screenshot retention policy | Privacy | _unsigned_ | | | Live UI-TARS grounding not a launch guarantee |
| Constitution 1.2.0 supersession | Product | _unsigned_ | | | See [constitution-supersession.md](constitution-supersession.md) |
| Dedicated-install identity and CIDR document | Security | _deferred_ | | | Not a first-launch gate. Implementation preserved. See [dedicated-install.md](dedicated-install.md) |

**Recorded 2026-09-20.** Candidate: `2721e6d` (unchanged). Until every **first-release** row has a name, public launch stays **HOLD**.
