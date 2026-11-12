#!/usr/bin/env node
/**
 * Keel agent harness sidecar — local Cursor SDK agent (Phase 2).
 * stdin: one JSON request; stdout: NDJSON events.
 */
import { Agent, Cursor, JsonlLocalAgentStore } from "@cursor/sdk";
/** @typedef {import("@cursor/sdk").Run} SdkRun */
import { mkdirSync, writeFileSync, renameSync, existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { createHash } from "node:crypto";
import { stdin as input } from "node:process";

function readStdin() {
  return new Promise((resolve, reject) => {
    const chunks = [];
    input.on("data", (c) => chunks.push(c));
    input.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    input.on("error", reject);
  });
}

function emit(obj) {
  process.stdout.write(JSON.stringify(obj) + "\n");
}

function emitError(code, message) {
  emit({ type: "error", code, message });
  process.exit(1);
}

function atomicMarker(taskDir, body) {
  const file = join(taskDir, "sdk-touch.marker");
  mkdirSync(taskDir, { recursive: true });
  const tmp = file + ".tmp";
  writeFileSync(tmp, JSON.stringify(body), "utf8");
  renameSync(tmp, file);
}

function assertWorkspace(workspacePath, taskDir) {
  const ws = resolve(workspacePath);
  const isolated = resolve(join(taskDir, "workspace"));
  if (ws !== isolated) {
    emitError("WORKSPACE_DENIED", "workspace must be taskDir/workspace");
  }
  if (!existsSync(ws)) {
    emitError("WORKSPACE_DENIED", "workspace missing");
  }
}

async function collectModified(workspacePath) {
  const manifestPath = join(dirname(workspacePath), "workspace-manifest-before.json");
  if (!existsSync(manifestPath)) {
    return [];
  }
  const before = JSON.parse(readFileSync(manifestPath, "utf8"));
  const after = {};
  walk(workspacePath, workspacePath, after);
  const modified = [];
  for (const [rel, hashAfter] of Object.entries(after)) {
    if (before[rel] !== hashAfter) {
      modified.push(rel);
    }
  }
  return modified;
}

function walk(root, dir, out) {
  for (const name of readdirSync(dir)) {
    if (name === "node_modules" || name === ".git") continue;
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) {
      walk(root, p, out);
    } else if (st.isFile()) {
      const rel = p.slice(root.length + 1).replace(/\\/g, "/");
      const buf = readFileSync(p);
      out[rel] = createHash("sha256").update(buf).digest("hex");
    }
  }
}

function snapshotBefore(workspacePath) {
  const hashes = {};
  walk(workspacePath, workspacePath, hashes);
  const file = join(dirname(workspacePath), "workspace-manifest-before.json");
  writeFileSync(file, JSON.stringify(hashes), "utf8");
}

async function waitForExistingRun(runId, workspacePath, storePath, agentId, apiKey, maxPollMs) {
  const store = new JsonlLocalAgentStore(storePath);
  Cursor.configure({ local: { store } });
  if (agentId) {
    try {
      const agent = await Agent.resume(agentId, {
        local: { cwd: workspacePath, store },
        tools: ["read"],
        disallowedTools: ["shell", "mcp", "task", "webFetch", "webSearch", "delete"],
        model: { id: "auto" },
        apiKey,
      });
      agent.close();
    } catch {
      /* resume optional — getRun is authoritative */
    }
  }
  const pollBudget = Number(maxPollMs) > 0 ? Number(maxPollMs) : 120_000;
  const deadline = Date.now() + pollBudget;
  /** @type {SdkRun | null} */
  let run = null;
  while (Date.now() < deadline) {
    run = await Agent.getRun(runId, {
      runtime: "local",
      cwd: workspacePath,
      store,
    });
    if (!run) {
      throw new Error("getRun returned null");
    }
    emit({ type: "sdk.event", stream: "reconcile.poll", payload: { status: run.status } });
    if (run.status !== "running") break;
    await new Promise((r) => setTimeout(r, 750));
  }
  if (!run) {
    throw new Error("getRun unavailable");
  }
  if (!run || run.status === "running") {
    emit({
      type: "result",
      status: "INTERRUPTED",
      summary: "reconcile poll budget exhausted while run still active",
      payload: { status: "INTERRUPTED", runStatus: "running", recovered: false },
      modifiedFiles: [],
    });
    return;
  }
  emit({ type: "run.started", agentId: run.agentId, runId: run.id });
  let lastStatus = run.status;
  let resultText = run.result;
  const modifiedFiles = await collectModified(workspacePath);
  const terminal =
    lastStatus === "cancelled"
      ? "CANCELLED"
      : lastStatus === "error"
        ? "FAILED"
        : "COMPLETED";
  const summary =
    resultText ||
    (lastStatus === "finished" ? "run finished" : `run ${lastStatus}`);
  emit({
    type: "result",
    status: terminal,
    summary,
    payload: { status: terminal, runStatus: lastStatus, recovered: true },
    modifiedFiles,
  });
}

