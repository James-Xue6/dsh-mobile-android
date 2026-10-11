// 解开 DSH 的 v2 会话日志，统计 source.kind 分布，找出"不在白名单里"的那一条。
// 用法：node scan.mjs
import fs from 'node:fs';
import path from 'node:path';
import { decompress } from 'fzstd';

const root = process.argv[2] || 'C:\\Users\\Administrator\\.dsh\\sessions\\--F-AI-~5DE5~4F5C~53F0~8BBE~8BA1--';

const dirs = fs.readdirSync(root).filter((d) =>
  fs.existsSync(path.join(root, d, 'session.v2.jsonl.zstd')));

const kinds = new Map();
const badSources = [];
let totalLines = 0;
let decompressed = 0;

for (const d of dirs) {
  const f = path.join(root, d, 'session.v2.jsonl.zstd');
  let text;
  try {
    text = Buffer.from(decompress(new Uint8Array(fs.readFileSync(f)))).toString('utf8');
    decompressed++;
  } catch (e) {
    console.log('DECOMPRESS FAIL', d, e.message);
    continue;
  }
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    totalLines++;
    let o;
    try { o = JSON.parse(line); } catch { continue; }
    const k = o && o.source ? o.source.kind : undefined;
    if (typeof k === 'string') kinds.set(k, (kinds.get(k) || 0) + 1);
    if (o && o.source && typeof k !== 'string') {
      badSources.push([d, JSON.stringify(o.source).slice(0, 300)]);
    }
  }
}

console.log('会话数(v2):', dirs.length, ' 成功解压:', decompressed, ' 总行数:', totalLines);
console.log('\n=== source.kind 分布 ===');
for (const [k, n] of [...kinds.entries()].sort((a, b) => b[1] - a[1])) {
  console.log(String(n).padStart(7), k);
}
console.log('\n=== source 无 kind 的样例（最多 5 条）===');
for (const [d, s] of badSources.slice(0, 5)) console.log(d, s);
if (badSources.length) console.log('source 无 kind 共', badSources.length, '条');
