# dsh-mobile-access（手机接入）

在 DSH 里直接管理手机端接入：**一键开关移动网关、生成配对二维码与访问令牌、查看并撤销已配对设备**。

## 它做什么 / 不做什么

| | |
|---|---|
| **做** | 设置 → 通用 →「手机接入」面板；调用网关管理接口做接入编排与展示 |
| **不做** | 不重写任何协议。WebSocket 网关、`dsh-mobile-v1` 协议、配对鉴权、会话同步全部由 **`dsh-plugin-mobile-gateway`** 提供 |

架构：本插件是**薄面板**。

```
浏览器面板 (client.js)
   │  fetch /dsh-mobile-access/*
   ▼
宿主代理 (index.js)                     ← 以 loopback 身份转发，鉴权边界留在宿主
   │  http://127.0.0.1:<webPort>/mgw/*
   ▼
dsh-plugin-mobile-gateway               ← 协议层（第三方，MIT）
   │
   ▼
DSH Host 0.2.0-rc.2
```

用宿主代理而不是让浏览器直连 `/mgw/*` 的原因：`/mgw` 带 `adminLoopbackOnly` +
`isSameOrigin` 校验，桌面端渲染进程的来源地址并不稳定；由宿主以 127.0.0.1 发起最稳。

## 安装

1. 确保 `dsh-plugin-mobile-gateway` 已装进同一个 profile（本插件依赖它的 `/mgw` 接口）。
2. 在 profile 的 `package.json` 里把本插件加成 `link:` 依赖，并加入 `dsh.profile.bundles`：

```json
{
  "dependencies": {
    "dsh-mobile-access": "link:C:/Users/Administrator/.dsh/local-plugins/dsh-mobile-access"
  },
  "dsh": {
    "profile": {
      "bundles": ["...", "dsh-mobile-access"]
    }
  }
}
```

3. 重启 DSH（客户端插件在启动时加载）。

## 接口（宿主代理）

| 方法 | 路径 | 转发到 |
|---|---|---|
| GET | `/dsh-mobile-access/status` | `/mgw/status` |
| GET | `/dsh-mobile-access/devices` | `/mgw/devices` |
| POST | `/dsh-mobile-access/gateway` | `/mgw/gateway`（`{"mode":"persistent\|temporary\|disabled"}`） |
| POST | `/dsh-mobile-access/pair` | `/mgw/pair`（`{"name","publicUrl"}`） |
| POST | `/dsh-mobile-access/devices/<id>/revoke` | `/mgw/devices/<id>/revoke` |

仅允许本机（loopback）访问；网关不可达时返回 `502 gateway-unavailable` 并给出中文原因。

## 注意

- 二维码**一次性、5 分钟有效**；过期重新生成即可。
- 局域网是明文 `ws://`，只允许私有网段；公网请用自己的反向代理转成 `wss://`。
- 撤销设备会立即断开该设备的连接，App 需要重新扫码配对。
