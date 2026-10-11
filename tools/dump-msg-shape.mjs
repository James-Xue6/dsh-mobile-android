// [V3.61 · 只读] 解开一个 DSH v4 会话文件（从第 2 个 zstd 帧解到文件尾，绕开切帧器的缺陷），
// 打印事件类型分布 + 一条 user/message 的完整结构 —— 用于对比 App 的 applyEvent 认的字段。
// 用法: node tools/dump-msg-shape.mjs <session.v4.jsonl.zstd>
import fs from 'node:fs';
import zlib from 'node:zlib';

const file = process.argv[2];
const b = fs.readFileSync(file);
const MAG = [0x28, 0xb5, 0x2f, 0xfd];
const offs = [];
for (let i = 0; i < b.length - 3; i++) {
  if (b[i] === MAG[0] && b[i + 1] === MAG[1] && b[i + 2] === MAG[2] && b[i + 3] === MAG[3]) offs.push(i);
}
console.log(`文件 ${b.length}B，magic 帧头 ${offs.length} 个，位置前 5：${offs.slice(0, 5).join(',')}`);

let text = '';
if (offs.length > 1) {
  try { text = zlib.zstdDecompressSync(b.subarray(offs[1])).toString('utf8'); }
  catch (e) { console.log('从第2帧解压失败：', e.message); }
}
if (!text) {
  try { text = zlib.zstdDecompressSync(b).toString('utf8'); } catch (e) { console.log('单帧解压也失败：', e.message); }
}
const lines = text.split('\n').filter((l) => l.trim() !== '');
console.log(`解出 ${text.length}B / ${lines.length} 行`);

const hist = new Map();
let sampleUser = null;
let sampleAssistant = null;
for (const l of lines) {
  let o;
  try { o = JSON.parse(l); } catch { continue; }
  const t = (o.event && o.event.type) || o.type;
  hist.set(t, (hist.get(t) || 0) + 1);
  if (t === 'user/message' && !sampleUser) sampleUser = o;
  if (t === 'assistant/message' && !sampleAssistant) sampleAssistant = o;
}
console.log('事件类型分布（前 20）：');
[...hist.entries()].sort((a, b2) => b2[1] - a[1]).slice(0, 20)
  .forEach(([k, v]) => console.log(`    ${k}  x${v}`));
if (sampleUser) {
  console.log('--- 一条 user/message（截 700 字）---');
  console.log(JSON.stringify(sampleUser).slice(0, 700));
}
if (sampleAssistant) {
  console.log('--- 一条 assistant/message（截 700 字）---');
  console.log(JSON.stringify(sampleAssistant).slice(0, 700));
}
