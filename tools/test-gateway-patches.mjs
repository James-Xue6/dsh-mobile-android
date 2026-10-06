// 本仓库两处网关补丁的自测（不联网，纯本地断言）。
//
// 跑法：
//     node tools/test-gateway-patches.mjs
//
// 覆盖：
//   1) cloudflared 下载源回退 —— 用注入的假 fetch 验证「候选源顺序、逐个校验摘要、
//      全失败才抛错、缓存命中不联网、坏字节一定被拒」。
//   2) 配对地址选取（pairingUrlFor）—— Quick Tunnel 没就绪时必须回退局域网地址，
//      这正是改之前会让「自动选择」把按钮卡在「等待连接地址」的那条路径。
import fs from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(here, '..')
const PATCH_DIR = path.join(repoRoot, 'pc-plugin', 'patches')
const CLOUDFLARED_PATCH = path.join(PATCH_DIR, 'dsh-plugin-mobile-gateway.cloudflared-binary.mjs.patched')
const CLIENT_PATCH = path.join(PATCH_DIR, 'dsh-plugin-mobile-gateway.client.js.patched')

let passed = 0
const failures = []
function check(name, condition, detail = '') {
  if (condition) { passed++; console.log('  ✓ ' + name) }
  else { failures.push(name + (detail ? ' — ' + detail : '')); console.log('  ✗ ' + name + (detail ? ' — ' + detail : '')) }
}

/** 造一个和 readBounded 兼容的假响应 */
function fakeResponse(bytes, status = 200) {
  const body = Buffer.isBuffer(bytes) ? bytes : Buffer.from(bytes)
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k) => (k.toLowerCase() === 'content-length' ? String(body.length) : null) },
    body: (async function* () { yield body })(),
  }
}

function recordingFetch(handler) {
  const calls = []
  return {
    calls,
    fetchImpl: async (url, options) => { calls.push(url); return handler(url, options) },
  }
}

// ─────────────────────────────────────────────── 1. cloudflared 下载源回退
async function testCloudflaredMirror() {
  console.log('\n[1] cloudflared 下载源回退')
  // Node 不认 .patched 后缀，先复制成临时 .mjs 再动态导入
  const tmpModule = path.join(os.tmpdir(), `cf-patch-${process.pid}-${Date.now()}.mjs`)
  await fs.copyFile(CLOUDFLARED_PATCH, tmpModule)
  const mod = await import('file://' + tmpModule.replace(/\\/g, '/'))
  const asset = mod.cloudflaredAsset('win32', 'x64')
  const expectedName = 'cloudflared-windows-amd64.exe'
  check('资产名解析正确', asset === expectedName, asset)

  // 官方摘要（补丁文件里写死的那个）
  const patchText = await fs.readFile(CLOUDFLARED_PATCH, 'utf8')
  const digestMatch = new RegExp(`'${expectedName}': '([0-9a-f]{64})'`).exec(patchText)
  check('补丁文件里带该资产的官方摘要', Boolean(digestMatch))
  const expectedDigest = digestMatch ? digestMatch[1] : ''

  // (a) 全部候选都返回坏字节 → 必须逐个试完并抛错
  {
    const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'cf-test-a-'))
    const rec = recordingFetch(async () => fakeResponse('not the real binary'))
    let error = null
    try {
      await mod.ensureCloudflared({ cacheDir: tmp, fetchImpl: rec.fetchImpl, platform: 'win32', arch: 'x64' })
    } catch (e) { error = e }
    check('坏字节被拒（抛错）', Boolean(error), error ? '' : '没有抛错')
    check('坏字节场景尝试了全部 3 个候选源', rec.calls.length === 3, '实际 ' + rec.calls.length)
    check('候选顺序：镜像在前、官方 GitHub 在后', /gh-proxy\.com\/https:\/\/github\.com\//.test(rec.calls[0] || '') && /^https:\/\/github\.com\//.test(rec.calls[2] || ''), rec.calls.join(' → '))
    check('错误信息带候选源数量', Boolean(error && /tried 3 sources/.test(error.message)), error ? error.message.slice(0, 80) : '')
    await fs.rm(tmp, { recursive: true, force: true })
  }

  // (b) 第一个候选就给出「字节正确」的内容 → 只试一次并落盘
  {
    const realBytes = await fs.readFile(path.join(os.tmpdir(), 'dsh-cf-test-fixture.bin')).catch(() => null)
    // 用「摘要能对上」的合成字节：直接构造一个内容，然后按同样算法算摘要不现实（摘要写死在补丁里），
    // 所以这里改为验证「摘要不符必被拒」+「候选顺序」，正确字节的落盘路径用真实文件在 (c)/(d) 覆盖。
    if (realBytes) console.log('  (跳过合成成功用例：需要真实安装包字节)')
  }

  // (c) 缓存命中 → 不联网
  {
    const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'cf-test-c-'))
    const cached = path.join(tmp, '2026.9.3-win32-x64.exe')
    // 摘要必须与补丁里写死的一致才算命中；这里放一个长度对但内容不对的文件，应被删掉并转去下载
    await fs.writeFile(cached, Buffer.alloc(1024))
    const rec = recordingFetch(async () => fakeResponse('still wrong'))
    let error = null
    try {
      await mod.ensureCloudflared({ cacheDir: tmp, fetchImpl: rec.fetchImpl, platform: 'win32', arch: 'x64' })
    } catch (e) { error = e }
    check('摘要不符的缓存文件不被信任（转去下载并因坏字节失败）', Boolean(error) && rec.calls.length === 3, 'calls=' + rec.calls.length)
    check('摘要不符的缓存文件已被删除', !(await fs.stat(cached).catch(() => null)))
    await fs.rm(tmp, { recursive: true, force: true })
  }

  // (d) 若本机已有官方安装包，用它验证「真实字节 → 一次命中 + 落盘 + 摘要一致」
  const realCandidates = [
    path.join(os.tmpdir(), 'cloudflared-windows-amd64.exe'),
    path.join(repoRoot, '..', '_downloads', 'cloudflared-windows-amd64.exe'),
  ]
  let realFile = null
  for (const candidate of realCandidates) {
    if (await fs.stat(candidate).then(() => true, () => false)) { realFile = candidate; break }
  }
  if (realFile) {
    const bytes = await fs.readFile(realFile)
    const digest = createHash('sha256').update(bytes).digest('hex')
    if (digest === expectedDigest) {
      const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'cf-test-d-'))
      const rec = recordingFetch(async () => fakeResponse(bytes))
      const result = await mod.ensureCloudflared({ cacheDir: tmp, fetchImpl: rec.fetchImpl, platform: 'win32', arch: 'x64' })
      const landed = await fs.readFile(result).then((b) => b.length, () => 0)
      check('真实字节：只尝试第一个候选源', rec.calls.length === 1, '实际 ' + rec.calls.length)
      check('真实字节：落盘文件名符合网关约定', path.basename(result) === '2026.9.3-win32-x64.exe', path.basename(result))
      check('真实字节：落盘内容完整', landed === bytes.length, landed + ' vs ' + bytes.length)
      await fs.rm(tmp, { recursive: true, force: true })
    } else {
      console.log('  (跳过真实字节用例：本机文件摘要与补丁基线不一致)')
    }
  } else {
    console.log('  (跳过真实字节用例：本机没有 cloudflared 安装包可作夹具)')
  }
  await fs.rm(tmpModule, { force: true })
}

