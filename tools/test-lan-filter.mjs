// 自测：内网地址过滤 + 配对串自检。
// 直接跑**真源码**里那两段函数（index.js 的 isUsableLanAddress / client.js 的
// pairPayloadProblem），不另抄一份规则，改了源码这里立刻能发现。
//
// 用法：node tools\test-lan-filter.mjs
//
// 注意：本文件是会被 git 跟踪的文件，**不要**把本机真实的局域网 IP、隧道域名、
// 主机名写进来 —— 全部用合成地址（10.x / 192.168.x / 172.30.x 这类示例）。
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const repo = path.join(here, '..')
let fail = 0
const check = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) fail += 1
  console.log((ok ? '  ok   ' : '  FAIL ') + name + '  got=' + JSON.stringify(got) + (ok ? '' : ' want=' + JSON.stringify(want)))
}

// ---------------------------------------------------------------- 1. host 侧 index.js
const idx = await import(pathToFileURL(path.join(repo, 'pc-plugin/dsh-mobile-access/index.js')).href)

console.log('=== index.js: isUsableLanAddress ===')
check('192.168.x 可用', idx.isUsableLanAddress('192.168.1.20'), true)
check('10.x 可用', idx.isUsableLanAddress('10.0.0.5'), true)
check('172.30.x（虚拟网卡）排除', idx.isUsableLanAddress('172.30.1.1'), false)
check('172.16.0.1 排除', idx.isUsableLanAddress('172.16.0.1'), false)
check('172.31.255.254 排除', idx.isUsableLanAddress('172.31.255.254'), false)
check('172.32.0.1 保留（不在 172.16/12 里）', idx.isUsableLanAddress('172.32.0.1'), true)
check('169.254.x（APIPA）排除', idx.isUsableLanAddress('169.254.1.1'), false)
check('127.0.0.1 排除', idx.isUsableLanAddress('127.0.0.1'), false)
check('100.64.x（CGNAT）排除', idx.isUsableLanAddress('100.64.0.1'), false)
check('乱码字符串 排除', idx.isUsableLanAddress('not-an-ip'), false)
check('越界 192.168.1.299 排除', idx.isUsableLanAddress('192.168.1.299'), false)

console.log('=== index.js: lanIPv4() 本机真实结果（只断言规则，不写死地址）===')
const lans = idx.lanIPv4()
console.log('  -> 探测到 ' + lans.length + ' 个可用内网地址：' +
  (lans.length ? lans.map((ip) => ip.replace(/\.\d+$/, '.x')).join(', ') : '（无）'))
check('结果全部通过可用性判据', lans.every((ip) => idx.isUsableLanAddress(ip)), true)
check('结果里没有 172.16-31.x / 169.254.x / 127.x',
  lans.every((ip) => !/^(172\.(1[6-9]|2\d|3[01])\.|169\.254\.|127\.)/.test(ip)), true)
check('排序：192.168.x 排在 10.x / 其它前面',
  lans.every((ip, i) => i === 0 || idx.lanAddressRank(lans[i - 1]) <= idx.lanAddressRank(ip)), true)

console.log('=== index.js: publicUrls() ===')
const pub = idx.publicUrls()
console.log('  version=' + pub.version + '  ref=' + pub.ref)
check('公网镜像不再用 v<version> 拼 tag（默认跟 main）', pub.cdn.includes('@main/'), true)
check('github 直链同样跟 ref 不跟 tag', pub.github.includes('/raw/main/'), true)

// ---------------------------------------------------------------- 2. 面板 client.js 的校验函数
const clientSrc = fs.readFileSync(path.join(repo, 'pc-plugin/dsh-mobile-access/client.js'), 'utf8')

/** 从源码里精确抠出一个具名函数的完整文本（跳过字符串/模板串/注释里的花括号）。 */
function extractFn(src, name) {
  const start = src.indexOf('function ' + name + '(')
  if (start < 0) throw new Error('找不到函数 ' + name)
  let depth = 0, i = src.indexOf('{', start), inS = null, inLine = false, inBlock = false
  for (; i < src.length; i++) {
    const c = src[i], n = src[i + 1]
    if (inLine) { if (c === '\n') inLine = false; continue }
    if (inBlock) { if (c === '*' && n === '/') { inBlock = false; i++ } continue }
    if (inS) {
      if (c === '\\') { i++; continue }
      if (c === inS) inS = null
      continue
    }
    if (c === '/' && n === '/') { inLine = true; i++; continue }
    if (c === '/' && n === '*') { inBlock = true; i++; continue }
    if (c === '"' || c === "'" || c === '`') { inS = c; continue }
    if (c === '{') depth += 1
    else if (c === '}') { depth -= 1; if (depth === 0) return src.slice(start, i + 1) }
  }
  throw new Error('括号不配对 ' + name)
}
const pairPayloadProblem = new Function(extractFn(clientSrc, 'pairPayloadProblem') + '; return pairPayloadProblem')()

console.log('=== client.js: pairPayloadProblem ===')
const real = Buffer.from(JSON.stringify({
  version: 2,
  publicUrl: 'wss://tunnel.example.com/ws/mobile',
  pairingCode: 'abcdefghijklmnopqrstuvwxyz1234567890abcdefghijkl',
  gatewayName: '示例设备（中文名，验证 UTF-8 还原）',
  expiresAt: 1790928680044,
  endpoints: ['ws://10.0.0.5:3091/ws/mobile', 'wss://tunnel.example.com/ws/mobile'],
}), 'utf8').toString('base64url')
console.log('  合成样本长度 = ' + real.length + '（真实配对串约 550）')
check('合法配对串通过', pairPayloadProblem(real), '')
check('纯 URL 被拒（「画错了的码」就是这种内容）',
  pairPayloadProblem('wss://tunnel.example.com/ws/mobile') !== '', true)
check('空串被拒', pairPayloadProblem('') !== '', true)
check('null 被拒', pairPayloadProblem(null) !== '', true)
check('太短被拒', pairPayloadProblem('aGVsbG8') !== '', true)
check('非 JSON 的 Base64 被拒', pairPayloadProblem('A'.repeat(200)) !== '', true)
check('version 不是 2 被拒',
  pairPayloadProblem(Buffer.from(JSON.stringify({ version: 1, publicUrl: 'ws://a/1', pairingCode: 'y', pad: 'z'.repeat(80) })).toString('base64url')) !== '', true)
check('缺 pairingCode 被拒',
  pairPayloadProblem(Buffer.from(JSON.stringify({ version: 2, publicUrl: 'ws://a/1', pad: 'z'.repeat(80) })).toString('base64url')) !== '', true)

console.log(fail ? '\n❌ ' + fail + ' 项未通过' : '\n✅ 全部通过')
process.exit(fail ? 1 : 0)
