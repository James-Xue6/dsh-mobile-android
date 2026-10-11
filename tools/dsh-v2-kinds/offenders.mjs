// 汇总 18 个 v2 会话里所有 source.kind，并打印"非白名单"那几条消息的上下文。
import fs from 'node:fs';
import path from 'node:path';
import { decompress } from 'fzstd';

const WHITELIST = new Set(['user', 'plugin', 'model', 'tool', 'agent-instructions',
  'session-reference', 'team-message', 'goal', 'skill-invocation', 'skill-catalog',
  'coordinator', 'subagent-report', 'subagent-settled', 'webhook', 'agent-message']);

const root = process.argv[2] || 'C:\\Users\\Administrator\\.dsh\\sessions\\--F-AI-~5DE5~4F5C~53F0~8BBE~8BA1--';
const dirs = fs.readdirSync(root).filter((d) =>
  fs.existsSync(path.join(root, d, 'session.v2.jsonl.zstd')));

const all = new Map();
const offenders = [];

for (const d of dirs) {
  const text = Buffer.from(decompress(new Uint8Array(
    fs.readFileSync(path.join(root, d, 'session.v2.jsonl.zstd'))))).toString('utf8');
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    let o; try { o = JSON.parse(line); } catch { continue; }
    const found = [];
    const walk = (v, depth) => {
      if (!v || typeof v !== 'object' || depth > 8) return;
      if (Array.isArray(v)) { v.forEach((x) => walk(x, depth + 1)); return; }
      for (const [k, val] of Object.entries(v)) {
        if (k === 'source' && val && typeof val === 'object' && typeof val.kind === 'string') {
          found.push(val.kind);
        }
        walk(val, depth + 1);
      }
    };
    walk(o, 0);
    for (const kind of found) {
      all.set(kind, (all.get(kind) || 0) + 1);
      if (!WHITELIST.has(kind)) {
        offenders.push({ dir: d, kind, evt: o.kind ?? o.type, seq: o.seq,
          line: line.slice(0, 700) });
      }
    }
  }
}

console.log('=== 所有 source.kind（含是否在白名单）===');
for (const [k, n] of [...all.entries()].sort((a, b) => b[1] - a[1])) {
  console.log(String(n).padStart(7), k, WHITELIST.has(k) ? 'OK' : '<<< 不在白名单');
}
console.log('\n=== 非白名单消息（' + offenders.length + ' 条）===');
for (const o of offenders) {
  console.log('\n--- ' + o.dir + '  kind=' + o.kind + '  事件=' + o.evt + '  seq=' + o.seq);
  console.log(o.line);
}
