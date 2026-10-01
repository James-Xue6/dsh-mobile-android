// tools/run-e2e.mjs —— 用 mock 网关驱动 JVM harness 跑端到端 / 故障注入
// 用法：
//   node tools/run-e2e.mjs                       # 跑全部 10 档（none + 9 故障）
//   node tools/run-e2e.mjs --fault silent --port 3191 --maxms 120000
//   node tools/run-e2e.mjs --classdir harness/out-ce7afd8   # 拿冻结快照做对照
// 用法说明：默认 classDir = harness/out（当前源码，见 harness/build.ps1 默认 -NetRoot src）。
// 产物：tools/runs/<fault>-<时间戳>/gateway.log / mock-gateway.jsonl / harness.log
//       tools/logs/<fault>.summary.txt
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const JAVA = process.env.HARNESS_JAVA ||
  'F:\\AI\\程序开发\\tools\\jdk-21.0.12.1+1\\bin\\java.exe';
const NODE = process.env.HARNESS_NODE || process.execPath;

const arg = (k, d) => { const i = process.argv.indexOf('--' + k); return i > 0 ? process.argv[i + 1] : d; };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

function openLog(p) { fs.mkdirSync(path.dirname(p), { recursive: true }); return fs.openSync(p, 'w'); }

async function runOne(fault, port, maxMs) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const runDir = path.join(ROOT, 'tools', 'runs', fault + '-' + stamp);
  fs.mkdirSync(runDir, { recursive: true });
  const gwLog = path.join(runDir, 'gateway.log');
  const hsLog = path.join(runDir, 'harness.log');

  const gw = spawn(NODE, [path.join(HERE, 'mock-gateway.mjs'), '--port', String(port), '--fault', fault], {
    cwd: runDir, stdio: ['ignore', openLog(gwLog), openLog(gwLog + '.err')], windowsHide: true,
  });

  // 等 PAIRING_STRING
  let pairing = null;
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    await sleep(120);
    if (fs.existsSync(gwLog)) {
      const txt = fs.readFileSync(gwLog, 'utf8');
      const m = txt.match(/PAIRING_STRING=(\S+)/);
      if (m) { pairing = m[1]; break; }
    }
  }
  if (!pairing) {
    try { gw.kill(); } catch { }
    return { fault, ok: false, error: 'mock gateway 未打印 PAIRING_STRING', runDir, gwLog };
  }

  const pairingFile = path.join(runDir, 'pairing.txt');
  fs.writeFileSync(pairingFile, pairing, 'ascii');   // 无换行，harness 会 trim

  // 默认用「当前源码」编出来的 harness/out（对应 harness/build.ps1 默认 -NetRoot src）。
  // 旧版硬编码 out-ce7afd8（冻结快照），会让所有档位测的是旧代码、结论无效。
  // 需要拿快照做对照时：--classdir harness/out-ce7afd8。
  const classDir = path.resolve(ROOT, arg('classdir', path.join('harness', 'out')));
  if (!fs.existsSync(path.join(classDir, 'Harness.class'))) {
    throw new Error('缺少 ' + classDir + '\\Harness.class —— 先跑 pwsh -File harness/build.ps1');
  }
  const jargs = ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
    '-Dharness.maxMs=' + maxMs,
    '-cp', [classDir, path.join(ROOT, 'harness', 'lib', 'json-20240303.jar')].join(path.delimiter),
    'Harness', pairingFile, '只回复四个字：联调成功', 'approve'];
  const hs = spawn(JAVA, jargs, {
    cwd: ROOT, stdio: ['ignore', openLog(hsLog), openLog(hsLog + '.err')], windowsHide: true,
  });

  const t0 = Date.now();
  const done = await new Promise(resolve => {
    hs.on('exit', (code) => resolve(code));
    setTimeout(() => { try { hs.kill(); } catch { } resolve('timeout-killed'); }, maxMs + 25000);
  });

  try { gw.kill(); } catch { }
  await sleep(300);
  return {
    fault, ok: true, runDir, classDir, elapsedMs: Date.now() - t0, exit: done,
    pairing, gwLog, hsLog,
    harness: fs.existsSync(hsLog) ? fs.readFileSync(hsLog, 'utf8') : '',
    gateway: fs.existsSync(gwLog) ? fs.readFileSync(gwLog, 'utf8') : '',
  };
}

const FAULTS = ['none', 'nohello', 'silent', 'emptyclose', 'resetstream', 'retrytrue', 'retryfalse',
  'proto4', 'nocaps', 'gatewayoff', 'close4003', 'close4004'];
const only = arg('fault', null);
// 注意：3091 在本机被 DSH 宿主进程占用（EADDRINUSE），默认改用 3191
const port = Number(arg('port', process.env.HARNESS_PORT || 3191));
const list = only ? only.split(',') : FAULTS;
const DEFAULT_MAX = {
  none: 40000, nohello: 45000, silent: 120000, emptyclose: 40000, resetstream: 40000,
  retrytrue: 40000, retryfalse: 40000, proto4: 40000, nocaps: 40000, gatewayoff: 40000,
  close4003: 40000, close4004: 40000,
};

const outDir = path.join(ROOT, 'tools', 'logs');
fs.mkdirSync(outDir, { recursive: true });

const report = [];
for (const f of list) {
  const maxMs = Number(arg('maxms', DEFAULT_MAX[f] || 40000));
  console.log('\n########## fault=' + f + '  port=' + port + '  maxMs=' + maxMs + ' ##########');
  const r = await runOne(f, port, maxMs);
  report.push(r);
  const summary = ['fault=' + f, 'runDir=' + r.runDir, 'classDir=' + (r.classDir || '(n/a)'),
    'exit=' + r.exit, 'elapsedMs=' + r.elapsedMs,
    r.error ? 'error=' + r.error : '', '--- harness ---', r.harness || '(no output)'].join('\n');
  fs.writeFileSync(path.join(outDir, f + '.summary.txt'), summary, 'utf8');
  console.log(r.error ? ('ERROR: ' + r.error) : (r.harness || '(no output)'));
  await sleep(600);
}
fs.writeFileSync(path.join(outDir, 'last-run.json'), JSON.stringify(report.map(r => ({
  fault: r.fault, exit: r.exit, elapsedMs: r.elapsedMs, runDir: r.runDir,
  classDir: r.classDir || null, error: r.error || null,
})), null, 2), 'utf8');
console.log('\n全部完成，摘要见 tools/logs/*.summary.txt');
