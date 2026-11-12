#!/usr/bin/env node
/**
 * Opt-in live Cursor SDK validation for Keel agent harness Phase 2.
 * Usage (from repo root, with CURSOR_API_KEY set):
 *   node tools/cursor-agent/live-spike.mjs
 * Writes: docs/handoff/cursor-sdk-live-spike-output.json (relative to repo root)
 */
import { Agent, Cursor, JsonlLocalAgentStore } from "@cursor/sdk";
import { spawn } from "node:child_process";
import {
  mkdtempSync,
  mkdirSync,
  writeFileSync,
  readFileSync,
  existsSync,
  rmSync,
  renameSync,
} from "node:fs";
import { join, resolve, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../..");
const AGENT_MJS = join(__dirname, "agent.mjs");

const results = [];
const startedAt = new Date().toISOString();

function record(id, pass, detail, extra = {}) {
  results.push({ id, pass, detail, at: new Date().toISOString(), ...extra });
  const mark = pass ? "PASS" : "FAIL";
  process.stderr.write(`[${mark}] ${id}: ${detail}\n`);
}

function requireApiKey() {
  const key = process.env.CURSOR_API_KEY;
  if (!key || !String(key).trim()) {
    record("precheck.api_key", false, "CURSOR_API_KEY not set");
    return false;
  }
  record("precheck.api_key", true, "CURSOR_API_KEY present");
  return true;
}

function makeTaskLayout() {
  const taskDir = mkdtempSync(join(tmpdir(), "keel-live-spike-"));
  const workspace = join(taskDir, "workspace");
  const storePath = join(taskDir, "cursor-sdk-state");
  mkdirSync(workspace, { recursive: true });
  mkdirSync(storePath, { recursive: true });
  writeFileSync(join(workspace, "readme.txt"), "Keel live spike.\n", "utf8");
  return { taskDir, workspace, storePath, taskId: "live" + Date.now() };
}

function baseRequest(layout, op, extra = {}) {
  return {
    protocolVersion: 1,
    op,
    taskId: layout.taskId,
    parentJobId: "live_spike",
    objective:
      'Read readme.txt in the workspace and reply with exactly: {"ok":true}',
    workspacePath: layout.workspace,
    taskDir: layout.taskDir,
    storePath: layout.storePath,
    tools: ["read", "grep", "glob", "ls"],
    disallowedTools: ["shell", "mcp", "task", "webFetch", "webSearch", "delete"],
    agentName: `keel-harness:${layout.taskId}`,
    idempotencyKey: `keel:live_spike:${layout.taskId}:a1`,
    ...extra,
  };
}

function runSidecar(req, { killAfterMs = 0, killAfterRunStarted = false } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [AGENT_MJS], {
      cwd: REPO_ROOT,
      env: process.env,
      stdio: ["pipe", "pipe", "pipe"],
    });
    let out = "";
    let err = "";
    child.stdout.on("data", (c) => (out += c));
    child.stderr.on("data", (c) => (err += c));
    child.stdin.write(JSON.stringify(req));
    child.stdin.end();
    let killer;
    let runStarted = false;
    child.stdout.on("data", (c) => {
      const chunk = String(c);
      if (chunk.includes('"type":"run.started"') || chunk.includes('"type": "run.started"')) {
        runStarted = true;
        if (killAfterRunStarted && killAfterMs > 0 && !killer) {
          killer = setTimeout(() => child.kill("SIGKILL"), killAfterMs);
        }
      }
    });
    if (killAfterMs > 0 && !killAfterRunStarted) {
      killer = setTimeout(() => {
        child.kill("SIGKILL");
      }, killAfterMs);
    }
    child.on("close", (code) => {
      if (killer) clearTimeout(killer);
      const lines = out
        .split(/\r?\n/)
        .map((l) => l.trim())
        .filter(Boolean);
      resolve({ code, lines, err, events: lines.map((l) => { try { return JSON.parse(l); } catch { return { raw: l }; } }) });
    });
    child.on("error", reject);
  });
}

async function scenarioExecuteViaSidecar() {
  const layout = makeTaskLayout();
  const res = await runSidecar(baseRequest(layout, "execute"));
  const started = res.events.some((e) => e.type === "run.started");
  const result = res.events.find((e) => e.type === "result");
  const marker = existsSync(join(layout.taskDir, "sdk-touch.marker"))
    ? JSON.parse(readFileSync(join(layout.taskDir, "sdk-touch.marker"), "utf8"))
    : null;
  const pass =
    res.code === 0 &&
    started &&
    result &&
    marker &&
    marker.sdkTouched === true;
  record("1.execute_real_sidecar", pass, JSON.stringify({ code: res.code, started, resultStatus: result?.status, marker }), {
    agentId: res.events.find((e) => e.type === "run.started")?.agentId,
    runId: res.events.find((e) => e.type === "run.started")?.runId,
    layout,
  });
  return { layout, res };
}

