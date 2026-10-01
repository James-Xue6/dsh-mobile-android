// tools/e2e-assert.mjs —— 逐档「可判定结论」分析器
//
// 读 tools/logs/last-run.json（最近一次 run-e2e 的档位→runDir/exit 映射），
// 再从每个 runDir 里取两份原始证据：
//   harness.log         —— App 侧状态机（net 层回调）
//   mock-gateway.jsonl  —— mock 侧记录的真实帧（含 Close 帧方向/码/原因）
// 输出：每档「期望 / 实测 / 判定」表 + 关键证据行。
//
// 用法: node tools/e2e-assert.mjs [--fault a,b,c]
// 注意：断言是按任务书给的验收口径写死的，不允许为了让测试通过而放松。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const arg = (k, d) => { const i = process.argv.indexOf('--' + k); return i > 0 ? process.argv[i + 1] : d; };

const lastRunPath = path.join(ROOT, 'tools', 'logs', 'last-run.json');
if (!fs.existsSync(lastRunPath)) { console.error('缺少 ' + lastRunPath + '，先跑 node tools/run-e2e.mjs'); process.exit(2); }
const lastRun = JSON.parse(fs.readFileSync(lastRunPath, 'utf8'));
const only = arg('fault', null);
const faults = (only ? only.split(',') : lastRun.map(r => r.fault));

const C = { ok: '✅', bad: '❌', warn: '⚠️' };

function parseHarness(text) {
  const lines = [];
  for (const raw of text.split(/\r?\n/)) {
    const m = /^\[(\d+)ms\] (.*)$/.exec(raw);
    if (m) lines.push({ ms: Number(m[1]), text: m[2] });
    else if (raw.trim()) lines.push({ ms: null, text: raw });
  }
  return lines;
}

function readJsonl(p) {
  if (!fs.existsSync(p)) return [];
  return fs.readFileSync(p, 'utf8').split(/\r?\n/).filter(Boolean).map(l => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean);
}

