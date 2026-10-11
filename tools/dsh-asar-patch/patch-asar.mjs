// 外科式给 app.asar 打补丁：把 "instruction-hint" 加进每一处 SOURCE_KINDS 白名单。
// 不改动其它文件、不动 app.asar.unpacked；重算被改文件的 integrity。
// 用法：node patch-asar.mjs [--apply]
//   默认只写 app.asar.patched（不动原文件）；--apply 时才备份并替换。
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const ASAR = 'D:\\AI\\DSH\\resources\\app.asar';
const APPLY = process.argv.includes('--apply');
const OUT = ASAR + '.patched';
const BLOCK = 4194304;

const buf = fs.readFileSync(ASAR);
const headerSize = buf.readUInt32LE(4);
const jsonSize = buf.readUInt32LE(12);
const json = buf.readJSON ? null : null;
const jsonStr = buf.subarray(16, 16 + jsonSize).toString('utf8');
const header = JSON.parse(jsonStr);
const dataStart = 8 + headerSize;
console.log('archive', buf.length, 'headerSize', headerSize, 'jsonSize', jsonSize, 'dataStart', dataStart);

// 收集所有叶子（文件名 -> 节点）
const leaves = [];
const walk = (node, prefix) => {
  if (node.files) { for (const k of Object.keys(node.files)) walk(node.files[k], prefix ? prefix + '/' + k : k); return; }
  leaves.push({ path: prefix, node });
};
walk(header, '');
console.log('叶子条目', leaves.length, ' 其中 unpacked', leaves.filter((l) => l.node.unpacked).length);