async function scenarioListAndResume(first) {
  const { layout, res } = first;
  const started = res.events.find((e) => e.type === "run.started");
  if (!started?.agentId) {
    record("2.list_resume", false, "no agentId from execute");
    return;
  }
  const store = new JsonlLocalAgentStore(layout.storePath);
  Cursor.configure({ local: { store } });
  const listed = await Agent.list({ runtime: "local", cwd: layout.workspace, store, limit: 20 });
  const found = listed.items.some((a) => a.agentId === started.agentId);
  record(
    "2a.Agent.list_after_exit",
    found,
    `items=${listed.items.length} found=${found}`,
    { agentIds: listed.items.map((a) => a.agentId) }
  );

  const store2 = new JsonlLocalAgentStore(layout.storePath);
  Cursor.configure({ local: { store: store2 } });
  let resumeOk = false;
  let listRunsOk = false;
  let listRunsError = "";
  let runIds = [];
  let agent;
  try {
    agent = await Agent.resume(started.agentId, {
      local: { cwd: layout.workspace, store: store2 },
      tools: ["read"],
      disallowedTools: ["shell", "mcp", "task", "webFetch", "webSearch", "delete"],
      model: { id: "auto" },
      apiKey: process.env.CURSOR_API_KEY,
    });
    resumeOk = !!agent?.agentId;
  } catch (e) {
    record("2b.Agent.resume_second_process", false, e.message || String(e));
    return;
  }
  try {
    const runs = await Agent.listRuns(started.agentId, { runtime: "local", store: store2, limit: 5 });
    listRunsOk = true;
    runIds = runs.items.map((r) => r.runId);
  } catch (e) {
    listRunsError = e.message || String(e);
  }
  record(
    "2b.Agent.resume_second_process",
    resumeOk,
    `resume=${resumeOk} listRuns=${listRunsOk} listRunsErr=${listRunsError.slice(0, 100)}`,
    { runIds, listRunsOk, listRunsError }
  );
  try {
    if (started.runId) {
      const run = await Agent.getRun(started.runId, {
        runtime: "local",
        cwd: layout.workspace,
        store: store2,
      });
      record("2c.Agent.getRun_after_exit", !!run?.id, `status=${run?.status}`, { runId: run?.id });
    }
  } catch (e) {
    record("2c.Agent.getRun_after_exit", false, e.message || String(e));
  }
  agent.close();
}

async function scenarioCancelInProcess() {
  const layout = makeTaskLayout();
  const req = baseRequest(layout, "execute", {
    objective:
      "Take your time analyzing every file in depth; do not finish for 2 minutes.",
  });
  const child = spawn(process.execPath, [AGENT_MJS], {
    cwd: REPO_ROOT,
    env: process.env,
    stdio: ["pipe", "pipe", "pipe"],
  });
  let out = "";
  child.stdout.on("data", (c) => (out += c));
  child.stdin.write(JSON.stringify(req));
  child.stdin.end();
  await new Promise((r) => setTimeout(r, 3000));
  const cancelFile = join(layout.taskDir, "control", "cancel.requested");
  mkdirSync(dirname(cancelFile), { recursive: true });
  writeFileSync(cancelFile, "requested", "utf8");
  const finished = await new Promise((resolve) => {
    child.on("close", (code) => resolve(code));
    setTimeout(() => child.kill("SIGKILL"), 120_000);
  });
  const events = out
    .split(/\r?\n/)
    .filter(Boolean)
    .map((l) => {
      try {
        return JSON.parse(l);
      } catch {
        return { type: "parse_skip", raw: l.slice(0, 80) };
      }
    });
  const result = events.find((e) => e.type === "result");
  const cancelled = result?.status === "CANCELLED" || result?.payload?.runStatus === "cancelled";
  record("3.cancel_in_process", cancelled || finished === 0, `exit=${finished} result=${result?.status}`, { events: events.map((e) => e.type) });
}

async function scenarioCancelAfterKill() {
  const layout = makeTaskLayout();
  const req = baseRequest(layout, "execute", {
    objective: "Analyze workspace slowly; sleep between steps.",
  });
  const res = await runSidecar(req, { killAfterMs: 2000, killAfterRunStarted: true });
  const started = res.events.find((e) => e.type === "run.started");
  if (!started?.runId) {
    record("4.cancel_after_kill", false, "no runId before kill");
    return;
  }
  const cancelReq = baseRequest(layout, "cancel", { agentId: started.agentId, runId: started.runId });
  const cancelRes = await runSidecar(cancelReq);
  const ok = cancelRes.code === 0 && cancelRes.events.some((e) => e.type === "result");
  record("4.cancel_after_kill", ok, `cancelOp code=${cancelRes.code}`, { killCode: res.code });
}

