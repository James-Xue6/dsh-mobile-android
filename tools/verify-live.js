#!/usr/bin/env node
/**
 * 发版后的**线上核验**：App 的「检查更新」到底能不能拿到这一版。
 *
 * 为什么必须有这一步：本地构建成功 ≠ 用户能更新 ✓ 更新链路上还有三处会失败 ——
 *   ① 清单没推上去（或推到了 tag 但 main 分支没更新 ✗ App 读的是 main ✗）
 *   ② 清单被写坏（说明里混了英文引号，url 字段被挤占 ✗）
 *   ③ APK 传坏了 / 传的是上一版（sha256 对不上 ✗ App 会中止安装 ✗）
 * 这里把这三件事一次性验完：拉清单 → 拉清单里写的那个 APK → 逐字节算 sha256 比对。
 *
 * 用法（仓库根目录，发版之后）：
 *     node tools/verify-live.js
 *
 * 说明：用 Node 自带的 https（OpenSSL + 自带 CA）。**不要**改用 PowerShell 的
 * Invoke-WebRequest / curl：在 DSH 沙箱里它们会报「schannel 没有可用凭证」，
 * 看起来像"线上挂了"，其实是环境问题（2026-10-06 踩过）。
 */
const https = require('https');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

/** 必须与 MainActivity.UPDATE_MANIFEST_GITEE 一致（App 实际拉的就是它）。 */
// [2026-10-07 合并适配] 改成我们自己的 Gitee 镜像（原文件写的是朋友那个 fork）
const MANIFEST = 'https://gitee.com/yuan-junqian/dsh-mobile-android/raw/main/dist/version.json';
const UA = 'DSH-Mobile/verify-live';

function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 6) return reject(new Error('重定向太多：' + url));
    https.get(url, { headers: { 'User-Agent': UA } }, (r) => {
      if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) {
        r.resume();
        const next = new URL(r.headers.location, url).toString();
        return get(next, redirects + 1).then(resolve, reject);
      }
      const chunks = [];
      r.on('data', (c) => chunks.push(c));
      r.on('end', () => resolve({ code: r.statusCode, body: Buffer.concat(chunks), url }));
    }).on('error', reject);
  });
}

function sha256(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}

function fail(msg) {
  console.error('\n[核验失败] ' + msg);
  process.exit(1);
}

(async () => {
  const local = path.join(__dirname, '..', 'dist', 'version.json');
  const want = fs.existsSync(local) ? JSON.parse(fs.readFileSync(local, 'utf8')) : null;

  console.log('清单地址（App 读的就是这条）：' + MANIFEST);
  const m = await get(MANIFEST);
  if (m.code !== 200) fail('清单 HTTP ' + m.code);
  let j;
  try {
    j = JSON.parse(m.body.toString('utf8'));
  } catch (e) {
    fail('清单不是合法 JSON（八成是更新说明里混了英文引号）：' + e.message);
  }
  console.log('  线上版本：versionCode=' + j.versionCode + '  versionName=' + j.versionName);
  console.log('  更新说明：' + j.notes);
  if (!j.url || !/^https?:\/\//.test(j.url)) fail('清单里的 url 不是合法网址：' + j.url);
  if (!j.sha256) fail('清单里没有 sha256');

  if (want && want.versionCode !== j.versionCode) {
    console.log('  [!] 本地 dist/version.json 是 ' + want.versionCode + '，线上是 ' + j.versionCode
      + '（本地还没重跑过发版脚本？）');
  }

  // 主线路（tag）与备用线路（main）都要能下、且 sha256 一致 —— App 下载失败时会切备用
  const targets = [['主线路', j.url]];
  if (j.mirror && j.mirror !== j.url) targets.push(['备用线路', j.mirror]);
  for (const [name, url] of targets) {
    const a = await get(url);
    if (a.code !== 200) fail(name + ' APK HTTP ' + a.code + '：' + url);
    const h = sha256(a.body);
    const ok = h === j.sha256;
    console.log('  ' + name + ' ' + (a.body.length / 1024).toFixed(1) + ' KB  sha256=' + h.slice(0, 16) + '…  '
      + (ok ? '与清单一致 ✓' : '与清单不一致 ✗'));
    if (!ok) fail(name + ' 的 sha256 与清单不符（这份包 App 装不上 / 甚至可能是旧包）');
  }
  console.log('\n[核验通过] 清单合法、两条线路都能下到、sha256 与清单一致 —— 用户可以更新到 '
    + j.versionName + ' ✓');
})().catch((e) => fail(e.message));
