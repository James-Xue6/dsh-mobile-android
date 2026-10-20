# 第三方代码声明（THIRD-PARTY-NOTICES）

本插件 `dsh-mobile-access` 的 `vendor/gateway/` 目录包含第三方代码，来源如下：

| 项目 | 版本基线 | 许可证 | 版权 |
|---|---|---|---|
| [dsh-plugin-mobile-gateway](https://github.com/Clarklevis1995/dsh-plugin-mobile-gateway) | 0.9.0 + 本仓库 fork 补丁 | MIT | Copyright (c) 2026 Clarklevis1995 |

- 许可证原文见 `vendor/gateway/LICENSE`（MIT 要求随附版权与许可声明，故原样保留）。
- 协议说明见 `vendor/gateway/PROTOCOL.md`。
- 本仓库对该代码做过的修改（便于日后与上游对齐时核对）：
  1. `DSH 0.2.1-rc.2` 的 Connection Peer 支持（`index.mjs` 创建 peer、`dsh-host-adapter.mjs` 全部出口透传）。
  2. 跨会话提醒、`hello` 帧带公网地址、地址变化广播、通用文件附件、子智能体寻址（原为 install.ps1 的补丁，已并入 vendor 基线）。
  3. 面板 UI：居中弹窗、去掉与「手机接入」卡片重复的旧高级设置。
- 上游更新（例如 0.10.x）**不会自动跟随**：需要人工把 `vendor/gateway/` 与上游 diff 后重新对齐，
  再把本仓库上述改动叠回去。对齐时优先看 `PROTOCOL.md` 与 `CHANGELOG`（如上游提供）。
