// [V3.53 · 只读扫描] 用**自切帧**解码全部会话文件，找 source.kind = "instruction-hint" 的越界记录。
// 只读：不改、不写、不删。
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

const all = walk(ROOT, []);
let hits = [], total = 0, big = [], kinds = new Map(), frameTotal = 0;
for (const f of all) {
  const buf = fs.readFileSync(f);
  const r = decodeAllFrames(buf);
  total += r.text.length;
  frameTotal += r.frames;
  big.push([r.text.length, r.frames, buf.length, f.replace(ROOT + '/', '')]);
  const n = (r.text.match(/instruction-hint/g) || []).length;
  if (n > 0) hits.push([n, f.replace(ROOT + '/', '')]);
  for (const m of r.text.matchAll(/"kind"\s*:\s*"([a-z-]+)"/g)) {
    kinds.set(m[1], (kinds.get(m[1]) || 0) + 1);
  }
}
console.log(`文件数 ${all.length}  解出总字节 ${total}  总帧数 ${frameTotal}`);
console.log('--- 最大的 5 个会话:');
big.sort((a, b) => b[0] - a[0]).slice(0, 5).forEach(([t, fr, sz, p]) => console.log(`  文本 ${t}B / ${fr} 帧 / 文件 ${sz}B  ${p}`));
console.log(`--- 含 "instruction-hint" 的文件: ${hits.length}`);
hits.forEach(([n, p]) => console.log(`  ${n} 处  ${p}`));
console.log('--- 出现过的 kind（前 25）:');
[...kinds.entries()].sort((a, b) => b[1] - a[1]).slice(0, 25).forEach(([k, v]) => console.log(`  ${k}  x${v}`));
