// [V3.53] DSH 会话文件是「头帧 + 逐个事件帧」的**串联多帧** zstd。
// Node 的 zstdDecompressSync / createZstdDecompress **只吃第一帧**（实测 182 个文件只解出 45KB），
// 所以这里按 zstd 帧结构自己切帧，再逐帧解压 —— 这是"读得全"和"将来能安全回写"的前提。
import zlib from 'node:zlib';

const MAGIC = Buffer.from([0x28, 0xb5, 0x2f, 0xfd]);

/** 解析一帧的长度（不依赖解压）。返回 {size, desc} 或 null。 */
export function frameSize(buf, off) {
  if (off + 4 > buf.length) return null;
  if (!buf.subarray(off, off + 4).equals(MAGIC)) return null;
  let p = off + 4;
  if (p >= buf.length) return null;
  const desc = buf[p++];
  const fcsFlag = desc >> 6;
  const singleSegment = (desc >> 5) & 1;
  const checksum = (desc >> 2) & 1;
  const dictFlag = desc & 3;
  if (singleSegment === 0) p += 1;              // Window_Descriptor
  p += [0, 1, 2, 4][dictFlag];                  // Dictionary_ID
  let fcsLen;
  if (fcsFlag === 0) fcsLen = singleSegment ? 1 : 0;
  else fcsLen = [0, 2, 4, 8][fcsFlag];
  p += fcsLen;
  if (p > buf.length) return null;
  // blocks
  for (;;) {
    if (p + 3 > buf.length) return null;
    const h = buf[p] | (buf[p + 1] << 8) | (buf[p + 2] << 16);
    const last = h & 1;
    const type = (h >> 1) & 3;
    const size = h >> 3;
    p += 3;
    if (type === 0) return null;                 // Reserved
    if (type === 1) p += 1;                      // RLE: 1 byte
    else p += size;                              // Raw / Compressed: size bytes
    if (p > buf.length) return null;
    if (last) break;
  }
  if (checksum) p += 4;
  return { size: p - off, desc };
}

/** 把整个文件切成帧边界列表。 */
export function splitFrames(buf) {
  const out = [];
  let off = 0;
  while (off < buf.length) {
    const f = frameSize(buf, off);
    if (!f) break;
    out.push({ start: off, size: f.size });
    off += f.size;
  }
  return out;
}

/** 逐帧解压并拼起来（返回 utf8 文本 + 帧数统计）。 */
export function decodeAllFrames(buf) {
  const fr = splitFrames(buf);
  const chunks = [];
  let bad = 0;
  for (const f of fr) {
    try {
      chunks.push(zlib.zstdDecompressSync(buf.subarray(f.start, f.start + f.size)));
    } catch { bad++; }
  }
  return { text: Buffer.concat(chunks).toString('utf8'), frames: fr.length, bad };
}
