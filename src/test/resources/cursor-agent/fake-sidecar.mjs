#!/usr/bin/env node
/**
 * Deterministic sidecar stub for Maven tests — no @cursor/sdk.
 */
import { readFileSync, writeFileSync, renameSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";

function readStdin() {
  return readFileSync(0, "utf8");
}

function atomicMarker(taskDir, body) {
  const file = join(taskDir, "sdk-touch.marker");
  mkdirSync(taskDir, { recursive: true });
  const tmp = file + ".tmp";
  writeFileSync(tmp, JSON.stringify(body), "utf8");
  renameSync(tmp, file);
}

function emit(obj) {
  process.stdout.write(JSON.stringify(obj) + "\n");
}

const raw = readStdin();
const req = JSON.parse(raw);
const taskDir = req.taskDir;

emit({ type: "ack", protocolVersion: 1 });

if (req.op === "cancel") {
  emit({ type: "lifecycle", phase: "cancel.confirmed" });
  emit({
    type: "result",
    status: "CANCELLED",
    summary: "fake cancel",
    payload: {},
    modifiedFiles: [],
  });
  process.exit(0);
}

atomicMarker(taskDir, { sdkTouched: false, phase: "sidecar_started" });

if (req.op === "reconcile" && req.runId) {
  emit({
    type: "run.started",
    agentId: req.agentId || "fake-agent-resume",
    runId: req.runId,
  });
  emit({
    type: "result",
    status: "COMPLETED",
    summary: "fake reconcile getRun",
    payload: { provider: "FAKE_SIDECAR", recovered: true },
    modifiedFiles: [],
  });
  process.exit(0);
}

atomicMarker(taskDir, { sdkTouched: true, phase: "create_requested" });

const agentId = "fake-agent-" + req.taskId;
const runId = "fake-run-" + req.taskId;
emit({ type: "run.started", agentId, runId });
emit({
  type: "result",
  status: "COMPLETED",
  summary: "fake runtime completed",
  payload: { provider: "FAKE_SIDECAR", status: "PASS" },
  modifiedFiles: [],
});
