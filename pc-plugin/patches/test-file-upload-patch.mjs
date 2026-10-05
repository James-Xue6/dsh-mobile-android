// 网关「通用文件附件」补丁的逻辑验证（不连真宿主）。
//
// 前置：由 run-file-upload-patch-test.ps1 在沙箱里准备好
//   <sandbox>/node_modules/dsh-plugin-mobile-gateway/lib/{index.mjs,dsh-host-adapter.mjs}
// 即"已打过补丁"的两份文件；本文件只做断言。
//
// 覆盖：
//   A. 适配器 uploadFile() 发出的 namespace/method/args 形状
//   B. admitMessage() 的 files[] → receipt → prompt content 全流程与失败码
//
// 用法：node test-file-upload-patch.mjs <sandboxRoot>

import { pathToFileURL } from 'node:url'
import path from 'node:path'

const sandbox = process.argv[2]
if (!sandbox) {
  console.error('用法: node test-file-upload-patch.mjs <sandboxRoot>')
  process.exit(2)
}

const libDir = path.join(sandbox, 'node_modules', 'dsh-plugin-mobile-gateway', 'lib')
const indexUrl = pathToFileURL(path.join(libDir, 'index.mjs')).href
const adapterUrl = pathToFileURL(path.join(libDir, 'dsh-host-adapter.mjs')).href

const { admitMessage } = await import(indexUrl)
const { createDshHostAdapter } = await import(adapterUrl)

let pass = 0, fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS  ' + name) }
  else { fail++; console.log('  FAIL  ' + name + (extra === undefined ? '' : '  ' + JSON.stringify(extra))) }
}

// ---------------------------------------------------------------- A. 适配器
console.log('\n[A] Host Adapter uploadFile()')
{
  const seen = []
  const api = createDshHostAdapter({
    invoke: async (req) => { seen.push(req); return { receiptId: 'rec-9', file: { attachmentId: 'a', name: 'n', bytes: 3 } } },
    stream: () => { throw new Error('not used') },
  })

  check('A1 顶层导出 uploadFile（不是 sessions.uploadFile）', typeof api.uploadFile === 'function')
  check('A1 sessions 上没有 uploadFile（防止落错层级）', api.sessions && api.sessions.uploadFile === undefined)

  await api.uploadFile('sess-1', 'QUJD', 'a.pdf', undefined)
  check('A2 namespace/method', seen[0].namespace === 'fileUploads' && seen[0].method === 'upload', seen[0])
  check('A2 args.agentId 是纯字符串 sessionId', seen[0].args.agentId === 'sess-1', seen[0].args)
  check('A2 args.request={data,name}', JSON.stringify(seen[0].args.request) === JSON.stringify({ data: 'QUJD', name: 'a.pdf' }), seen[0].args)

  seen.length = 0
  await api.uploadFile('sess-2', 'QUJD', undefined, undefined)
  check('A3 name 省略', !('name' in seen[0].args.request) && seen[0].args.request.data === 'QUJD', seen[0].args)

  seen.length = 0
  await api.uploadFile('sess-3', 'QUJD', '', undefined)
  check('A4 name 空串也省略', !('name' in seen[0].args.request), seen[0].args)

  seen.length = 0
  const ac = new AbortController()
  await api.uploadFile('sess-4', 'QUJD', 'x', ac.signal)
  check('A5 signal 透传', seen[0].signal === ac.signal, Object.keys(seen[0]))
}

// ---------------------------------------------------------------- B. admitMessage
console.log('\n[B] admitMessage files[] 流程')
function mockApi(overrides = {}) {
  const calls = { upload: [], prompt: [], create: [] }
  const api = {
    sessions: {
      create: async (p) => { calls.create.push(p); return { sessionId: 'sess-new' } },
      prompt: async (p) => { calls.prompt.push(p); return { accepted: true } },
    },
    uploadFile: async (agentId, data, name) => {
      calls.upload.push({ agentId, data, name })
      return { receiptId: 'rec-' + calls.upload.length, file: { attachmentId: 'att', name: name || 'f', bytes: data.length } }
    },
    ...overrides,
  }
  return { api, calls }
}