function analyze(fault, runDir, exitCode) {
  const hsText = fs.existsSync(path.join(runDir, 'harness.log')) ? fs.readFileSync(path.join(runDir, 'harness.log'), 'utf8') : '';
  const hs = parseHarness(hsText);
  const js = readJsonl(path.join(runDir, 'mock-gateway.jsonl'));

  const f = {
    fault, runDir, exitCode, hs, js,
    states: hs.filter(l => /^state=/.test(l.text)).map(l => ({ ms: l.ms, text: l.text })),
    reconnects: hs.filter(l => /^>> 安排重连/.test(l.text)).map(l => ({ ms: l.ms, text: l.text })),
    delays: [], // scheduleReconnect 打印的 "Ns 后重试"
    resets: hs.filter(l => /STREAM-RESET/.test(l.text)).map(l => ({ ms: l.ms, text: l.text })),
    hellos: hs.filter(l => /^HELLO#/.test(l.text)).map(l => ({ ms: l.ms, text: l.text })),
    inboundCloses: js.filter(o => o.dir === 'in' && o.close).map(o => o.close),
    outboundCloses: js.filter(o => o.dir === 'out' && o.close).map(o => o.close),
    upgrades: js.filter(o => o.dir === 'upgrade').length,
    rejects: js.filter(o => o.dir === 'reject').map(o => o.code + ' ' + o.msg),
    outResets: js.filter(o => o.dir === 'out' && o.frame && o.frame.kind === 'session-stream-reset').map(o => o.frame),
    inFrames: js.filter(o => o.dir === 'in' && o.frame).map(o => o.frame),
  };
  for (const l of f.states) {
    const m = /(\d+)s 后重试/.exec(l.text);
    if (m) f.delays.push({ ms: l.ms, delayS: Number(m[1]) });
  }
  const num = (re) => { const m = re.exec(hsText); return m ? Number(m[1]) : null; };
  f.protocolErrors = num(/协议错误\s*:\s*(\d+)/);
  f.helloCount = num(/hello 次数\s*:\s*(\d+)/);
  f.chunkFrames = num(/assistant-stream 增量帧\s*:\s*(\d+)/);
  f.approvals = num(/审批请求\s*:\s*(\d+)/);
  f.questions = num(/提问请求\s*:\s*(\d+)/);
  f.eventCount = num(/持久 event 帧\s*:\s*(\d+)/);
  const st = /流式文本片段\s*:\s*(.*)$/m.exec(hsText);
  f.streamText = st ? st[1].trim() : '';
  const cp = /capabilities: (.*)$/m.exec(hsText);
  f.capabilities = cp ? cp[1].trim() : '';
  f.helloProtocols = f.hellos.map(h => { const m = /protocol=(\d+)/.exec(h.text); return m ? Number(m[1]) : null; });
  f.has = (re) => re.test(hsText);
  return f;
}

// ---------- 断言 ----------
const chk = (name, ok, detail) => ({ name, ok, detail });
const between = (v, lo, hi) => v !== null && v >= lo && v <= hi;

const has1006 = (f) => f.inboundCloses.some(c => c.code === 1006) || f.outboundCloses.some(c => c.code === 1006);

function analyzeNone(f) {
  const r = [];
  r.push(chk('exit=0（harness 自身判定无协议错误）', f.exitCode === 0, 'exit=' + f.exitCode));
  r.push(chk('配对拿到长期 token', /PAIRED 取得长期 token=mock-dev/.test(f.hs.map(l => l.text).join('\n')), ''));
  r.push(chk('token 重连后第二次 hello（protocol=3）', f.helloProtocols[1] === 3, 'protocols=' + JSON.stringify(f.helloProtocols)));
  r.push(chk('订阅 assistantStream=true', f.has(/SUBSCRIBED .*assistantStream=true/), ''));
  r.push(chk('流式 chunk ≥4 且 end(committed)', (f.chunkFrames || 0) >= 4 && f.has(/流帧 end outcome=\{"kind":"committed"\}/), 'chunkFrames=' + f.chunkFrames));
  r.push(chk('审批请求 1 且已回 outcome', f.approvals === 1 && f.has(/提交 approval-response outcome=allowed-once/), 'approvals=' + f.approvals));
  r.push(chk('提问请求 1 且已回 selected=[A]', f.questions === 1 && f.has(/提交 question-answer.*selected=\[A\]/), 'questions=' + f.questions));
  r.push(chk('文件下载走完 + sha256 校验通过', f.has(/DOWNLOAD 完成 共 208 块 \/ 1664 字节 · sha256 校验=通过/), ''));
  r.push(chk('收到 turn/end 持久事件', f.has(/event\[4\] turn\/end/), 'eventCount=' + f.eventCount));
  r.push(chk('协议错误 = 0', f.protocolErrors === 0, 'errors=' + f.protocolErrors));
  r.push(chk('Close 帧无 1006（全部 1000）', !has1006(f) && f.inboundCloses.every(c => c.code === 1000), 'inbound=' + JSON.stringify(f.inboundCloses)));
  return r;
}

function analyzeSilent(f) {
  const r = [];
  const stale = f.states.find(l => /连接已失效，正在重连/.test(l.text));
  // 用「失效判定之前的最后一次 hello」算时延 —— 判死之后还会重连并产生新 hello，
  // 取数组末尾会得到负的 Δ（分析器自身的 bug，已修）。
  const hello = stale ? f.hellos.filter(h => h.ms <= stale.ms).pop() : null;
  const dt = stale && hello ? stale.ms - hello.ms : null;
  r.push(chk('触发「连接已失效，正在重连」', !!stale, stale ? stale.text : '(未触发)'));
  r.push(chk('触发时延 ∈ [70s,85s]（≈75s 判死）', between(dt, 70000, 85000), 'Δ=' + (dt === null ? 'n/a' : (dt / 1000).toFixed(3) + 's')));
  r.push(chk('客户端 Close 帧 = 1011', f.inboundCloses.some(c => c.code === 1011), JSON.stringify(f.inboundCloses)));
  r.push(chk('Close 帧无 1006', !has1006(f), ''));
  r.push(chk('随后自动重连成功（拿到新 hello）', f.reconnects.length >= 1 && f.helloCount >= 3, 'reconnects=' + f.reconnects.length + ' hello=' + f.helloCount));
  return r;
}

function analyzeNohello(f) {
  const r = [];
  const to = f.states.find(l => /握手超时/.test(l.text));
  const auth = f.states.find(l => /AUTHENTICATING/.test(l.text));
  const dt = to && auth ? to.ms - auth.ms : null;
  r.push(chk('触发「握手超时」', !!to, to ? to.text : '(未触发)'));
  r.push(chk('握手超时时延 ∈ [8s,13s]（看门狗 10s）', between(dt, 8000, 13000), 'Δ=' + (dt === null ? 'n/a' : (dt / 1000).toFixed(2) + 's')));
  r.push(chk('客户端 Close 帧 = 1000（不是 1006）', f.inboundCloses.some(c => c.code === 1000), JSON.stringify(f.inboundCloses)));
  r.push(chk('Close 帧无 1006', !has1006(f), ''));
  const delays = f.delays.map(d => d.delayS);
  const inc = delays.length >= 3 && delays.every((v, i) => i === 0 || v >= delays[i - 1]) && delays[delays.length - 1] > delays[0];
  r.push(chk('重连退避递增', inc, 'delays=' + JSON.stringify(delays)));
  return r;
}

function analyzeEmptyclose(f) {
  const r = [];
  const delays = f.delays.map(d => d.delayS);
  r.push(chk('40s 内发生多次重连（≥3）', f.reconnects.length >= 3, 'reconnects=' + f.reconnects.length));
  r.push(chk('退避递增（非恒定 1.6s）', delays.length >= 3 && delays[delays.length - 1] > delays[0] && delays.every((v, i) => i === 0 || v >= delays[i - 1]), 'delays=' + JSON.stringify(delays)));
  r.push(chk('退避出现明显放大（末次 ≥ 6s）', delays.length > 0 && delays[delays.length - 1] >= 6, 'max=' + (delays.length ? delays[delays.length - 1] : 'n/a') + 's'));
  r.push(chk('Close 帧无 1006', !has1006(f), JSON.stringify(f.inboundCloses)));
  return r;
}

function analyzeReset(f, expectRetry) {
  const r = [];
  const l = f.resets[0];
  const got = l ? /retrying=(true|false)/.exec(l.text) : null;
  r.push(chk('收到 session-stream-reset', !!l, l ? l.text : '(未收到)'));
  if (expectRetry !== null) {
    r.push(chk('retrying 透传 = ' + expectRetry, !!got && got[1] === String(expectRetry), got ? 'retrying=' + got[1] : '(无)'));
  } else {
    r.push(chk('retrying 缺省(false) 兼容路径', !!got && got[1] === 'false', got ? 'retrying=' + got[1] : '(无)'));
  }
  r.push(chk('断流前的输出未丢（net 层已交付"半个字"）', /半个字/.test(f.streamText), 'streamText=' + f.streamText));
  r.push(chk('协议错误 = 0', f.protocolErrors === 0, 'errors=' + f.protocolErrors));
  r.push(chk('未崩溃（仍打印出结果统计）', f.exitCode === 0 && /联调结果/.test(f.hs.map(x => x.text).join('\n')), 'exit=' + f.exitCode));
  if (expectRetry === true) {
    r.push(chk('retrying=true 后 follower 续流: a2 的 chunk 到达', /（断流后）流帧 #\d+ chunk attemptId=a2/.test(f.hs.map(x => x.text).join('\n')), ''));
    r.push(chk('续流后全文重开且含两段', /半个字/.test(f.streamText) && /后半句/.test(f.streamText), 'streamText=' + f.streamText));
  }
  if (expectRetry === false) {
    const cont = f.hs.filter(x => /（断流后）/.test(x.text));
    r.push(chk('retrying=false 后无续流帧（终态）', cont.length === 0, '续流帧数=' + cont.length));
  }
  r.push(chk('Close 帧无 1006', !has1006(f), JSON.stringify(f.inboundCloses)));
  return r;
}

function analyzeProto4(f) {
  const r = [];
  r.push(chk('hello 宣告 protocol=4（已被记录）', f.helloProtocols.includes(4), 'protocols=' + JSON.stringify(f.helloProtocols)));
  const rejected = f.rejects.length > 0 || (f.protocolErrors || 0) > 0 || f.has(/协议版本/);
  r.push(chk('[如实记录] 协议版本不匹配未被拒绝/未提示', !rejected, rejected ? '有拒绝/错误' : '当前实现照单全收'));
  r.push(chk('[期望] 应做协议版本校验 → 未实现', false, '未实现（如实记录，非本轮回归）'));
  return r;
}

function analyzeNocaps(f) {
  const r = [];
  r.push(chk('hello capabilities 被记录且仅剩 session-cancel', f.capabilities === 'session-cancel', 'caps=' + f.capabilities));
  const gated = f.has(/能力|capability|capabilities.*(不支持|隐藏)/);
  r.push(chk('[如实记录] 能力缺失未做入口收敛/提示', !gated, gated ? '有提示' : '当前实现照单全收'));
  r.push(chk('[期望] 应做能力校验 → 未实现', false, '未实现（如实记录，非本轮回归）'));
  return r;
}

function analyzeGatewayoff(f) {
  const r = [];
  r.push(chk('进入 GATEWAY_OFF 状态', f.has(/state=GATEWAY_OFF/), (f.states.find(l => /GATEWAY_OFF/.test(l.text)) || {}).text || '(无)'));
  // 旧报告的副作用：GATEWAY_OFF 之后被迟到的 onClosed 覆盖成 DISCONNECTED「已断开」。
  // P1-3 的 settled 去重应当已经堵住这条；这里做回归断言。
  const gi = f.states.findIndex(l => /GATEWAY_OFF/.test(l.text));
  const after = gi >= 0 ? f.states.slice(gi + 1) : [];
  const overwritten = after.some(l => /state=DISCONNECTED/.test(l.text));
  r.push(chk('GATEWAY_OFF 未被 DISCONNECTED 覆盖（P1-3 去重回归）', !overwritten,
    after.map(l => l.text).join(' | ') || '(其后无状态帧)'));
  r.push(chk('40s 内重连次数 ≤ 2（旧的无限 15s 重连已修）', f.reconnects.length <= 2, 'reconnects=' + f.reconnects.length));
  const okDelay = f.delays.length === 0 || f.delays.every(d => d.delayS >= 300);
  r.push(chk('重试间隔 ≥ 300s（503 冷启动自愈）', okDelay, 'delays=' + JSON.stringify(f.delays.map(d => d.delayS))));
  r.push(chk('Close 帧无 1006', !has1006(f), JSON.stringify(f.inboundCloses)));
  return r;
}

function analyzeServerClose(f, expectCode, expectState) {
  const r = [];
  r.push(chk('mock 侧记录到客户端回显 Close ' + expectCode, f.inboundCloses.some(c => c.code === expectCode),
    'inbound=' + JSON.stringify(f.inboundCloses)));
  r.push(chk('Close 帧无 1006', !has1006(f), ''));
  r.push(chk('进入 ' + expectState + ' 语义态（服务端关闭码被读到）', f.has(new RegExp('state=' + expectState)), 
    (f.states.find(l => new RegExp('state=' + expectState).test(l.text)) || {}).text || '(未进入)'));
  r.push(chk('停止重连（语义码处理生效，wantConnected=false）', f.reconnects.length === 0,
    'reconnects=' + f.reconnects.length + ' ' + JSON.stringify(f.reconnects.map(x => x.text))));
  return r;
}

const ANALYZERS = {
  none: analyzeNone, silent: analyzeSilent, nohello: analyzeNohello, emptyclose: analyzeEmptyclose,
  resetstream: (f) => analyzeReset(f, null), retrytrue: (f) => analyzeReset(f, true), retryfalse: (f) => analyzeReset(f, false),
  proto4: analyzeProto4, nocaps: analyzeNocaps, gatewayoff: analyzeGatewayoff,
  close4003: (f) => analyzeServerClose(f, 4003, 'UNAUTHORIZED'),
  close4004: (f) => analyzeServerClose(f, 4004, 'GATEWAY_OFF'),
};

let totalOk = 0, total = 0;
for (const fault of faults) {
  const meta = lastRun.find(r => r.fault === fault);
  if (!meta) { console.log('\n### ' + fault + '  ❌ 本次 last-run.json 里没有这一档'); continue; }
  const f = analyze(fault, meta.runDir, meta.exit);
  const checks = ANALYZERS[fault] ? ANALYZERS[fault](f) : [chk('无分析器', false, '')];
  const passed = checks.filter(c => c.ok).length;
  totalOk += passed; total += checks.length;
  console.log('\n' + '='.repeat(78));
  console.log('档位: ' + fault + '   runDir=' + path.relative(ROOT, meta.runDir) + '   exit=' + meta.exit
    + '   用时=' + (meta.elapsedMs / 1000).toFixed(1) + 's');
  console.log('  inboundClose=' + JSON.stringify(f.inboundCloses) + '  upgrades=' + f.upgrades
    + '  rejects=' + JSON.stringify(f.rejects));
  console.log('  fromMock(session-stream-reset 发帧)=' + JSON.stringify(f.outResets.map(x => ({ retrying: x.retrying }))));
  for (const c of checks) console.log('  ' + (c.ok ? C.ok : C.bad) + ' ' + c.name + (c.detail ? '   [' + c.detail + ']' : ''));
  console.log('  判定: ' + (passed === checks.length ? C.ok + ' 全过 (' + passed + '/' + checks.length + ')'
    : C.bad + ' 未全过 (' + passed + '/' + checks.length + ')'));
}
console.log('\n' + '='.repeat(78));
console.log('总计: ' + totalOk + '/' + total + ' 断言通过');
