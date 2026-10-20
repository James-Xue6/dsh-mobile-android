<#
安装 DSH 掌上通 的 PC 端插件（dsh-mobile-access）。

用法（在仓库根目录执行）：
    pwsh -File .\pc-plugin\install.ps1
    pwsh -File .\pc-plugin\install.ps1 -Profile desktop     # 指定 profile

它会做四件事：
 1. 把 pc-plugin\dsh-mobile-access 复制到 ~/.dsh/local-plugins/dsh-mobile-access
    （旧目录与任何同名残留先挪到 ~/.dsh/backups/ —— **不留在 local-plugins 里**，
      否则 DSH 会扫到第二个同名插件，用户看到"两个插件"甚至渲染到旧版面板）
 2. 把安装包 dist\dsh-mobile.apk 放进插件目录的 app\（面板据此提供「扫码下载 App」）
 3. 在目标 profile 的 package.json 里登记依赖与 bundle
    （dsh-plugin-mobile-gateway 只登记依赖；它的宿主行由本插件的 cordis.patch.yml 挂载，
     旧版写进 bundles 的残留项会被摘掉，避免同一个行被两个 bundle 层各插一次）
 4. 在目标 profile 的 cordis.patch.yml 里补一段 mobile-gateway 配置（lanPort 3091，避开 dsh-pocket 的 3081）

改完需要**重启一次 DSH** 才生效。原文件都会先备份成 *.bak-install。
#>
[CmdletBinding()]
param(
  [string]$Profile = 'desktop'
)

$ErrorActionPreference = 'Stop'

