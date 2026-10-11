// 探 app.asar 头部结构（不写任何东西）
import fs from 'node:fs';
const ASAR = 'D:\\AI\\DSH\\resources\\app.asar';
const fd = fs.openSync(ASAR, 'r');
const head = Buffer.alloc(16);
fs.readSync(fd, head, 0, 16, 0);
console.log('uint32[0..4):', head.readUInt32LE(0));
console.log('uint32[4..8):', head.readUInt32LE(4));
console.log('uint32[8..12):', head.readUInt32LE(8));
console.log('uint32[12..16):', head.readUInt32LE(12));
const jsonSize = head.readUInt32LE(12);
const jsonBuf = Buffer.alloc(jsonSize);
fs.readSync(fd, jsonBuf, 0, jsonSize, 16);
const json = jsonBuf.toString('utf8');
console.log('JSON 前缀:', json.slice(0, 60));
console.log('JSON 末尾:', json.slice(-40));
const header = JSON.parse(json);
const dataStart = 16 + head.readUInt32LE(4);   // 16 + headerSize?
console.log('dataStart 推测:', dataStart, ' 文件头 12 字段(headerSize):', head.readUInt32LE(4));
// 找一个小文件验证 offset/size 的相对基准
const probe = (p) => {
  const parts = p.split('/');
  let node = header;
  for (const x of parts) node = node.files[x];
  return node;
};
const pj = probe('package.json');
console.log('package.json 条目:', pj);
// 用两种基准各解一次，看哪个是合法 JSON
for (const [label, base] of [['16+headerSize', 16 + head.readUInt32LE(4)], ['16+jsonSize', 16 + jsonSize]]) {
  try {
    const b = Buffer.alloc(Number(pj.size));
    fs.readSync(fd, b, 0, Number(pj.size), base + Number(pj.offset));
    const s = b.toString('utf8');
    JSON.parse(s);
    console.log(base === 16 + head.readUInt32LE(4) ? '基准1 命中' : '基准2 命中', '内容前缀:', s.slice(0, 40).replace(/\n/g, ' '));
  } catch (e) {
    console.log(label, '不成立:', e.message.slice(0, 60));
  }
}
// 统计 unpacked 文件数
let unpacked = 0, total = 0;
const walk = (node) => {
  if (node.files) { for (const k of Object.keys(node.files)) walk(node.files[k]); return; }
  total++;
  if (node.unpacked) unpacked++;
};
walk(header);
console.log('总条目:', total, ' unpacked:', unpacked);
fs.closeSync(fd);