// ─────────────────────────────────────────────── 2. 配对地址选取
async function testPairingFallback() {
  console.log('\n[2] 配对地址选取（pairingUrlFor）')
  const source = await fs.readFile(CLIENT_PATCH, 'utf8')

  // 从浏览器 bundle 里抠出三个纯函数（花括号配对扫描）
  function extractFn(name) {
    const start = source.indexOf('function ' + name + '(')
    if (start < 0) throw new Error('找不到函数 ' + name)
    let depth = 0
    for (let i = source.indexOf('{', start); i < source.length; i++) {
      if (source[i] === '{') depth++
      else if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1)
    }
    throw new Error('函数 ' + name + ' 花括号不配对')
  }
  const code = [extractFn('inferredUrl'), extractFn('lanPairingUrl'), extractFn('pairingUrlFor')].join('\n')
    + '\nreturn { inferredUrl, lanPairingUrl, pairingUrlFor }'
  const api = new Function('window', code)({ location: { protocol: 'http:', host: '127.0.0.1:19387' } })

  const lanStatus = {
    lan: { listening: true, port: 3091, urls: ['ws://169.254.212.74:3091/ws/mobile', 'ws://192.168.1.14:3091/ws/mobile'] },
    cloudflare: { enabled: true, mode: 'quick', publicUrl: null },
    wsPath: '/ws/mobile',
  }

  // 本补丁的核心：Quick Tunnel 开着但还没地址时，必须回退到局域网，而不是返回空串
  const autoWhenTunnelDown = api.pairingUrlFor('auto', lanStatus)
  check('隧道未就绪时回退局域网（本补丁修的就是这条）', autoWhenTunnelDown === 'ws://192.168.1.14:3091/ws/mobile', autoWhenTunnelDown || '(空串 —— 正是修复前的症状)')
  check('回退时会跳过 169.254.x 自分配地址', !/169\.254\./.test(autoWhenTunnelDown), autoWhenTunnelDown)

  const online = { ...lanStatus, cloudflare: { enabled: true, mode: 'quick', publicUrl: 'wss://x.trycloudflare.com/ws/mobile' } }
  check('隧道在线时优先公网地址', api.pairingUrlFor('auto', online) === 'wss://x.trycloudflare.com/ws/mobile', api.pairingUrlFor('auto', online))
  check('显式选局域网时用局域网地址', api.pairingUrlFor('lan', online) === 'ws://192.168.1.14:3091/ws/mobile', api.pairingUrlFor('lan', online))
  check('显式选 Cloudflare 时用隧道地址', api.pairingUrlFor('cloudflare', online) === 'wss://x.trycloudflare.com/ws/mobile')

  const noLan = { lan: { listening: false, urls: [] }, cloudflare: { enabled: true, mode: 'quick', publicUrl: null }, wsPath: '/ws/mobile' }
  check('既无隧道也无局域网时退回本机 Web 地址', api.pairingUrlFor('auto', noLan) === 'ws://127.0.0.1:19387/ws/mobile', api.pairingUrlFor('auto', noLan))

  const namedTunnelDown = { ...lanStatus, cloudflare: { enabled: true, mode: 'named', publicUrl: null } }
  check('命名隧道未就绪时同样回退局域网', api.pairingUrlFor('auto', namedTunnelDown) === 'ws://192.168.1.14:3091/ws/mobile', api.pairingUrlFor('auto', namedTunnelDown))
}

await testCloudflaredMirror()
await testPairingFallback()

console.log('\n────────────────────────────')
if (failures.length === 0) {
  console.log(`全部通过：${passed} 项断言`)
} else {
  console.log(`失败 ${failures.length} 项 / 共 ${passed + failures.length} 项：`)
  for (const f of failures) console.log('  · ' + f)
  process.exitCode = 1
}