# ── [2026-10-07 合并朋友 fork] 文本一律显式按 UTF-8（无 BOM）读写 ──────────────
# 为什么不用 Get-Content / Set-Content 的默认行为：
#   · Windows PowerShell 5.1 的 Get-Content 默认按 ANSI 代码页解码，而 profile 的
#     package.json / cordis.patch.yml 里有中文（例如 link:L:/DSH自制插件/...）——
#     读成乱码再写回去就会**把用户的中文路径写坏**；
#   · Set-Content -Encoding utf8 在 5.1 会写出 BOM，JSON/YAML 前面的 BOM 会让一些
#     解析器报错，version.txt 也会带上看不见的字符。
# 注意：**本脚本自身的 [1/4] 备份位置等修复照旧**（朋友那一版把它们退回去了，没有采用）。
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Read-Text([string]$p) { return [System.IO.File]::ReadAllText($p, $utf8) }
function Write-Text([string]$p, [string]$t) { [System.IO.File]::WriteAllText($p, $t, $utf8) }
# 写 JSON：5.1 的 ConvertTo-Json 会把中文转义成 \uXXXX（仍合法、DSH 读得懂，但人看着累），
# 还原成字符后再解析校验一次，不过就用转义版兜底。
function Write-JsonFile([string]$p, $o) {
  $json = $o | ConvertTo-Json -Depth 20
  $unescaped = [System.Text.RegularExpressions.Regex]::Replace(
    $json, '(?<!\\)\\u([0-9a-fA-F]{4})',
    [System.Text.RegularExpressions.MatchEvaluator] { param($m) [string][char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
  $text = $json
  try { $null = $unescaped | ConvertFrom-Json; $text = $unescaped } catch { }
  Write-Text $p $text
}
$repoRoot = Split-Path $PSScriptRoot -Parent
$dshHome = Join-Path $env:USERPROFILE '.dsh'
$profileDir = Join-Path $dshHome "profiles\$Profile"
$pluginSrc = Join-Path $PSScriptRoot 'dsh-mobile-access'
$pluginDst = Join-Path $dshHome 'local-plugins\dsh-mobile-access'
# [P1 修复] 插件目录的备份统一放这里，**绝不放 local-plugins\**：
# 那个目录会被 DSH 当插件来源扫描，里面留一份 dsh-mobile-access.bak-install-*（package.json
# 的 name 同样是 dsh-mobile-access）就会变成"两个插件"，用户还可能看到旧版面板。
$backupDir = Join-Path $dshHome 'backups'
$apkSrc = Join-Path $repoRoot 'dist\dsh-mobile.apk'
$pluginName = 'dsh-mobile-access'
$gatewayName = 'dsh-plugin-mobile-gateway'
$gatewayVersion = '0.9.0'

function Info($m) { Write-Host "  $m" }
function Ok($m) { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }

Write-Host "`n=== DSH 掌上通 · PC 插件安装 ===" -ForegroundColor Cyan
Info "仓库     : $repoRoot"
Info "profile  : $Profile  ($profileDir)"

if (-not (Test-Path $pluginSrc)) { throw "找不到插件源码：$pluginSrc" }
if (-not (Test-Path $profileDir)) { throw "找不到 profile 目录：$profileDir（profile 名写对了吗？）" }

# ---------------------------------------------------------------- 1. 复制插件
Write-Host "`n[1/4] 复制插件到 local-plugins" -ForegroundColor Cyan
# 先把 local-plugins\ 里所有 dsh-mobile-access* 残留（旧的正式目录 + 历史 .bak-install-*）
# 统一挪到 ~/.dsh/backups/。为什么必须挪走而不是原地改名：
# 原地改名后目录里的 package.json 仍然写着 name = dsh-mobile-access，DSH 扫描时
# 会把它当成第二个同名插件 → 用户看到"两个插件"，甚至渲染的是旧版面板。
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$pluginRoot = Split-Path $pluginDst -Parent
New-Item -ItemType Directory -Force -Path $pluginRoot | Out-Null
$stale = @(Get-ChildItem -Path $pluginRoot -Directory -Filter "$pluginName*" -ErrorAction SilentlyContinue)
foreach ($d in $stale) {
  $dest = Join-Path $backupDir ($d.Name + '.' + (Get-Date -Format yyyyMMdd-HHmmss))
  Move-Item $d.FullName $dest
  Warn "移走同名残留：local-plugins\$($d.Name)  ->  $dest"
}
if ($stale.Count -eq 0) { Info "local-plugins 下没有同名残留" }
Copy-Item $pluginSrc $pluginDst -Recurse -Force
Ok "已复制 -> $pluginDst"

# ---------------------------------------------------------------- 2. 放安装包
Write-Host "`n[2/4] 放入手机安装包（面板用于扫码下载）" -ForegroundColor Cyan
if (Test-Path $apkSrc) {
  $appDir = Join-Path $pluginDst 'app'
  New-Item -ItemType Directory -Force -Path $appDir | Out-Null
  Copy-Item $apkSrc (Join-Path $appDir 'dsh-mobile.apk') -Force
  Ok ("已放入 app\dsh-mobile.apk（{0:N1} KB）" -f ((Get-Item $apkSrc).Length / 1KB))
  # version.txt 必须与这个 APK 一致：面板「安装包信息」显示它，别再出现
  # 「文件是 0.81 的包、版本号写着 0.8」这种对不上的情况。
  # 以 AndroidManifest 的 versionName 为准（build.ps1 就是照它打的包）；
  # 读不到再退到 dist\version.json。注意 version.json 只有跑 release.ps1 才会更新，
  # 所以它可能落后于工作区里刚构建出来的包，只作兜底。
  $verName = ''
  $manifestPath = Join-Path $repoRoot 'AndroidManifest.xml'
  if (Test-Path $manifestPath) {
    $mName = [regex]::Match((Read-Text $manifestPath), 'android:versionName="([^"]+)"')
    if ($mName.Success) { $verName = $mName.Groups[1].Value.Trim() }
  }
  if (-not $verName) {
    $verJson = Join-Path $repoRoot 'dist\version.json'
    if (Test-Path $verJson) {
      try { $verName = (Read-Text $verJson | ConvertFrom-Json).versionName } catch { }
    }
  }
  if ($verName) {
    Write-Text (Join-Path $appDir 'version.txt') $verName
    Ok "app\version.txt = $verName（与 APK 的 versionName 一致）"
  } else {
    Warn "没能读到 versionName，app\version.txt 未更新（面板会显示成兜底版本 0.2）"
  }
} else {
  Warn "仓库里没有 dist\dsh-mobile.apk，先跑一次 .\build.ps1 再来；面板会显示「没有安装包」"
}

# ---------------------------------------------------------------- 3. 登记 profile
Write-Host "`n[3/4] 登记到 profile 的 package.json" -ForegroundColor Cyan
$pkgPath = Join-Path $profileDir 'package.json'
Copy-Item $pkgPath "$pkgPath.bak-install" -Force
$pkg = Read-Text $pkgPath | ConvertFrom-Json

if (-not $pkg.dependencies.PSObject.Properties[$gatewayName]) {
  $pkg.dependencies | Add-Member -NotePropertyName $gatewayName -NotePropertyValue $gatewayVersion
  Ok "依赖 + $gatewayName = $gatewayVersion"
} else {
  Info "依赖已有 $gatewayName"
}
$linkTarget = 'link:' + ($pluginDst -replace '\\', '/')
if (-not $pkg.dependencies.PSObject.Properties[$pluginName]) {
  $pkg.dependencies | Add-Member -NotePropertyName $pluginName -NotePropertyValue $linkTarget
  Ok "依赖 + $pluginName = $linkTarget"
} else {
  if ($pkg.dependencies.$pluginName -ne $linkTarget) {
    $pkg.dependencies.$pluginName = $linkTarget
    Ok "依赖 $pluginName 指向已更新"
  } else { Info "依赖已有 $pluginName" }
}

# 只把本插件登记为 bundle：dsh-plugin-mobile-gateway 现在由本插件自己的
# cordis.patch.yml 挂载（id: mobile-gateway），这里不再把它塞进 bundles；
# 已经装过旧版的要把残留项摘掉，否则同一个行会被两个 bundle 层各插一次。
#
# [P1 修复] 追加前**先查重**：同一项在 bundles 里出现两次，DSH 会把同一个插件加载两遍
# （本机实测过 "dsh-mobile-access" 出现 2 次的报告）。这里顺手做归一化（去空白、丢空项）
# 并对整表去重，把已经存在的重复项一起修掉。
$bundles = @($pkg.dsh.profile.bundles | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
$beforeCount = $bundles.Count
$bundles = @($bundles | Select-Object -Unique)
if ($bundles.Count -lt $beforeCount) { Warn "bundles 里有 $($beforeCount - $bundles.Count) 个重复项，已去掉" }
if ($bundles -notcontains $pluginName) { $bundles += $pluginName; Ok "bundles + $pluginName" }
else { Info "bundles 已有 $pluginName（查重通过，不重复追加）" }
if ($bundles -contains $gatewayName) {
  $bundles = @($bundles | Where-Object { $_ -ne $gatewayName })
  Ok "bundles - $gatewayName（改由本插件的 patch 挂载，避免重复行）"
}
$pkg.dsh.profile.bundles = @($bundles)
Write-JsonFile $pkgPath $pkg
Ok "package.json 已更新（备份：package.json.bak-install）"
# 落盘后复核：只看"没报错"不算验证 —— bundles 写坏了会直接把 DSH 卡在启动
$verify = @((Read-Text $pkgPath | ConvertFrom-Json).dsh.profile.bundles)
$dup = @($verify | Group-Object | Where-Object { $_.Count -gt 1 })
if ($dup.Count -gt 0) { throw "package.json 里 bundles 仍有重复项：$($dup.Name -join ', ')" }
if (@($verify | Where-Object { $_ -eq $pluginName }).Count -ne 1) {
  throw "package.json 里 $pluginName 在 bundles 中出现次数不是 1"
}
Ok "复核通过：bundles 无重复，$pluginName 恰好 1 次（共 $($verify.Count) 项）"

# ---------------------------------------------------------------- 4. 补网关配置
Write-Host "`n[4/4] 补 mobile-gateway 配置（lanPort 3091）" -ForegroundColor Cyan
$patchPath = Join-Path $profileDir 'cordis.patch.yml'
$patch = Read-Text $patchPath
if ($patch -match 'id:\s*mobile-gateway') {
  Info "cordis.patch.yml 已有 mobile-gateway 配置，跳过"
} else {
  Copy-Item $patchPath "$patchPath.bak-install" -Force
  $block = @'

# == DSH 掌上通：手机端网关（由 pc-plugin/install.ps1 追加）==
# lanPort 用 3091，避开 dsh-pocket 默认占用的 3081。
- id: mobile-gateway
  config:
    path: /ws/mobile
    requireAuth: true
    gatewayEnabled: true
    gatewayMode: persistent
    adminLoopbackOnly: true
    pairingTtlMs: 300000
    lanEnabled: true
    lanHost: 0.0.0.0
    lanPort: 3091
'@
  Write-Text $patchPath ($patch + $block)
  Ok "cordis.patch.yml 已追加配置（备份：cordis.patch.yml.bak-install）"
}

# ---------------------------------------------------------------- 附：local-plugins 残留体检
# 「两个插件 / 老界面」的根因就是这里留着 dsh-mobile-access.bak-install-*。
# 本脚本 [1/4] 已经自动把它们挪走了；这一段是落盘后的复核 + 手动清理指引。
Write-Host "`n[附] 检查 local-plugins 下的同名残留" -ForegroundColor Cyan
$leftover = @(Get-ChildItem -Path $pluginRoot -Directory -Filter "$pluginName*" -ErrorAction SilentlyContinue |
              Where-Object { $_.FullName -ne $pluginDst })
if ($leftover.Count -eq 0) {
  Ok "local-plugins 下只剩一个 $pluginName（没有 .bak-install-* 残留）"
} else {
  Warn "local-plugins 下仍有同名目录：$($leftover.Name -join ', ')"
  Warn "  手动清理（挪到备份目录，不要直接删）："
  foreach ($d in $leftover) {
    Warn "    Move-Item '$($d.FullName)' '$backupDir\$($d.Name).manual'"
  }
}

# ---------------------------------------------------------------- 完成
Write-Host "`n=== 安装完成 ===" -ForegroundColor Cyan
Write-Host @"
下一步：
  1. 重启 DSH 桌面版（panel/宿主代码都是启动时加载的）
  2. 点左侧边栏底部的「移动设备」按钮 —— PC 端只有这一个入口
       抽屉第一屏就是「手机接入」卡片，只有三个按钮：
         · 下载 App        —— 手机连同一 WiFi 扫码即可下载安装 APK
         · 生成公网二维码   —— 出门在外用（临时域名，电脑重启后会变，需重新扫码）
         · 生成内网二维码   —— 在家用
       网关开关、公网隧道、已配对设备与吊销都在下面的「高级设置」里（默认收起）
       · 设置 → 通用 →「手机接入」现在只做跳转，不再重复一份功能
  3. 若面板提示网关不可用，确认 dsh-plugin-mobile-gateway 已装且已重启
  4. 若曾经看到"两个插件 / 老界面"：那是 local-plugins 里留了 .bak-install-* 残留。
     本脚本 [1/4] 已自动挪到 $backupDir；想手动再清一遍就照 [附] 那段打印的命令做
     （把 <残留目录名> 换成实际名字，挪走而不是删除）：
       Move-Item "$pluginRoot\<残留目录名>" "$backupDir\<残留目录名>.manual"
"@

# ---------------------------------------------------------------- 附：网关面板补丁
# 「手机接入」并入「移动设备」这一步要改第三方网关包的 lib/client.js（升级会被覆盖），
# 所以走 pc-plugin\patches\ 下的「打补丁 + 可重放脚本」。脚本自带版本校验（0.9.0），
# 版本不符会报错退出而不是把插件降级 —— 这里只在文件已就位时顺手跑一次。
#
# [2026-10-20 修] 这里原来只在网关已落盘时才跑补丁，而且缺失时只 Warn 一句。
# 结果：**全新机器第一次装必然错过补丁** —— install.ps1 只登记依赖，网关是 DSH 下次启动时
# 才由 pnpm 装上的；等用户重启完 DSH，补丁那一步早就跑过了 ⇒ 看到的是上游原版面板
# （右侧整栏、没有「手机接入」卡片），而且新内核上还会缺 peer → 手机报 reading 'signal'。
# 现在改成：缺网关时把后果说清楚，并在全部步骤结束后统一做一次「补丁是否真的落到盘上」的验收。
$gwClient = Join-Path $profileDir 'node_modules\dsh-plugin-mobile-gateway\lib\client.js'
$patchScript = Join-Path $PSScriptRoot 'patches\restore-gateway-panel-fix.ps1'
if (Test-Path $gwClient) {
  Write-Host "`n[附] 叠加网关面板补丁（居中弹窗 + 手机接入三个按钮置顶 + 去掉重复的旧高级设置）" -ForegroundColor Cyan
  if (Test-Path $patchScript) {
    try { & $patchScript } catch { Warn "补丁未应用：$($_.Exception.Message)" }
  } else { Warn "找不到 $patchScript" }
} else {
  Write-Host ""
  Warn "网关插件还没落盘：$gwClient"
  Warn "  这不是错误 —— 全新机器上 DSH 会在**下次启动**时才把 dsh-plugin-mobile-gateway 装上。"
  Warn "  但本脚本的补丁步骤只在网关已存在时才会执行，所以现在这几步会被跳过。"
  Warn "  ⇒ **重启一次 DSH 之后，必须再跑一次本脚本**（第二次会把补丁全部补上）。"
  Warn "     命令：pwsh -File .\pc-plugin\install.ps1"
  Warn "  不补的后果：面板是上游原版（右侧整栏、没有「手机接入」卡片），"
  Warn "  且新版 DSH 内核上缺 Connection Peer ⇒ 手机端报 reading 'signal'、0 个对话。"
}

# ---------------------------------------------------------------- 附：网关协议补丁（幂等、可重放）
#
# [2026-10-05 补] 以前这里**只跑面板补丁**，其余几个网关补丁（跨会话提醒 / hello 带公网地址 /
# 地址变化广播 / 通用文件附件）都是**手工**打的 —— 结果它们只活在 node_modules 里，
# 网关一升级/重装就静默失效（手机表现为：收不到跨会话提醒、出门连不上、发不了文件）。
# 现在装完统一重放一遍：每个脚本都自带「锚点唯一性预检 + 幂等标记 + -Revert」，
# 版本不符会报错退出而不是把插件降级，所以**重复跑是安全的**。
$gwLib = Join-Path $profileDir 'node_modules\dsh-plugin-mobile-gateway\lib'
if (Test-Path $gwLib) {
  Write-Host "`n[附] 叠加网关协议补丁（幂等、可重放；网关升级/重装后重跑本脚本即可）" -ForegroundColor Cyan
  $gatewayPatches = @(
    @{ File = 'patch-gateway-crosssession.ps1';    What = '跨会话提醒（放宽两道 waterfall 门 + 跨会话下发带 global 标记）' },
    @{ File = 'patch-gateway-hello-publicurl.ps1'; What = 'hello 帧带上电脑当前公网(隧道)地址（隧道换域名后自动同步，不用重新扫码）' },
    @{ File = 'patch-gateway-route-broadcast.ps1'; What = '地址变化 / 启动就绪后主动广播' },
    @{ File = 'patch-gateway-file-upload.ps1';     What = '通用文件附件（message.files[] + hello.capabilities: file-uploads）' },
    # [2026-10-19 补] DSH 内核 0.2.1-rc.2 起 invoke/stream 入口会 assertPeerLive(request.peer)，
    # 0.9.0 不带 peer 就抛 "Cannot read properties of undefined (reading 'signal')"：
    # 手机能连上、能鉴权，但 sessions / workspaces 全失败（0 对话 + 弹窗报错）。
    # 必须放最后：它是「整文件替换」，落的是 0.9.0 + 上面全部补丁 + peer 的已验收完整文件。
    @{ File = 'restore-gateway-connection-peer.ps1'; What = 'DSH 0.2.1-rc.2 的 Connection Peer（invoke/stream 全部透传 peer）' }
  )
  foreach ($p in $gatewayPatches) {
    $s = Join-Path $PSScriptRoot "patches\$($p.File)"
    if (-not (Test-Path $s)) { Warn "找不到 $s"; continue }
    Write-Host "  · $($p.What)"
    try { & $s } catch { Warn "    $($p.File) 未应用：$($_.Exception.Message)" }
  }
  Write-Host "  提示：这些补丁改的是 node_modules 里的网关文件，**网关升级/重装后请重跑** pwsh -File .\pc-plugin\install.ps1" -ForegroundColor Yellow
} else {
  Warn "网关插件还没落盘（$gwLib 不存在）：先让 DSH 装好 dsh-plugin-mobile-gateway，再手动重跑一次本脚本"
}

# ---------------------------------------------------------------- 落盘验收（别只看「没报错」）
# 这是 2026-10-19 另一台电脑「装完界面和这边不一样」的直接教训：
# 补丁没落盘时，上面的补丁步骤只是 Warn，用户看到的是「安装完成」→ 却拿到别的界面。
Write-Host "`n[验收] 网关补丁是否真的落到盘上" -ForegroundColor Cyan
$checks = @(
  @{ Name = '面板 v2（居中弹窗）'; File = 'client.js';             Marker = '\[pc-plugin patch\] dsh-mobile-access panel v2' },
  @{ Name = 'Connection Peer';     File = 'index.mjs';             Marker = 'local patch: DSH 0\.2\.1-rc\.2' },
  @{ Name = 'Connection Peer';     File = 'dsh-host-adapter.mjs';  Marker = 'local patch: DSH 0\.2\.1-rc\.2' },
  @{ Name = 'cloudflared 镜像回退'; File = 'cloudflared-binary.mjs'; Marker = 'MIRROR_PREFIXES' }
)
$missingPatches = @()
foreach ($c in $checks) {
  $f = Join-Path $gwLib $c.File
  if (-not (Test-Path $f)) { $missingPatches += "$($c.Name)（文件不存在：$($c.File)）"; continue }
  $text = Read-Text $f
  if ($text -match $c.Marker) {
    Write-Host "  OK  $($c.Name)  →  $($c.File)"
  } else {
    Write-Host "  缺失  $($c.Name)  →  $($c.File)" -ForegroundColor Yellow
    $missingPatches += "$($c.Name)（$($c.File)）"
  }
}
if ($missingPatches.Count -eq 0) {
  Write-Host "  全部补丁已就位。" -ForegroundColor Green
} else {
  Write-Host ""
  Write-Host "  !! 还有 $($missingPatches.Count) 项补丁没落盘：$($missingPatches -join '；')" -ForegroundColor Red
  Write-Host "  多数情况是「网关还没装好就来跑本脚本」。处理顺序：" -ForegroundColor Yellow
  Write-Host "    1) 重启 DSH 桌面版（让它把 dsh-plugin-mobile-gateway 装上）" -ForegroundColor Yellow
  Write-Host "    2) 再跑一次：pwsh -File .\pc-plugin\install.ps1" -ForegroundColor Yellow
  Write-Host "    3) 再刷新/重启一次 DSH" -ForegroundColor Yellow
}
