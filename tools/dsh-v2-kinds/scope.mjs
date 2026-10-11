// 统计：v2-only 会话里，哪些含非白名单 source.kind（= 迁移必然失败的那些）。
import fs from 'node:fs';
import path from 'node:path';
import { decompress } from 'fzstd';

const WHITELIST = new Set(['user', 'plugin', 'model', 'tool', 'agent-instructions',
  'session-reference', 'team-message', 'goal', 'skill-invocation', 'skill-catalog',
  'coordinator', 'subagent-report', 'subagent-settled', 'webhook', 'agent-message']);

const root = process.argv[2] || 'C:\\Users\\Administrator\\.dsh\\sessions\\--F-AI-~5DE5~4F5C~53F0~8BBE~8BA1--';
const dirs = fs.readdirSync(root).filter((d) =>
  fs.existsSync(path.join(root, d, 'session.v2.jsonl.zstd')));

const bad = [];
const kindCount = new Map();
for (const d of dirs) {
  const text = Buffer.from(decompress(new Uint8Array(
    fs.readFileSync(path.join(root, d, 'session.v2.jsonl.zstd'))))).toString('utf8');
  const found = new Set();
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    let o; try { o = JSON.parse(line); } catch { continue; }
    const walk = (v, depth) => {
      if (!v || typeof v !== 'object' || depth > 8) return;
      if (Array.isArray(v)) { v.forEach((x) => walk(x, depth + 1)); return; }
      for (const [k, val] of Object.entries(v)) {
        if (k === 'source' && val && typeof val === 'object' && typeof val.kind === 'string'
            && !WHITELIST.has(val.kind)) {
          found.add(val.kind);
          kindCount.set(val.kind, (kindCount.get(val.kind) || 0) + 1);
        }
        walk(val, depth + 1);
      }
    };
    walk(o, 0);
  }
  if (found.size) bad.push([d, [...found].join('+')]);
}
console.log('v2-only 会话总数:', dirs.length);
console.log('含非白名单 kind（迁移必失败）:', bad.length);
for (const [d, k] of bad) console.log('  ', d, k);
console.log('\n非白名单 kind 计数:', [...kindCount.entries()]);
