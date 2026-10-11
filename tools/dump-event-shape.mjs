// [V3.61 · 只读] 打印某个会话文件里**记录/事件的实际结构**，用于对比 App 的 applyEvent 认的命名。
// 用法: node tools/dump-event-shape.mjs <session.jsonl.zstd> [条数]
import fs from 'node:fs';
import { decodeAllFrames } from './zstd-frames.mjs';

const file = process.argv[2];
const limit = Number(process.argv[3] ?? 4);
const { text, frames } = decodeAllFrames(fs.readFileSync(file));
console.log(`文件=${file}\n帧=${frames} 文本=${text.length}B`);
const lines = text.split('\n').filter((l) => l.trim() !== '');
console.log(`行数=${lines.length}`);
let shown = 0;
for (const line of lines) {
  let o;
  try { o = JSON.parse(line); } catch { continue; }
  const ev = o.event ?? o;
  const t = String(o.type ?? '(no type)');
  if (o.type === 'session') { console.log(`  [header] ${JSON.stringify(o).slice(0, 160)}`); continue; }
  console.log(`--- 记录 type=${t}`);
  console.log(`    event.type = ${JSON.stringify(ev.type)}`);
  console.log(`    event keys = ${Object.keys(ev).join(',')}`);
  console.log(`    event 前 320 字 = ${JSON.stringify(ev).slice(0, 320)}`);
  if (++shown >= limit) break;
}
