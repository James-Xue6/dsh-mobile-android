// [V3.53 · 只读扫描] 找出"读不出来"的老会话：session.v2.jsonl.zstd 里 source.kind = "instruction-hint"
// 的越界记录（DSH 本体 v2→v3 迁移的 SOURCE_KINDS 白名单没这一项 ⇒ 整条会话拒绝迁移）。
// 只读：不改、不写、不删任何文件。
//
// ⚠️ 必须用**流式**解压：这些文件是「头帧 + 逐个事件帧」的**串联多帧**布局，
//    而 zstdDecompressSync(buf) 只吃第一帧（实测每个文件只解出 ~210B = 头帧）⇒ 会得到"0 命中"的**假阴性**。
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const ROOT = 'C:/Users/Administrator/.dsh/sessions';

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.zstd')) out.push(p);
  }
  return out;
}

/** 流式解压：Node 的 ZstdDecompress 会依次处理串联的多个帧。 */
function decodeAll(buf) {
  return new Promise((resolve) => {
    const z = zlib.createZstdDecompress();
    const chunks = [];
    z.on('data', (c) => chunks.push(c));
    z.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    z.on('error', () => resolve(chunks.length ? Buffer.concat(chunks).toString('utf8') : ''));
    z.end(buf);
  });
}

const files = walk(ROOT, []);
const v2 = files.filter((f) => f.includes('v2'));
let hitFiles = 0, hitRecords = 0, lens = [];
for (const f of v2) {
  const t = await decodeAll(fs.readFileSync(f));
  lens.push(t.length);
  const n = (t.match(/"instruction-hint"/g) || []).length;
  if (n > 0) {
    hitFiles++;
    hitRecords += n;
    console.log(`  [踩雷] ${n} 处  ${f.replace(ROOT + '/', '')}`);
  }
}
console.log('---');
console.log(`v2 文件数: ${v2.length} / 全部 .zstd: ${files.length}`);
console.log(`解出内容长度: 最小 ${Math.min(...lens)} / 最大 ${Math.max(...lens)} / 合计 ${lens.reduce((a, b) => a + b, 0)}`);
console.log(`含 "instruction-hint" 的文件: ${hitFiles}，出现次数: ${hitRecords}`);
