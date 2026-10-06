// 手机语音输入 —— 服务端识别（DSH 掌上通）
// 编译成 exe 而不是用 .ps1：SpeechRecognitionEngine 在 PowerShell 脚本文件里行为不稳
//（交互式能跑、写成文件报 No audio input 或卡死，本机实测多次）。
//
// 用法: voice-stt.exe <wav> [out.txt] [culture前缀，默认 zh]
//   成功: 文本 → stdout（同时写 out.txt，UTF-8 无 BOM），退出码 0
//   失败: 原因 → stderr，退出码 1
//   -list: 只列出本机可用的识别器（排查用）
using System;
using System.Speech.Recognition;

class VoiceStt {
    static int Main(string[] args) {
        try {
            if (args.Length >= 1 && args[0] == "-list") {
                foreach (var r in SpeechRecognitionEngine.InstalledRecognizers())
                    Console.Out.Write(r.Culture.Name + " | " + r.Description + "\n");
                return 0;
            }
            if (args.Length < 1) { Console.Error.Write("usage: voice-stt <wav> [out.txt] [culturePrefix]"); return 2; }
            string wav = System.IO.Path.GetFullPath(args[0]);
            if (!System.IO.File.Exists(wav)) { Console.Error.Write("找不到音频: " + wav); return 1; }
            string prefix = args.Length > 2 && args[2].Length > 0 ? args[2] : "zh";

            // 用 InstalledRecognizers() 里真实的 RecognizerInfo 建引擎：
            // 直接用 new CultureInfo("zh-CN") 建会报"找不到具有所需 ID 的识别器"（本机实测），
            // 因为系统里那套引擎的 culture 名未必与传入字符串逐字相同。
            RecognizerInfo info = null;
            foreach (var r in SpeechRecognitionEngine.InstalledRecognizers()) {
                if (r.Culture.Name.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) { info = r; break; }
            }
            if (info == null) { Console.Error.Write("本机没有 " + prefix + " 的语音识别引擎"); return 1; }

            var eng = new SpeechRecognitionEngine(info);
            eng.SetInputToWaveFile(wav);
            eng.LoadGrammar(new DictationGrammar());

            var sb = new System.Text.StringBuilder();
            RecognitionResult r0 = eng.Recognize();          // 第一次不带超时：它才会读完整段
            int guard = 0;
            while (r0 != null && guard++ < 20) {             // 之后给 1 秒超时，避免在 EOF 上死等
                sb.Append(r0.Text);
                r0 = eng.Recognize(TimeSpan.FromSeconds(1));
            }
            eng.Dispose();

            string text = sb.ToString().Trim();
            if (text.Length == 0) { Console.Error.Write("没识别出文字（录音太短/太轻，或不是 16kHz 单声道 PCM WAV）"); return 1; }
            if (args.Length > 1 && args[1].Length > 0)
                System.IO.File.WriteAllText(args[1], text, new System.Text.UTF8Encoding(false));
            Console.Out.Write(text);
            return 0;
        } catch (Exception e) { Console.Error.Write(e.Message); return 1; }
    }
}