{
  const { api, calls } = mockApi()
  const r = await admitMessage(api, { sessionId: 'sess-1', text: '看下这个', files: [{ data: 'QUJD', name: 'a.pdf' }] })
  check('B1 kind=sent', r.kind === 'sent', r)
  check('B1 uploadFile 参数', calls.upload.length === 1 && calls.upload[0].agentId === 'sess-1'
    && calls.upload[0].data === 'QUJD' && calls.upload[0].name === 'a.pdf', calls.upload)
  check('B1 content=[file,text]', JSON.stringify(calls.prompt[0].content)
    === JSON.stringify([{ type: 'file', receiptId: 'rec-1' }, { type: 'text', text: '看下这个' }]), calls.prompt[0].content)
}
{
  const { api, calls } = mockApi()
  const r = await admitMessage(api, { sessionId: 'sess-1', files: [{ data: 'QUJD' }] })
  check('B2 仅文件 kind=sent', r.kind === 'sent', r)
  check('B2 name 省略', calls.upload[0].name === undefined, calls.upload[0])
  check('B2 content 仅 file', JSON.stringify(calls.prompt[0].content)
    === JSON.stringify([{ type: 'file', receiptId: 'rec-1' }]), calls.prompt[0].content)
}
{
  const { api, calls } = mockApi()
  await admitMessage(api, {
    sessionId: 'sess-1', text: 't',
    images: [{ mediaType: 'image/png', data: 'aW1n' }],
    files: [{ data: 'QUJD', name: 'a.bin' }],
  })
  const c = calls.prompt[0].content
  check('B3 顺序 image→file→text', c[0].type === 'image' && c[1].type === 'file' && c[2].type === 'text', c)
}
{
  const { api, calls } = mockApi()
  const r = await admitMessage(api, { sessionId: 'sess-1', text: 'hi' })
  check('B4 无附件回归 kind=sent', r.kind === 'sent', r)
  check('B4 未调用 uploadFile', calls.upload.length === 0, calls.upload)
  check('B4 content 仅 text', JSON.stringify(calls.prompt[0].content)
    === JSON.stringify([{ type: 'text', text: 'hi' }]), calls.prompt[0].content)
}
{
  const { api } = mockApi()
  const r = await admitMessage(api, { sessionId: 'sess-1', text: 'x', files: 'nope' })
  check('B5 files 非数组', r.kind === 'error' && r.code === 'bad-request' && /files must be an array/.test(r.message), r)
}
{
  const { api } = mockApi()
  const many = Array.from({ length: 21 }, () => ({ data: 'QQ==' }))
  const r = await admitMessage(api, { sessionId: 'sess-1', text: 'x', files: many })
  check('B6 超 20 项', r.kind === 'error' && r.code === 'bad-request' && /at most 20 files/.test(r.message), r)
}
{
  const { api, calls } = mockApi()
  const r = await admitMessage(api, { sessionId: 'sess-1', files: [{ name: 'a.pdf' }] })
  check('B7 data 缺失', r.kind === 'error' && r.code === 'bad-request' && /\.data must be a non-empty base64/.test(r.message), r)
  check('B7 未调用 uploadFile', calls.upload.length === 0, calls.upload)
}
{
  const { api } = mockApi()
  const r = await admitMessage(api, { sessionId: 'sess-1' })
  check('B8 三者全空', r.kind === 'error' && r.code === 'bad-request' && /non-empty text/.test(r.message), r)
}
{
  const { api } = mockApi({ uploadFile: async () => { const e = new Error('too big'); e.code = 'file-too-large'; throw e } })
  const r = await admitMessage(api, { sessionId: 'sess-1', files: [{ data: 'QUJD' }] })
  check('B9 失败码透传', r.kind === 'error' && r.code === 'file-too-large' && r.message === 'too big', r)
}
{
  const { api, calls } = mockApi()
  const r = await admitMessage(api, { text: '新会话带文件', files: [{ data: 'QUJD', name: 'n.pdf' }] })
  check('B10 新会话 kind=sent', r.kind === 'sent', r)
  check('B10 先 create 后 upload', calls.create.length === 1 && calls.upload[0].agentId === 'sess-new', { create: calls.create, upload: calls.upload })
  check('B10 prompt 用新 sessionId', calls.prompt[0].sessionId === 'sess-new', calls.prompt[0])
}
{
  const { api } = mockApi({ uploadFile: async () => ({ nope: true }) })
  const r = await admitMessage(api, { sessionId: 'sess-1', files: [{ data: 'QUJD' }] })
  check('B11 非法 receipt 报错', r.kind === 'error' && r.code === 'internal' && /invalid receipt/.test(r.message), r)
}

console.log('\nPASS=' + pass + '  FAIL=' + fail)
process.exit(fail === 0 ? 0 : 1)
