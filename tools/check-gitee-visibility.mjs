#!/usr/bin/env node
/**
 * Gitee 可见性体检：仓库里的文件是不是被平台内容审核拦了。
 *
 * 背景（2026-10-06 实测）：Gitee 判定某个文档违规后，只会
 *   ① 把仓库主页那一块换成「内容可能含有违规信息」（服务器端渲染，不是浏览器缓存）；
 *   ② 让 `raw` 取该文件返回 **451**（内容安全层 ADAS）。
 * 两者都**不影响**同仓库的其它文件 —— 所以「别的文件能下、就这个不能」时，
 * 别怀疑 push 或缓存，直接跑这个脚本看一眼。
 *
 * 判定要点（当初是怎么定位的）：
 *   · 换 ref 取同一个文件（老 tag / 老提交）：**内容没变而状态变了** → 不是"你这次改错了"；
 *   · 把同一份内容换个文件名推上去：**仍然 451** → 判的是"这篇文档本身"，不是文件名/位置；
 *   · 把文档切成几段分别放行：**全 200** → 不是某个词触发；
 *   · 整篇实质重写后再推：**200** → 结论：判的是"这篇文档"，重写就能恢复显示。
 *
 * 用法：
 *   node tools/check-gitee-visibility.mjs                    # 体检 main 上的常用文件
 *   node tools/check-gitee-visibility.mjs README.md README.en.md
 *   node tools/check-gitee-visibility.mjs --home             # 顺便看仓库主页是否显示违规提示
 */
import https from 'node:https';

// [2026-10-07 合并适配] 改成**我们自己的 Gitee 镜像**（原文件写的是朋友那个 fork 的仓库名）
const REPO = 'yuan-junqian/dsh-mobile-android';
const UA = 'Mozilla/5.0 (compatible; dsh-mobile-android/check-gitee-visibility)';
// 体检默认看这几个：都是本仓库 main 上真实存在的对外文件
const DEFAULT_FILES = ['README.md', 'README.en.md', 'CONTRIBUTING.md', 'NEXT-UPDATE.md', 'LICENSE'];

function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 6) return reject(new Error('重定向太多：' + url));
    https.get(url, { headers: { 'User-Agent': UA } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        return get(new URL(res.headers.location, url).toString(), redirects + 1).then(resolve, reject);
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ code: res.statusCode, body: Buffer.concat(chunks) }));
    }).on('error', reject);
  });
}

const args = process.argv.slice(2);
const wantHome = args.includes('--home');
const files = args.filter((a) => !a.startsWith('--'));
const list = files.length ? files : DEFAULT_FILES;

let blocked = 0;
console.log('仓库：' + REPO + '（main）');
for (const f of list) {
  const r = await get(`https://gitee.com/${REPO}/raw/main/${f}`);
  const mark = r.code === 200 ? '可见' : r.code === 451 ? '★ 被内容审核拦（451）' : 'HTTP ' + r.code;
  if (r.code !== 200) blocked++;
  console.log(`  ${String(r.code).padEnd(4)} ${f.padEnd(26)} ${mark}${r.code === 200 ? '  ' + r.body.length + ' B' : ''}`);
}

if (wantHome) {
  const home = await get('https://gitee.com/' + REPO);
  const html = home.body.toString('utf8');
  // 判据要精确到**渲染块**里那一句：仓库自己的文档里就引用过「内容可能含有违规信息」这几个字
  // （排查记录写进了 ORIGIN-AND-LICENSE.md），只按整页搜字符串会误报（2026-10-06 亲测误报一次）。
  const notice = html.includes('内容可能含有违规信息</p>');
  console.log('\n仓库主页：' + (notice ? '★ 显示「内容可能含有违规信息」—— README 被替换了' : '正常（没有违规提示）'));
  if (notice) blocked++;
}

console.log('\n' + (blocked === 0
  ? '[体检通过] 没有文件被拦。'
  : '[注意] 有 ' + blocked + ' 处被平台拦。若确认不是 push 问题：把整篇文档实质重写后再推（实测可恢复显示），或到 Gitee 官方反馈仓库 oschina/git-osc 提 Issue 申诉。'));
process.exit(blocked === 0 ? 0 : 1);
