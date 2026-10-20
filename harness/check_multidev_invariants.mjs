#!/usr/bin/env node
/**
 * 多设备串台回归 · 不变量检查（源码级，不需要真机）
 *
 * 背景：进程里只有一个 GatewayClient，所以「连着 A 却在 UI 上操作 B」是这类 bug 的唯一成因。
 * 修复的中心纪律是三条不变量，本脚本把三条变成可机械重跑的红线检查：
 *
 *   I1  每一个真正发起连接的调用点（gw.connect(...) / connectWith(store, gw)）
 *       前面都必须先 bindConnectionToActiveDevice()（把「这次连的是哪台设备」记下来）。
 *   I2  「能不能进入 / 在线状态 / 实际线路」这些判断必须按设备算（isOnlineFor），
 *       不许再用裸 isOnline()。
 *   I3  会话列表帧到达时若没有已绑定设备（换设备空窗），必须丢弃。
 *
 * 用法：node harness/check_multidev_invariants.mjs
 * 退出码 0 = 全部通过；1 = 有违例（打印行号）。
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
// 允许用第一个参数指定被检查的文件（自测检查器本身时用：拷一份改坏，再指着它跑）
const src = process.argv[2] || join(here, '..', 'src', 'com', 'dsh', 'mobile', 'MainActivity.java')
const lines = readFileSync(src, 'utf8').split(/\r?\n/)

const fail = []
const near = (i, text, span = 4) => {
  for (let k = Math.max(0, i - span); k <= i; k++) if (lines[k].includes(text)) return true
  return false
}

// ---- I1：连接调用点必须先绑定设备
// ⚠️ 必须包含 gw.pair()：扫码配对走的是**另一条**建连路径，漏了它的真机症状是
//    "扫完码配上了，卡片却还写离线"（socket 连的是新电脑，connectedDeviceId 还是旧值）。
const connectCall = /gw\.connect\(|gw\.pair\(|connectWith\(store,\s*gw\)/
lines.forEach((line, i) => {
  const code = line.split('//')[0]
  if (!connectCall.test(code)) return
  if (line.trim().startsWith('*')) return            // javadoc 里提到的
  if (!near(i, 'bindConnectionToActiveDevice()')) {
    fail.push(`I1 第 ${i + 1} 行：连接调用点前面没有 bindConnectionToActiveDevice() → ${line.trim()}`)
  }
})

// ---- I2：入口/在线/线路判断要按设备算
const mustUseOnlineFor = [
  [/boolean online = active != null && isOnline\(\)/, 'refreshDevices 的在线判据仍用裸 isOnline()'],
  [/if \(isOnline\(\)\) \{\s*$/, null],   // 占位（onOpenDevice 里已改成 isOnlineFor，这里不误报）
]
const refreshIdx = lines.findIndex(l => l.includes('refreshDevices() {') || l.includes('private void refreshDevices()'))
if (refreshIdx >= 0) {
  const body = lines.slice(refreshIdx, refreshIdx + 12).join('\n')
  if (!body.includes('isOnlineFor(')) fail.push('I2 refreshDevices()：在线状态没有按设备算（缺 isOnlineFor）')
}
const openIdx = lines.findIndex(l => l.includes('public void onOpenDevice('))
if (openIdx >= 0) {
  const body = lines.slice(openIdx, openIdx + 30).join('\n')
  if (!body.includes('isOnlineFor(')) fail.push('I2 onOpenDevice()：「能不能进入」没有按设备算（缺 isOnlineFor）')
  if (!body.includes('switchDevice(')) fail.push('I2 onOpenDevice()：换设备时没有 switchDevice()（会串台）')
}
// 注意：先出现的同名方法是 GlassDeviceHost 里的匿名转发实现，必须找**本类实现**（带 RoutePolicy.Pick p =）
const routeIdx = lines.findIndex(l => /^\s{4}public RoutePolicy\.Pick routeOf\(Store\.Device d\) \{/.test(l))
if (routeIdx >= 0) {
  const body = lines.slice(routeIdx, routeIdx + 14).join('\n')
  if (!body.includes('connectedDeviceId')) fail.push('I2 routeOf()：「实际在用线路」没有按连接所属设备判断')
}

// ---- I3：会话列表帧的空窗守卫
const sessIdx = lines.findIndex(l => l.includes('public void onSessions(JSONArray arr'))
if (sessIdx >= 0) {
  const body = lines.slice(sessIdx, sessIdx + 6).join('\n')
  if (!body.includes('connectedDeviceId')) fail.push('I3 onSessions()：没有「无绑定设备则丢弃」的守卫')
}

// ---- 顺带：clearDeviceScopedState 必须真的清关键账本
const clearIdx = lines.findIndex(l => l.includes('private void clearDeviceScopedState()'))
if (clearIdx >= 0) {
  const body = lines.slice(clearIdx, clearIdx + 60).join('\n')
  for (const key of ['sessions.clear()', 'items.clear()', 'currentSessionId = ""', 'queueBySession.clear()',
    'pendingInteractions.clear()', 'store.setLastSessionId("")']) {
    if (!body.includes(key)) fail.push(`clear 账本缺 ${key}`)
  }
}

// ---- I4 [2026-10-20 第二轮]：异步地址同步不许跨设备写
const wanIdx = lines.findIndex(l => l.includes('private void maybeRefreshWanUrl(String source, boolean manual)'))
if (wanIdx >= 0) {
  const body = lines.slice(wanIdx, wanIdx + 90).join('\n')
  if (!body.includes('ownerDeviceId')) fail.push('I4 maybeRefreshWanUrl()：没有 ownerDeviceId 守卫（异步回来时会把 A 的地址写进 B）')
  if (!body.includes('ownerDeviceId.equals(store.activeDeviceId())')) {
    fail.push('I4 maybeRefreshWanUrl()：没有"设备已切换就丢弃这次同步"的判断')
  }
}

// ---- I5：Compose 设备卡的线路判据必须走宿主 routeOf（默认那条带全局内网退避，会跨设备串味）
const screenPath = join(here, '..', 'src', 'com', 'dsh', 'mobile', 'glass', 'GlassDeviceScreen.kt')
try {
  const kt = readFileSync(screenPath, 'utf8')
  if (!kt.includes('routeOf = { host.routeOf(it) }')) {
    fail.push('I5 GlassDeviceScreen：DeviceCard 没传 routeOf = host.routeOf(it)（会用到带全局退避的默认值）')
  }
} catch (e) {
  fail.push('I5 读不到 GlassDeviceScreen.kt：' + e.message)
}

// ---- I6 [2026-10-20 血泪]：**不许静默清用户的设备地址**
// 现场：为了"修重复地址"，加了一段自动清理，结果把用户家电脑唯一可用的内网地址清空了
// （两台拿到同一份地址时，"保留哪一份"App 没有可信证据可判）。结论：检测可以，删除不行。
{
  const bad = []
  lines.forEach((line, i) => {
    const code = line.split('//')[0]
    if (/\.\s*(lanUrl|wanUrl)\s*=\s*""\s*;/.test(code)) bad.push(i + 1)
  })
  if (bad.length) {
    fail.push(`I6 MainActivity 第 ${bad.join(',')} 行在清空设备地址（lanUrl/wanUrl = ""）——` +
      '禁止静默删用户地址，只能"标出来让用户重新扫码"')
  }
}

if (fail.length) {
  console.error('✗ 多设备不变量检查未通过：')
  for (const f of fail) console.error('  - ' + f)
  process.exit(1)
}
console.log('✓ 多设备不变量检查通过（I1 连接点绑定 / I2 按设备判在线与线路 / I3 列表空窗守卫 / 清账本齐备）')
