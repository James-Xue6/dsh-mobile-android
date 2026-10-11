// [V3.56 · 只读] 扫每个会话文件里的记录 type 分布，找"history 页里混入非 event 记录"的会话。
// 背景：网关 dsh-host-adapter.mjs:22 `if (value.type !== 'event') throw new Error('DSH Session V4
// history requires event records')` ⇒ 一旦 history 页里出现非 event 记录，整页抛错 ⇒ App 侧静默空列表。
import fs from 'node:fs';
import path from 'node:path';
import { decodeAllFrames } from './zstd-frames.mjs';

const ROOT = 'C:/Users/Administrator/.dsh/sessions';
function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.zstd')) out.push(p);
  }
  return out;
}

const files = walk(ROOT, []);
const rows = [];
for (const f of files) {
  const buf = fs.readFileSync(f);
  const { text, frames } = decodeAllFrames(buf);
  if (!text) continue;
  const types = new Map();
  for (const line of text.split('\n')) {
    const t = line.match(/"type"\s*:\s*"([a-z-]+)"/);
    if (t) types.set(t[1], (types.get(t[1]) || 0) + 1);
  }
  const nonEvent = [...types.entries()].filter(([k]) => k !== 'event' && k !== 'system');
  if (nonEvent.length) {
    rows.push([text.length, frames, nonEvent.map(([k, v]) => `${k}x${v}`).join(','), f.replace(ROOT + '/', '')]);
  }
}
rows.sort((a, b) => b[0] - a[0]);
console.log(`有非-event记录的会话文件: ${rows.length} / ${files.length}`);
rows.slice(0, 25).forEach(([len, fr, t, p]) => console.log(`  文本${len}B/${fr}帧  [${t}]  ${p}`));