const readEntry = (l) => {
  const size = Number(l.node.size);
  if (l.node.unpacked) {
    // 解包文件在 app.asar.unpacked 下
    return fs.readFileSync(path.join(ASAR + '.unpacked', l.path.replace(/\//g, path.sep)));
  }
  const b = Buffer.alloc(size);
  buf.copy(b, 0, dataStart + Number(l.node.offset), dataStart + Number(l.node.offset) + size);
  return b;
};

// 找含白名单定义的文件
const NEEDLE = 'const SOURCE_KINDS = new Set([';
const targets = [];
for (const l of leaves) {
  if (l.node.unpacked) continue;
  const b = readEntry(l);
  if (b.includes(NEEDLE)) {
    targets.push({ ...l, content: b });
  }
}
console.log('需要打补丁的文件:', targets.map((t) => t.path + ' (size=' + t.content.length + ')'));
if (targets.length === 0) { console.error('没找到 SOURCE_KINDS 定义，退出'); process.exit(1); }

// 打补丁：在 new Set([ 之后插入一项
const insert = Buffer.from('\n\t"instruction-hint",', 'utf8');
const patches = new Map();   // path -> {oldLen, newContent}
for (const t of targets) {
  const s = t.content.toString('utf8');
  const idx = s.indexOf(NEEDLE);
  const at = idx + NEEDLE.length;                 // 紧接着 '[' 的位置（原为 '\n'）
  const before = Buffer.from(s.slice(0, at), 'utf8');
  const after = Buffer.from(s.slice(at), 'utf8');
  const newContent = Buffer.concat([before, insert, after]);
  patches.set(t.path, { len: t.content.length, newContent });
  console.log('  补丁', t.path, t.content.length, '->', newContent.length,
    ' (+' + (newContent.length - t.content.length) + ')');
}

// 重算 integrity（SHA256，按 4MB 分块）
const integrityOf = (b) => {
  const blocks = [];
  for (let i = 0; i < b.length; i += BLOCK) {
    blocks.push(crypto.createHash('sha256').update(b.subarray(i, Math.min(i + BLOCK, b.length))).digest('hex'));
  }
  return { algorithm: 'SHA256', hash: crypto.createHash('sha256').update(b).digest('hex'), blockSize: BLOCK, blocks };
};

// 组装新档案：先算出每个文件的最终内容与偏移
let offset = 0;
const parts = [];
const newHeader = JSON.parse(JSON.stringify(header));
const setNode = (p, fn) => {
  const seg = p.split('/');
  let node = newHeader;
  for (let i = 0; i < seg.length - 1; i++) node = node.files[seg[i]];
  fn(node.files[seg[seg.length - 1]]);
};
for (const l of leaves) {
  if (l.node.unpacked) continue;               // 解包文件不占档案数据区
  const patched = patches.get(l.path);
  const content = patched ? patched.newContent : readEntry(l);
  setNode(l.path, (n) => {
    n.size = content.length;
    n.offset = String(offset);
    n.integrity = integrityOf(content);
  });
  parts.push(content);
  offset += content.length;
}
const newJson = JSON.stringify(newHeader);
const jsonBuf = Buffer.from(newJson, 'utf8');
const padded = Buffer.alloc(Math.ceil(jsonBuf.length / 4) * 4, 0x20);   // 空格补齐
jsonBuf.copy(padded);
const head = Buffer.alloc(16);
head.writeUInt32LE(4, 0);
head.writeUInt32LE(padded.length + 8, 4);
head.writeUInt32LE(padded.length + 4, 8);
head.writeUInt32LE(jsonBuf.length, 12);
const out = Buffer.concat([head, padded, ...parts]);
fs.writeFileSync(OUT, out);
console.log('\n已写出', OUT, out.length, 'bytes (原', buf.length, ')');

// ---- 校验：用同一套解析重新读回，逐条比对（除补丁文件外必须完全一致）----
const verify = fs.readFileSync(OUT);
const vJsonSize = verify.readUInt32LE(12);
const vJson = JSON.parse(verify.subarray(16, 16 + vJsonSize).toString('utf8'));
const vDataStart = 8 + verify.readUInt32LE(4);
const vLeaves = [];
(function vwalk(n, p) { if (n.files) { for (const k of Object.keys(n.files)) vwalk(n.files[k], p ? p + '/' + k : k); } else vLeaves.push({ path: p, node: n }); })(vJson, '');
let same = 0, diff = 0, bad = [];
const origByPath = new Map(leaves.map((l) => [l.path, l]));
for (const l of vLeaves) {
  const o = origByPath.get(l.path);
  if (!o) { bad.push('多出的条目 ' + l.path); continue; }
  if (l.node.size !== o.node.size) { diff++; continue; }
  if (l.node.unpacked) { same++; continue; }
  const a = Buffer.alloc(Number(l.node.size));
  verify.copy(a, 0, vDataStart + Number(l.node.offset), vDataStart + Number(l.node.offset) + Number(l.node.size));
  const b = readEntry(o);
  if (a.equals(b)) same++; else bad.push('内容不一致 ' + l.path);
}
console.log('校验：条目', vLeaves.length, ' 一致', same, ' 大小变化(补丁)', diff, ' 异常', bad.length);
for (const x of bad.slice(0, 10)) console.log('   !', x);
for (const t of targets) {
  const l = vLeaves.find((x) => x.path === t.path);
  const s = verify.subarray(vDataStart + Number(l.node.offset), vDataStart + Number(l.node.offset) + Number(l.node.size)).toString('utf8');
  console.log('补丁文件', t.path, '含 instruction-hint:', s.includes('"instruction-hint"'),
    ' integrity.hash 前 12:', l.node.integrity.hash.slice(0, 12),
    ' 实际前 12:', crypto.createHash('sha256').update(Buffer.from(s, 'utf8')).digest('hex').slice(0, 12));
}

if (APPLY) {
  const bak = ASAR + '.bak-' + new Date().toISOString().replace(/[:.]/g, '').slice(0, 15);
  console.log('\n--- APPLY ---');
  try {
    fs.renameSync(ASAR, bak);
    fs.renameSync(OUT, ASAR);
    console.log('已替换。备份：', bak);
  } catch (e) {
    console.log('替换失败（应用可能占用中）：', e.message);
    console.log('→ 已保留', OUT, '；请关闭 DSH 后手动替换，或稍后重跑本脚本 --apply');
  }
}