async function runExecute(req) {
  const taskDir = req.taskDir;
  const workspacePath = resolve(req.workspacePath);
  const storePath = resolve(req.storePath);
  assertWorkspace(workspacePath, taskDir);
  mkdirSync(storePath, { recursive: true });

  const apiKey = process.env.CURSOR_API_KEY;
  if (req.op === "reconcile" && req.runId) {
    if (!apiKey) {
      emitError("MISSING_API_KEY", "CURSOR_API_KEY not set");
    }
    emit({ type: "ack", protocolVersion: 1 });
    try {
      await waitForExistingRun(
        req.runId,
        workspacePath,
        storePath,
        req.agentId,
        apiKey || process.env.CURSOR_API_KEY,
        req.reconcileMaxPollMs
      );
    } catch (e) {
      emitError("RECONCILE_FAILED", e && e.message ? e.message : String(e));
    }
    return;
  }

  if (!apiKey) {
    emitError("MISSING_API_KEY", "CURSOR_API_KEY not set");
  }

  const markerPath = join(taskDir, "sdk-touch.marker");
  if (!req.agentId && existsSync(markerPath)) {
    try {
      const existing = JSON.parse(readFileSync(markerPath, "utf8"));
      if (existing.sdkTouched === true) {
        emitError("RECONCILE_FAILED", "sdk-touch.marker sdkTouched=true — create refused");
      }
    } catch {
      emitError("RECONCILE_FAILED", "sdk-touch.marker unreadable");
    }
  }

  if (!existsSync(markerPath)) {
    atomicMarker(taskDir, { sdkTouched: false, phase: "sidecar_started" });
  }
  emit({ type: "ack", protocolVersion: 1 });

  const store = new JsonlLocalAgentStore(storePath);
  Cursor.configure({ local: { store } });

  snapshotBefore(workspacePath);

  let agent;
  const tools = req.tools || ["read", "grep", "glob", "ls", "edit"];
  const disallowedTools = req.disallowedTools || ["shell", "mcp", "task", "webFetch", "webSearch", "delete"];
  const model = { id: "auto" };

  if (req.agentId) {
    agent = await Agent.resume(req.agentId, {
      local: { cwd: workspacePath, store },
      tools,
      disallowedTools,
      model,
    });
  } else {
    atomicMarker(taskDir, { sdkTouched: true, phase: "create_requested" });
    agent = await Agent.create({
      name: req.agentName || `keel-harness:${req.taskId}`,
      local: { cwd: workspacePath, store },
      tools,
      disallowedTools,
      model,
      apiKey,
    });
    atomicMarker(taskDir, { sdkTouched: true, phase: "create_returned", agentId: agent.agentId });
  }

  const cancelFile = join(taskDir, "control", "cancel.requested");
  const prompt = String(req.objective || "Inspect the workspace and return a short JSON summary.");
  const run = await agent.send(prompt, {
    idempotencyKey: req.idempotencyKey,
    model,
    local: { cwd: workspacePath },
  });

  emit({ type: "run.started", agentId: agent.agentId, runId: run.id });

  let lastStatus = run.status;
  const cancelPoll = setInterval(async () => {
    if (existsSync(cancelFile)) {
      try {
        await run.cancel();
      } catch {
        /* ignore */
      }
    }
  }, 500);

  try {
    for await (const _msg of run.stream()) {
      emit({ type: "sdk.event", stream: "message" });
      if (existsSync(cancelFile)) {
        await run.cancel();
      }
    }
    const finished = await run.wait();
    lastStatus = finished.status;
    const modifiedFiles = await collectModified(workspacePath);
    const terminal =
      lastStatus === "cancelled"
        ? "CANCELLED"
        : lastStatus === "error"
          ? "FAILED"
          : "COMPLETED";
    emit({
      type: "result",
      status: terminal,
      summary: finished.result || "cursor run finished",
      payload: { status: terminal, runStatus: lastStatus },
      modifiedFiles,
    });
  } finally {
    clearInterval(cancelPoll);
    agent.close();
  }
}

async function runCancel(req) {
  const storePath = resolve(req.storePath);
  const workspacePath = resolve(req.workspacePath);
  const store = new JsonlLocalAgentStore(storePath);
  Cursor.configure({ local: { store } });
  if (req.runId) {
    await Agent.cancelRun(req.runId, { runtime: "local", cwd: workspacePath, store });
  }
  emit({ type: "lifecycle", phase: "cancel.confirmed" });
  emit({
    type: "result",
    status: "CANCELLED",
    summary: "cancel op",
    payload: {},
    modifiedFiles: [],
  });
}

async function main() {
  let req;
  try {
    req = JSON.parse(await readStdin());
  } catch {
    emitError("MALFORMED_REQUEST", "invalid JSON");
  }
  try {
    if (req.op === "cancel") {
      await runCancel(req);
      return;
    }
    if (req.op === "execute" || req.op === "reconcile") {
      await runExecute(req);
      return;
    }
    emitError("UNSUPPORTED_OP", String(req.op));
  } catch (e) {
    emitError("SDK_ERROR", e && e.message ? e.message : String(e));
  }
}

main();
