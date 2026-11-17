#!/usr/bin/env node
/**
 * Prints the Cursor SDK's live model list as JSON to stdout.
 * Reads the API key from CURSOR_API_KEY. Exits non-zero on failure.
 */
import { listCloudModels } from "@cursor/sdk";

const apiKey = process.env.CURSOR_API_KEY;
if (!apiKey || !apiKey.trim()) {
  process.stderr.write("MISSING_API_KEY\n");
  process.exit(2);
}

try {
  const models = await listCloudModels({ apiKey });
  const rows = (models || []).map((m) => ({
    id: m.id,
    displayName: m.displayName || m.id,
    description: m.description || "",
    aliases: m.aliases || [],
  }));
  process.stdout.write(JSON.stringify(rows));
} catch (e) {
  process.stderr.write("LIST_FAILED: " + (e && e.message ? e.message : String(e)) + "\n");
  process.exit(1);
}
