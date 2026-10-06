<#
  DSH 掌上通 · 网关补丁：通用文件附件上传（移动端 → 宿主 fileUploads）
  ==================================================================
  用法（工作区根目录，或任意位置）：
      pwsh -File pc-plugin\patches\patch-gateway-file-upload.ps1
      pwsh -File pc-plugin\patches\patch-gateway-file-upload.ps1 -Revert
      pwsh -File pc-plugin\patches\patch-gateway-file-upload.ps1 -Profile desktop

  ⚠️ 打完补丁**需要用户重启一次 DSH** 才生效（本脚本不会、也不许去杀 DSH 进程）。

  ⚠️⚠️ 状态：**未在真机/真宿主上端到端验证过**（实现者按契约纪律不跑 build/模拟器，
      也不重启宿主）。协议可达性已逐条取证，见 docs/PROTO-FINDINGS.md；启用前请先按
      该文档「启用前必须补的验证」一节过一遍。

  问题：
      message 帧只支持图片（lib/index.mjs:413 parseWireImages），
      通用文件（PDF/Office/压缩包…）目前**没有上传通道**，
      而宿主 prompt 已支持 { type:'file', receiptId } 内容块（见下方证据）。

  证据（已查证，不是猜的）：
      1) 宿主 prompt 内容块支持文件引用：
         app.asar:331156  PromptContentPart = text | image | { type:'file', receiptId }
      2) receipt 由宿主 fileUploads 服务产生，且该服务是 Typert Remote：
         app.asar:372760  id: '@deepseek-ai/dsh-client-file-upload#fileUploads/upload'
         app.asar:372821  @Remote('upload') upload(agent, request:{data,name?}, signal)
         app.asar:372769  parameters[0] = { name:'agent', wire:'agentId', source:'lookup' }
         app.asar:372744  result = { receiptId, file:{attachmentId,name,bytes} }
      3) 网关已经持有等价的 RPC 入口：
         lib/index.mjs:2127  inject: ['webServer','typertGateway','agentDefaultModel']
         lib/index.mjs:2133  const api = createDshHostAdapter(ctx.typertGateway)
         app.asar:297516     Host ctx.typertGateway 与 Client ctx.remote 是同一份
                             InvocationDescriptor 契约的两端
      4) 同一"agentId 线格式 + 纯字符串 sessionId"的调用，网关已经在用且工作：
         lib/dsh-host-adapter.mjs:220  commands.list(sessionId)
         （commands/list 的 descriptor 与 fileUploads/upload 同形：
           scope {context:'agent', wire:'agentId'} + parameters[0] source:'lookup' lookup:'agent'）

  补丁做什么（两个文件，一个脚本原子完成）：
      A. lib/dsh-host-adapter.mjs：给适配器加一个领域方法
         uploadFile(agentId, data, name, signal) → { receiptId, file }
         —— 走 typertGateway.invoke('fileUploads','upload',{agentId, request:{data,name?}})。
      B. lib/index.mjs：message 帧新增可选 files[]（元素 {data,name?}，data 为标准 Base64），
         在 admitMessage 里先把每个文件换成 receipt，再拼进 prompt content：
           content = [...imageParts, ...fileParts, ...(text ? [{type:'text',text}] : [])]

  幂等：两处都用注释标记行判定；-Revert 按标记还原（靠 .bak-file-upload 备份）。
        任一文件失败则整体回滚（不允许出现"只打了一半"的网关）。

  失败码（网关回帧，与既有 fileTransferError 同一套风格）：
      bad-request     files 不是数组 / 元素非对象 / data 非非空字符串 / name 超 255 字符
      bad-request     files 超过 20 个
      bad-request     text 与 files 同时为空（沿用既有「requires non-empty text or ...」语义）
      file-too-large  单帧 base64 超过网关 maxPayloadBytes（默认 144 MiB）→ 传输层限制
      internal        宿主 fileUploads/upload 抛错（透传 error.message）
#>
param(
  [string]$Profile = 'desktop',
  [switch]$Revert
)

$ErrorActionPreference = 'Stop'

# 目标文件是 LF-only（实测：index.mjs 3457 个 LF、仅 1 个 CRLF；adapter 441 个 LF、0 个 CRLF）。
# 因此所有插入/锚点都必须用 LF，混进 CRLF 会让锚点匹配不上、也会污染文件。
$LF = [string][char]10

$adapterMarker = 'dsh-mobile:file-upload-adapter'
$frameMarker   = 'dsh-mobile:file-upload-frame'

$lib = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib"
$adapter = Join-Path $lib 'dsh-host-adapter.mjs'
$frame   = Join-Path $lib 'index.mjs'

foreach ($f in @($adapter, $frame)) {
  if (-not (Test-Path $f)) { throw "找不到网关文件：$f" }
}

