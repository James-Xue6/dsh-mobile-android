<#
  DSH 掌上通 · 网关补丁（方案 5 · A 第 2 层）：隧道地址变化 / 启动就绪后**主动广播**
  ==================================================================
  用法（任意位置）：
      pwsh -File pc-plugin\patches\patch-gateway-route-broadcast.ps1
      pwsh -File pc-plugin\patches\patch-gateway-route-broadcast.ps1 -Revert
      pwsh -File pc-plugin\patches\patch-gateway-route-broadcast.ps1 -Profile desktop

  ⚠️ 打完补丁**需要用户重启一次 DSH** 才生效（本脚本不会、也不许去杀 DSH 进程）。

  用户要求（原话）：「怎么还多出个按钮，**后台自动不就行了**」
  → 手动按钮已从 App 移除；这条补丁负责"电脑侧主动推"，与 hello 握手同步合起来
    就是用户要的**全自动**：
      · 手机打开/连上   → hello 握手即同步 ✓（patch-gateway-hello-publicurl.ps1）
      · DSH 启动/地址变 → 电脑主动广播 route-updated → 手机自动更新 ✓（本补丁）
      · 回前台 / 5G↔WiFi → App 侧自动对比更新 ✓（已实现，不需要补丁）

  广播帧：{ kind: 'route-updated', publicUrl: 'wss://…/ws/mobile' }
    · App 侧分支已就绪（MainActivity.onOther 的 route-updated 分支）✓
    · 发送方式沿用网关既有广播写法（lib/index.mjs:3162）
        if (client.readyState === 1) client.send(stringifyWireFrame({...}))

  幂等：文件里已有标记行就不重复插入；-Revert 从 .bak-route-broadcast 恢复。
#>
param(
  [string]$Profile = 'desktop',
  [switch]$Revert
)

$ErrorActionPreference = 'Stop'

$marker = 'dsh-mobile:route-broadcast'
$target = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib\index.mjs"
$bak = "$target.bak-route-broadcast"

if (-not (Test-Path $target)) { throw "找不到网关文件：$target" }
Write-Host "网关文件：$target"

$text = Get-Content $target -Raw -Encoding UTF8

if ($Revert) {
  if ($text -notmatch [regex]::Escape($marker)) { Write-Host '  没有找到标记，无需回退'; exit 0 }
  if (-not (Test-Path $bak)) { throw "找不到备份 $bak，拒绝盲目回退（请手工核对）" }
  Copy-Item $bak $target -Force
  $after = Get-Content $target -Raw -Encoding UTF8
  if ($after -match [regex]::Escape($marker)) { throw '回退失败：标记仍在' }
  Write-Host '  ✓ 已回退到补丁前（从 .bak-route-broadcast 恢复）'
  Write-Host '  ⚠️ 需重启一次 DSH 生效'
  exit 0
}

if ($text -match [regex]::Escape($marker)) { Write-Host '  ✓ 已经打过这个补丁（幂等，未重复插入）'; exit 0 }

# 锚点：管理接口注册处（在插件 setup 作用域内，clients/tunnel/log 均可闭包捕获）
$anchor = 'const disposeMgmt = webServer.register({'
$count = ([regex]::Matches($text, [regex]::Escape($anchor))).Count
if ($count -ne 1) { throw "锚点出现 $count 次（期望 1 次），拒绝盲改" }

$inject = @"
        // $marker
        // [方案5·A 第2层] 隧道地址变化时 + 启动就绪后，主动把当前公网地址广播给所有已连接客户端。
        // 用户要的是"后台自动"：手机端不需要任何操作（App 侧 route-updated 分支已就绪）。
        try {
          let __dshLastRoute = null
          const __dshRouteNow = () => {
            try {
              const s = tunnel?.snapshot?.()
              return (s && s.publicUrl) || configuredPublicUrl() || null
            } catch { return null }
          }
          const __dshBroadcastRoute = (url) => {
            if (!url || url === __dshLastRoute) return
            __dshLastRoute = url
            for (const __c of clients) {
              try {
                if (__c.readyState === 1) __c.send(stringifyWireFrame({ kind: 'route-updated', publicUrl: url }))
              } catch { /* 单个连接失败不影响其它 */ }
            }
            try { log('route-updated broadcast -> ' + url) } catch { }
          }
          setTimeout(() => __dshBroadcastRoute(__dshRouteNow()), 3000)          // 启动就绪后先发一次
          const __dshRouteTimer = setInterval(() => __dshBroadcastRoute(__dshRouteNow()), 5000)
          if (__dshRouteTimer.unref) __dshRouteTimer.unref()
        } catch { /* 广播失败不影响网关主流程 */ }

"@

if (-not (Test-Path $bak)) { Copy-Item $target $bak -Force; Write-Host "  已备份：$bak" }

$new = $text.Replace($anchor, $inject + $anchor)
if ($new -eq $text) { throw '替换没有生效（锚点未变？）' }
Set-Content -Path $target -Value $new -Encoding UTF8 -NoNewline

# 落盘后复核：标记存在 + 锚点仍唯一 + 广播帧类型写对
$verify = Get-Content $target -Raw -Encoding UTF8
if ($verify -notmatch [regex]::Escape($marker)) { throw '复核失败：标记没写进去' }
if (([regex]::Matches($verify, [regex]::Escape($anchor))).Count -ne 1) { throw '复核失败：锚点数量异常' }
if ($verify -notmatch "kind: 'route-updated'") { throw '复核失败：广播帧类型缺失' }
Write-Host '  ✓ 已注入 route-updated 广播（启动就绪后一次 + 每 5s 检查地址变化）'
Write-Host '  ⚠️ 需用户重启一次 DSH 生效（本脚本不重启、不杀进程）'
