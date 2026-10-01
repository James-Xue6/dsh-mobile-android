// 面板 client.js 的静态校验：找出「用了但没定义」的 state setter。
//
// 为什么需要它：client.js 是手写的 React.createElement，state 是
//   var xState = React.useState(init)
//   var x = xState[0]
//   var setX = xState[1]
// 这种模式。少写一行 accessor 时**语法依然正确**（node --check 过得去），
// 但渲染期会抛 ReferenceError，导致整个面板从设置里消失 —— 排查起来很费时。
// 这个脚本把这类错误在提交前拦住。
//
// 用法：node pc-plugin\lint-panel.cjs [file ...]
const fs = require('fs')
const path = require('path')

const GLOBALS = new Set(['setInterval', 'setTimeout', 'setImmediate', 'setYear'])

const defaultFiles = [
  path.join(__dirname, 'dsh-mobile-access', 'client.js'),
  path.join(process.env.USERPROFILE || '', '.dsh', 'local-plugins', 'dsh-mobile-access', 'client.js'),
]

function lint(file) {
  let src
  try { src = fs.readFileSync(file, 'utf8') } catch { return null }
  const declared = new Set()
  // var setX = xState[1]
  for (const m of src.matchAll(/var\s+(set[A-Z]\w*)\s*=\s*\w+\[1\]/g)) declared.add(m[1])
  // var [x, setX] = React.useState(...)  /  const [x, setX] = useState(...)
  for (const m of src.matchAll(/[[(]\s*\w+\s*,\s*(set[A-Z]\w*)\s*[\])]/g)) declared.add(m[1])
  // function setX(
  for (const m of src.matchAll(/function\s+(set[A-Z]\w*)\s*\(/g)) declared.add(m[1])

  const used = new Set()
  for (const m of src.matchAll(/(?:^|[^.\w])(set[A-Z]\w*)\s*\(/g)) used.add(m[1])

  const missing = [...used].filter((n) => !declared.has(n) && !GLOBALS.has(n)).sort()
  return { declared: declared.size, used: used.size, missing }
}

const files = process.argv.length > 2 ? process.argv.slice(2) : defaultFiles
let failed = false
for (const file of files) {
  const r = lint(file)
  if (!r) { console.log('跳过（不存在或读不到）: ' + file); continue }
  console.log(path.basename(path.dirname(file)) + '/' + path.basename(file) + '  setter 声明 ' + r.declared + ' / 使用 ' + r.used)
  if (r.missing.length) {
    failed = true
    console.log('  ❌ 用了但没定义: ' + r.missing.join(', '))
  } else {
    console.log('  ✅ 所有 setter 都有定义')
  }
}
process.exit(failed ? 1 : 0)