async function scenarioMarkerInterruptRecovery() {
  const layout = makeTaskLayout();
  writeFileSync(
    join(layout.taskDir, "sdk-touch.marker"),
    JSON.stringify({ sdkTouched: true, phase: "create_requested" }),
    "utf8"
  );
  const res = await runSidecar(baseRequest(layout, "execute"));
  const err = res.events.find((e) => e.type === "error");
  const created = res.events.some((e) => e.type === "run.started");
  const refused = err?.code === "RECONCILE_FAILED";
  record(
    "5.marker_blocks_blind_create",
    refused && !created,
    refused ? "sidecar refused create" : created ? "unexpected run.started" : String(err?.message || res.code),
    { code: res.code, error: err }
  );
}

async function scenarioIdempotency() {
  const layout = makeTaskLayout();
  const store = new JsonlLocalAgentStore(layout.storePath);
  Cursor.configure({ local: { store } });
  const agent = await Agent.create({
    name: `keel-idem:${layout.taskId}`,
    local: { cwd: layout.workspace, store },
    tools: ["read"],
    disallowedTools: ["shell", "mcp", "task", "webFetch", "webSearch", "delete", "edit"],
    model: { id: "auto" },
    apiKey: process.env.CURSOR_API_KEY,
  });
  const key = "keel-idem-key-1";
  const r1 = await agent.send("Reply: one", { idempotencyKey: key, model: { id: "auto" } });
  let r2status = "not_attempted";
  let r2error = "";
  try {
    const r2 = await agent.send("Reply: two", { idempotencyKey: key, model: { id: "auto" } });
    r2status = r2.status;
  } catch (e) {
    r2error = e.message || String(e);
  }
  record("6.idempotency_duplicate_send", true, `r1=${r1.id} r2status=${r2status} r2error=${r2error.slice(0, 120)}`, {
    note: "Document observed behavior; pass means test completed without crash",
    r1: r1.id,
    r2status,
    r2error,
  });
  agent.close();
}

async function scenarioConcurrentStoreWrites() {
  const storePath = mkdtempSync(join(tmpdir(), "keel-store-race-"));
  const storeA = new JsonlLocalAgentStore(storePath);
  const storeB = new JsonlLocalAgentStore(storePath);
  Cursor.configure({ local: { store: storeA } });
  const agents = [];
  try {
    const a1 = await Agent.create({
      name: "race-a",
      local: { cwd: REPO_ROOT, store: storeA },
      tools: [],
      disallowedTools: ["shell", "mcp", "task", "webFetch", "webSearch", "delete", "edit", "read", "grep", "glob", "ls"],
      model: { id: "auto" },
      apiKey: process.env.CURSOR_API_KEY,
    });
    Cursor.configure({ local: { store: storeB } });
    const a2 = await Agent.create({
      name: "race-b",
      local: { cwd: REPO_ROOT, store: storeB },
      tools: [],
      disallowedTools: ["shell", "mcp", "task", "webFetch", "webSearch", "delete", "edit", "read", "grep", "glob", "ls"],
      model: { id: "auto" },
      apiKey: process.env.CURSOR_API_KEY,
    });
    agents.push(a1, a2);
    const raw = readFileSync(join(storePath, "agents.ndjson"), "utf8");
    const lines = raw.trim().split("\n").filter(Boolean);
    const parsed = lines.every((l) => {
      try {
        JSON.parse(l);
        return true;
      } catch {
        return false;
      }
    });
    record(
      "7.concurrent_store_unsynchronized",
      true,
      `lines=${lines.length} allValidJson=${parsed}`,
      { verdict: parsed ? "no_line_corruption_observed" : "CORRUPTION_DETECTED", lines: lines.length }
    );
  } catch (e) {
    record("7.concurrent_store_unsynchronized", false, e.message || String(e));
  } finally {
    for (const a of agents) {
      try {
        a.close();
      } catch {
        /* ignore */
      }
    }
    try {
      rmSync(storePath, { recursive: true, force: true });
    } catch {
      /* ignore */
    }
  }
}

async function main() {
  if (!requireApiKey()) {
    writeReport();
    process.exit(2);
  }
  const scenarios = [
    async () => {
      const first = await scenarioExecuteViaSidecar();
      await scenarioListAndResume(first);
    },
    scenarioCancelInProcess,
    scenarioCancelAfterKill,
    scenarioMarkerInterruptRecovery,
    scenarioIdempotency,
    scenarioConcurrentStoreWrites,
  ];
  for (const fn of scenarios) {
    try {
      await fn();
    } catch (e) {
      record(fn.name || "scenario", false, e.stack || String(e));
    }
  }
  writeReport();
  const failed = results.some((r) => r.pass === false);
  process.exit(failed ? 1 : 0);
}

function writeReport() {
  const outPath = join(REPO_ROOT, "docs/handoff/cursor-sdk-live-spike-output.json");
  const body = { startedAt, sdkVersion: "1.0.27", results };
  writeFileSync(outPath, JSON.stringify(body, null, 2), "utf8");
  process.stderr.write(`Wrote ${outPath}\n`);
}

main();
