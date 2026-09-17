#!/usr/bin/env node
import fs from "fs";

const dest = process.argv[2];
if (!dest) {
  process.stderr.write("usage: capture-stdin.mjs <outfile>\n");
  process.exit(2);
}
let buf = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  buf += chunk;
});
process.stdin.on("end", () => {
  fs.writeFileSync(dest, buf);
  process.stdout.write("{\"candidateId\":\"\"}\n");
});
