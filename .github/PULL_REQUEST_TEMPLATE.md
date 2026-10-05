## 改了什么

<!-- 一两句话说清楚 -->

## 为什么要改

<!-- 解决了什么问题 / 哪个 Issue -->

## 怎么验证的

- [ ] `pwsh -File .\build.ps1` 通过（末尾看到"证书指纹与期望一致"）
- [ ] 动了 `src\com\dsh\mobile\net\` → `pwsh -File .\harness\run-protocol-v2-test.ps1` 全绿
- [ ] 动了 UI → `pwsh -File .\tools\verify-4features.ps1`（有设备时）
- [ ] 在真机 / 模拟器上实际点过，现象如下：

<!-- 贴截图或描述 -->

## 自查

- [ ] **没有**改 `AndroidManifest.xml` 里的 versionCode / versionName
- [ ] **没有**提交密钥、token、配对串
- [ ] **没有**新增第三方依赖
- [ ] 涉及的 DSH / 网关接口都在源码或 `docs/PROTO-FINDINGS.md` 里核实过（没有臆造）
- [ ] 若改了电脑端网关 → 补丁写成了 `pc-plugin/patches/` 下的**幂等脚本**（支持 `-Revert`）并挂进 `install.ps1`

## 没做 / 不确定的地方

<!-- 如实写。未验证的不要写成"已修复" -->