$adapterBak = "$adapter.bak-file-upload"
$frameBak   = "$frame.bak-file-upload"

function Test-Marked($path, $marker) {
  return ((Get-Content $path -Raw -Encoding UTF8) -match [regex]::Escape($marker))
}

# ---------------------------------------------------------------- 回退
if ($Revert) {
  $any = (Test-Marked $adapter $adapterMarker) -or (Test-Marked $frame $frameMarker)
  if (-not $any) {
    Write-Host '  没有找到标记，无需回退（文件未被本补丁改过）'
    exit 0
  }
  foreach ($pair in @(@($adapter, $adapterBak), @($frame, $frameBak))) {
    if (-not (Test-Marked $pair[0] $adapterMarker) -and -not (Test-Marked $pair[0] $frameMarker)) { continue }
    if (-not (Test-Path $pair[1])) { throw "找不到备份 $($pair[1])，拒绝盲目回退（请手工核对）" }
    Copy-Item $pair[1] $pair[0] -Force
  }
  if ((Test-Marked $adapter $adapterMarker) -or (Test-Marked $frame $frameMarker)) {
    throw '回退失败：标记仍在'
  }
  Write-Host '  ✓ 已回退到补丁前（从 .bak-file-upload 恢复两个文件）'
  Write-Host '  ⚠️ 需重启一次 DSH 生效'
  exit 0
}

# ---------------------------------------------------------------- 幂等
if ((Test-Marked $adapter $adapterMarker) -and (Test-Marked $frame $frameMarker)) {
  Write-Host '  ✓ 已经打过这个补丁（幂等，未重复插入）'
  exit 0
}
if ((Test-Marked $adapter $adapterMarker) -or (Test-Marked $frame $frameMarker)) {
  throw '检测到只打了一半（一个文件有标记、另一个没有）。请先 -Revert 再重打，不要在这个状态上继续。'
}

# ---------------------------------------------------------------- 预检：锚点必须唯一
$adapterText = Get-Content $adapter -Raw -Encoding UTF8
$frameText   = Get-Content $frame -Raw -Encoding UTF8

# 顶层字段插入点：必须在返回对象的**顶层**（api.uploadFile），不能落进 sessions 子对象
# （api.sessions.uploadFile 会让 index.mjs 的 host.uploadFile 变成 undefined）。
$adapterAnchor = "    commands," + $LF + "    host: {"
# parseWireImages 的结尾（插入 parseWireFiles 辅助函数的位置，全库唯一）
$helperAnchor = "  return { value: imageParts }" + $LF + "}"
# admitMessage 的「图片解析 + 空内容校验」四行块（全库唯一）
$frameEmptyAnchor = "  const imageParts = parsedImages.value" + $LF + "  if (!text && imageParts.length === 0) {" + $LF + "    return { kind: 'error', code: 'bad-request', message: 'message requires non-empty text or at least one image' }" + $LF + "  }"
# 真正上传的位置：sessionId 已就绪（新会话已 create）之后的 prompt 调用前
$framePromptAnchor = "    const resp = await api.sessions.prompt({"
$frameContentAnchor = "      content: [...imageParts, ...(text ? [{ type: 'text', text }] : [])],"
# [主理人 2026-10-05 补] 给 hello.capabilities 加一个能力名：客户端靠它判断"对端到底认不认
# message.files[]"。**没有它就不能盲发** —— 未打补丁的网关会静默忽略 files[]，文件悄悄丢掉，
# 用户以为发出去了。加了之后客户端才能按能力门决定"正常发 / 明确提示暂不支持"。
$capAnchor = "              ...(options.fileDownloadsEnabled ? ['file-downloads'] : []),"

# 早期只做「形状 + 数量」校验与空内容判定；真正的上传必须等 sessionId 就绪 ——
# 新会话的 sessionId 是在下面 try 里 create 之后才有的，且上传要绑定 agent，
# 早期引用 sessionId 会踩 let 的 TDZ（ReferenceError: Cannot access before initialization）。
$frameEmptyBlock = @"
  const imageParts = parsedImages.value
  // 文件附件（本补丁新增）：这里只做形状/数量校验与空内容判定，
  // 真正的上传放到 sessionId 就绪之后（见下面 parseWireFiles 调用）。
  const rawFiles = msg.files === undefined ? [] : msg.files
  if (!Array.isArray(rawFiles)) {
    return { kind: 'error', code: 'bad-request', message: 'files must be an array', requestType: 'message' }
  }
  if (rawFiles.length > 20) {
    return { kind: 'error', code: 'bad-request', message: 'a request can contain at most 20 files', requestType: 'message' }
  }
  if (!text && imageParts.length === 0 && rawFiles.length === 0) {
    return { kind: 'error', code: 'bad-request', message: 'message requires non-empty text, at least one image, or at least one file' }
  }
