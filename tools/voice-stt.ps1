# 手机语音输入 —— 服务端识别（DSH 掌上通）
#
# 用途：手机录一段 WAV（16kHz / 单声道 / PCM16）传上来，这里识别成文字。
# 为什么放电脑做：手机上的系统 ASR 在国产 ROM 上不可用（真机实测：华为弹 ERROR_NETWORK），
# 而**电脑上 Windows 自带的中文识别引擎可用**（Microsoft Speech Recognizer 8.0 zh-CN）——
# 识别放电脑侧、手机只负责录音，就绕开了手机系统语音服务这个坑。
#
# 用法：
#   powershell -NoProfile -File voice-stt.ps1 <wav路径> [输出txt路径]
# 输出：识别文本写进 txt（UTF-8 无 BOM），同时打到 stdout；失败退出码 1、原因写 stderr。
#
# 已知准确率：Windows 自带引擎是**听写级**，实测中文七八成
#（"帮我看看今天的工作日志" → "当我看看今天的工作业绩"）。要更高准确率换 whisper 引擎（另装模型）。

param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Wav,
    [Parameter(Position = 1)][string]$Out = '',
    [string]$Culture = 'zh-CN'
)

# 注意（本机调试记录）：下面这段必须是**最朴素的写法**：
#   · 内联 (Resolve-Path ...).Path 会被 PS 5.1 的参数绑定吃掉 → "No audio input is supplied"；
#   · SetInputToAudioStream 在这个环境里也不认；
#   · 只有"独立变量 + SetInputToWaveFile"验证通过。
Add-Type -AssemblyName System.Speech

$ci = [System.Globalization.CultureInfo]::GetCultureInfo($Culture)
$eng = New-Object System.Speech.Recognition.SpeechRecognitionEngine -ArgumentList $ci

# **顺序不能换**（本机调试记录）：先 SetInputToWaveFile、后 LoadGrammar。
# 反过来写（先 LoadGrammar 再设输入）在脚本里会表现为"引擎没有音频输入"或直接卡死，
# 而同样代码在交互式命令里却能跑 —— 这条顺序是按"能跑通的那个版本"固化的。
$full = (Resolve-Path -LiteralPath $Wav).Path
Write-Verbose "WAV = $full"
$eng.SetInputToWaveFile($full)
$eng.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))

# 识别：**第一次调用不带超时**（它才会把整个文件读完），之后的调用一律给 1 秒超时。
# 踩过的坑：写成 while(true){ Recognize() } 会在音频结尾**无限阻塞**（本机实测卡死 5 分钟以上），
# 因为 EOF 时无参 Recognize() 会一直等新输入。加超时后取到 null 立即结束。
$sb = New-Object System.Text.StringBuilder
$r = $eng.Recognize()
while ($null -ne $r) {
    [void]$sb.Append($r.Text)
    $r = $eng.Recognize([TimeSpan]::FromSeconds(1))
}
$eng.Dispose()

$text = $sb.ToString().Trim()
if ([string]::IsNullOrWhiteSpace($text)) {
    [Console]::Error.Write('没识别出任何文字（录音太短、太轻，或不是 16kHz/单声道 PCM WAV）')
    exit 1
}
if ($Out -ne '') {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Out, $text, $enc)
}
[Console]::Out.Write($text)
exit 0
