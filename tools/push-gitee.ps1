<#
  DSH 掌上通 · 一键把仓库同步到 Gitee（含全部分支与 tag）
  ==================================================================
  为什么要有它：Gitee 上的仓库**必须先在网页上建**（空仓库即可），SSH 公钥也得先在
  Gitee 账号里登记 —— 这两步只能人工在网页做。做完之后，本脚本负责剩下的全部：
  配远程 + 推 main + 推所有 tag + 校验。

  用法（仓库根目录）：
      pwsh -File tools\push-gitee.ps1 -GiteeUser <你的Gitee用户名>
      pwsh -File tools\push-gitee.ps1 -GiteeUser <用户名> -RepoName dsh-mobile-android
      pwsh -File tools\push-gitee.ps1 -UseHttps -GiteeUser <用户名>   # 不想用 SSH 时

  配好之后：release.ps1 会自动带上 Gitee（它认的远程名就是 `gitee`），以后不用再管。
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$GiteeUser,
  [string]$RepoName = 'dsh-mobile-android',
  [switch]$UseHttps
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

function Info($m) { Write-Host "  $m" }
function Ok($m)   { Write-Host "  OK $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  ! $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "  X $m" -ForegroundColor Red; exit 1 }

$url = if ($UseHttps) { "https://gitee.com/$GiteeUser/$RepoName.git" }
       else           { "git@gitee.com:$GiteeUser/$RepoName.git" }

Write-Host "`n=== 把仓库同步到 Gitee ===" -ForegroundColor Cyan
Info "目标：$url"

# ---------------------------------------------------------------- 1. 配远程
$remotes = @(& git remote)
if ($remotes -contains 'gitee') {
  $old = (& git remote get-url gitee).Trim()
  if ($old -eq $url) { Ok "gitee 远程已是 $url" }
  else { & git remote set-url gitee $url | Out-Null; Ok "gitee 远程已更新：$old -> $url" }
} else {
  & git remote add gitee $url | Out-Null
  Ok "已添加 gitee 远程"
}

# ---------------------------------------------------------------- 2. 先探一下能不能连（失败就给可读的原因）
Info "探测连通性…"
$probe = & git ls-remote --heads gitee 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
  Write-Host $probe -ForegroundColor DarkGray
  Die @"
连不上 Gitee。最常见两个原因，去网页处理完再跑一次：
  ① **Gitee 上还没建仓库** —— 打开 https://gitee.com/projects/new
     仓库名填 $RepoName，**不要**勾「初始化仓库」（README/.gitignore/许可证全不勾，我们要推已有的）
  ② **这把 SSH 公钥没加到 Gitee** —— 打开 https://gitee.com/profile/sshkeys
     把下面这行整条粘进去（标题随便写）：
     $(Get-Content "$env:USERPROFILE\.ssh\id_ed25519_dsh_mobile.pub" -Raw)
     或者改用 HTTPS：重跑时加 -UseHttps（会要你输 Gitee 账号密码/私人令牌）
"@
}
Ok "连通性正常"

# ---------------------------------------------------------------- 3. 推 main + 所有 tag
Info "推送 main…"
& git push gitee HEAD:main 2>&1 | ForEach-Object { Write-Host "    $_" }
if ($LASTEXITCODE -ne 0) { Die "推送 main 失败（看上面输出）" }

Info "推送所有 tag…"
& git push gitee --tags 2>&1 | ForEach-Object { Write-Host "    $_" }
# tag 已存在会返回非 0，但那是"没有新 tag 要推"，不算失败
Warn "若上面显示 Everything up-to-date / already exists，属正常（没有新 tag）"

# ---------------------------------------------------------------- 4. 校验
Info "校验远端…"
$remoteHeads = (& git ls-remote --heads gitee) -join "`n"
$localMain   = (& git rev-parse main).Trim()
if ($remoteHeads -match [regex]::Escape($localMain)) { Ok "Gitee 的 main 与本地一致（$($localMain.Substring(0,7))）" }
else { Warn "远端 main 与本地不一致，请到 Gitee 页面核对" }

$localTags = (& git tag) | Where-Object { $_ -match '^v' }
$remoteTags = (& git ls-remote --tags gitee) -join "`n"
$missing = @($localTags | Where-Object { $remoteTags -notmatch [regex]::Escape("refs/tags/$_") })
if ($missing.Count -eq 0) { Ok "全部 $($localTags.Count) 个 tag 都已在 Gitee" }
else { Warn "这些 tag 没推上去：$($missing -join ', ')" }

Write-Host "`n=== 完成 ===" -ForegroundColor Cyan
Write-Host "  Gitee 仓库：https://gitee.com/$GiteeUser/$RepoName"
Write-Host "  APK 直链（Gitee 的 raw，国内通常更快）："
Write-Host "    https://gitee.com/$GiteeUser/$RepoName/raw/main/dist/dsh-mobile.apk"
Write-Host "`n  以后发版：release.ps1 会自动同步到 Gitee（它认的远程名就是 `gitee`），不用再管。" -ForegroundColor DarkGray