"@

$framePromptBlock = @"
    // $frameMarker (upload)
    // 此时 sessionId 一定已就绪（新会话已 create），且 fileUploads 绑定的是该 agent，
    // 所以 receipt 必然归属于即将 prompt 的那个会话。
    const parsedFiles = await parseWireFiles(api, sessionId, msg.files, 'message')
    if (parsedFiles.error) return parsedFiles.error
    const fileParts = parsedFiles.value
    const resp = await api.sessions.prompt({
"@

foreach ($a in @(
  @('dsh-host-adapter.mjs', $adapterText, $adapterAnchor),
  @('index.mjs',            $frameText,   $helperAnchor),
  @('index.mjs',            $frameText,   $frameEmptyAnchor),
  @('index.mjs',            $frameText,   $framePromptAnchor),
  @('index.mjs',            $frameText,   $frameContentAnchor),
  @('index.mjs',            $frameText,   $capAnchor)
)) {
  $n = ([regex]::Matches($a[1], [regex]::Escape($a[2]))).Count
  if ($n -ne 1) { throw "锚点（$($a[0])）出现 $n 次（期望 1 次），拒绝盲改：`n$a[2]" }
}

# ---------------------------------------------------------------- 备份（各只备份一次）
foreach ($pair in @(@($adapter, $adapterBak), @($frame, $frameBak))) {
  if (-not (Test-Path $pair[1])) { Copy-Item $pair[0] $pair[1] -Force; Write-Host "  已备份：$($pair[1])" }
}

# ---------------------------------------------------------------- A. 适配器方法
$adapterBlock = @"
    // $adapterMarker
    // 通用文件附件：宿主 prompt 的内容块 { type:'file', receiptId } 需要一个 receipt，
    // receipt 只能由宿主 fileUploads 服务签发（app.asar:372760 fileUploads/upload）。
    // 该服务与 commands/list 同形：scope {context:'agent', wire:'agentId'}，
    // 所以这里与 commands.list 一样传纯字符串 sessionId 作 agentId。
    // 返回 { receiptId, file:{attachmentId,name,bytes} }；失败按 Remote 错误抛出。
    uploadFile: (agentId, data, name, signal) => invoke('fileUploads', 'upload', {
      agentId,
      request: { data, ...(name === undefined || name === '' ? {} : { name }) },
    }, signal),
"@

$newAdapter = $adapterText.Replace($adapterAnchor, "    commands," + $LF + $adapterBlock + $LF + "    host: {")
if ($newAdapter -eq $adapterText) { throw '适配器替换没有生效（锚点未变？）' }
Set-Content -Path $adapter -Value $newAdapter -Encoding UTF8 -NoNewline
Write-Host '  ✓ 已给 Host Adapter 加 uploadFile()'

# ---------------------------------------------------------------- B. index.mjs
$helperBlock = @"
// $frameMarker (helper)
// 把 message.files[]（{data,name?}，标准 Base64）逐个换成宿主 receipt，
// 转成 prompt 可用的 { type:'file', receiptId } 内容块。
// 与 parseWireImages 同一套校验风格；数量上限 20 与图片对齐。
async function parseWireFiles(host, sessionId, rawFiles, requestType) {
  const files = rawFiles === undefined ? [] : rawFiles
  if (!Array.isArray(files)) {
    return { error: { kind: 'error', code: 'bad-request', message: 'files must be an array', requestType } }
  }
  if (files.length > 20) {
    return { error: { kind: 'error', code: 'bad-request', message: 'a request can contain at most 20 files', requestType } }
  }
  const fileParts = []
  for (let index = 0; index < files.length; index++) {
    const file = files[index]
    const where = 'files[' + String(index) + ']'
    if (!file || typeof file !== 'object') {
      return { error: { kind: 'error', code: 'bad-request', message: where + ' must be an object', requestType } }
    }
    if (typeof file.data !== 'string' || file.data.length === 0) {
      return { error: { kind: 'error', code: 'bad-request', message: where + '.data must be a non-empty base64 string', requestType } }
    }
    if (file.name !== undefined && (typeof file.name !== 'string' || file.name.length > 255)) {
      return { error: { kind: 'error', code: 'bad-request', message: where + '.name must be a string of at most 255 characters', requestType } }
    }
    try {
      const uploaded = await host.uploadFile(sessionId, file.data, file.name, new AbortController().signal)
      if (!uploaded || typeof uploaded.receiptId !== 'string' || !uploaded.receiptId) {
        throw new Error('file upload returned an invalid receipt')
      }
      fileParts.push({ type: 'file', receiptId: uploaded.receiptId })
    } catch (error) {
      const code = error && error.code ? error.code : 'internal'
      const message = error && error.message ? error.message : String(error)
      return { error: { kind: 'error', code, message, requestType } }
    }
  }
  return { value: fileParts }
}
"@

$newFrame = $frameText.Replace($helperAnchor, $helperAnchor + $LF + $helperBlock)
if ($newFrame -eq $frameText) { throw 'index.mjs 辅助函数插入没有生效（锚点未变？）' }

$newFrame0 = $newFrame.Replace($frameEmptyAnchor, $frameEmptyBlock)
if ($newFrame0 -eq $newFrame) { throw 'index.mjs 空内容块替换没有生效（锚点未变？）' }

$newFrame1 = $newFrame0.Replace($framePromptAnchor, $framePromptBlock)
if ($newFrame1 -eq $newFrame0) { throw 'index.mjs parseWireFiles 调用替换没有生效（锚点未变？）' }

$newFrame2 = $newFrame1.Replace(
  $frameContentAnchor,
  "      // $frameMarker (merge)" + $LF +
  "      content: [...imageParts, ...fileParts, ...(text ? [{ type: 'text', text }] : [])],"
)
if ($newFrame2 -eq $newFrame1) { throw 'index.mjs content 替换没有生效（锚点未变？）' }

# C. hello.capabilities 新增能力名 file-uploads（客户端据此判断对端支持度）
$newFrame3 = $newFrame2.Replace(
  $capAnchor,
  $capAnchor + $LF +
  "              // $frameMarker (cap) 本网关支持 message.files[]（通用文件附件）" + $LF +
  "              'file-uploads',"
)
if ($newFrame3 -eq $newFrame2) { throw 'index.mjs capabilities 替换没有生效（锚点未变？）' }

Set-Content -Path $frame -Value $newFrame3 -Encoding UTF8 -NoNewline
Write-Host '  ✓ 已给 message 帧加 files[] → receipt → prompt content'
Write-Host '  ✓ 已给 hello.capabilities 加 file-uploads'

# ---------------------------------------------------------------- 复核（只看"没报错"不算验证）
$va = Get-Content $adapter -Raw -Encoding UTF8
$vf = Get-Content $frame -Raw -Encoding UTF8
if ($va -notmatch [regex]::Escape($adapterMarker)) { throw '复核失败：适配器标记没写进去' }
if ($vf -notmatch [regex]::Escape($frameMarker)) { throw '复核失败：index.mjs 标记没写进去' }
if (([regex]::Matches($vf, [regex]::Escape($frameContentAnchor))).Count -ne 0) { throw '复核失败：旧 content 行仍在' }
if (([regex]::Matches($vf, [regex]::Escape("content: [...imageParts, ...fileParts"))).Count -ne 1) { throw '复核失败：新 content 行不唯一' }
if (([regex]::Matches($vf, [regex]::Escape("async function parseWireFiles("))).Count -ne 1) { throw '复核失败：parseWireFiles 不唯一' }
if (([regex]::Matches($vf, [regex]::Escape("const parsedFiles = await parseWireFiles("))).Count -ne 1) { throw '复核失败：parseWireFiles 调用不唯一' }
if (([regex]::Matches($va, [regex]::Escape("uploadFile: (agentId, data, name, signal)"))).Count -ne 1) { throw '复核失败：uploadFile 不唯一' }
if (([regex]::Matches($vf, [regex]::Escape("'file-uploads',"))).Count -ne 1) { throw '复核失败：file-uploads 能力名不唯一' }
# 结构性断言：uploadFile 必须落在顶层（在 "    commands," 之后、"    host: {" 之前），
# 否则会变成 api.sessions.uploadFile / api.host.uploadFile，index.mjs 调用时是 undefined。
$iCmd = $va.IndexOf("    commands," + $LF + "    // " + $adapterMarker)
$iUpload = $va.IndexOf("    uploadFile: (agentId, data, name, signal)")
$iHost = $va.IndexOf("    host: {" + $LF + "      describe:")
if ($iCmd -lt 0 -or $iUpload -lt 0 -or $iHost -lt 0 -or -not ($iCmd -lt $iUpload -and $iUpload -lt $iHost)) {
  throw '复核失败：uploadFile 不在返回对象的顶层（commands, 与 host: { 之间）'
}

Write-Host '  ✓ 两个文件都已打好（幂等标记已写入）'
Write-Host '  ⚠️ 需用户重启一次 DSH 生效（本脚本不重启、不杀进程）'
Write-Host ''
Write-Host '  ⚠️ 本补丁未经真机端到端验证；启用前请先读 docs/PROTO-FINDINGS.md'
Write-Host '  ✓ hello.capabilities 已加 file-uploads（客户端据此判断对端支持度，避免盲发被静默丢弃）'
