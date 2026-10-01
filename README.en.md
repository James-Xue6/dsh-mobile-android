**English** | [简体中文](README.md)

# DSH Mobile for Android (DSH 掌上通)

Control **DeepSeek Harness** on your PC from your phone. A native Android client whose UI follows the shape of chat products like Doubao / Trae:
session list → bubble chat → real-time streaming output → inline approval / question cards; pair by QR code on the LAN, and fill in your reverse-proxy address for WAN access.

> **Current status**: 13 Android-side features have been verified on a real device; the PC access plugin (Cordis/DSH) has been tested on both host and panel;
> public access goes through the gateway's built-in Cloudflare quick tunnel (no port forwarding, no self-hosted reverse proxy). See the sections at the end of this document.
>
> **This repository contains no screenshots**: real-device verification screenshots contain sensitive information such as device tokens and LAN addresses,
> so they are kept locally only (`dist/*.png` is in .gitignore); the repository holds only source code and build artifacts.

---

## Quick start

**1. Install the PC plugin** (on your computer, from the repository root)

```powershell
git clone git@github.com:James-Xue6/dsh-mobile-android.git
cd dsh-mobile-android
pwsh -File .\pc-plugin\install.ps1
```

The script copies the plugin into `~/.dsh/local-plugins/`, drops the APK into the plugin directory,
and registers the dependency inside your DSH profile.

**2. Restart the DSH desktop app once**, then open `Settings → General → "Mobile Access"`.

**3. Install the app on your phone**: on the "Phone app installer" card, **with your phone on the same
Wi-Fi, scan the QR code to download and install**. The APK is served straight from your own computer
(local port 8099) — no third-party cloud drive or CDN involved.

> You can also skip the panel: download the APK straight from the CDN `https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@v0.1/dist/dsh-mobile.apk` or GitHub `https://github.com/James-Xue6/dsh-mobile-android/raw/v0.1/dist/dsh-mobile.apk` (the CDN is usually faster in mainland China).

**4. Pair by QR**: back in the panel, click "Generate pairing QR code" and scan it with the app —
**both the LAN and the public address are filled in at once**, so you can use the LAN at home and
switch to the public address when you go out, with no manual typing.

> The computer needs `dsh-plugin-mobile-gateway` (the protocol layer). The install script registers it
> automatically; if it is missing, install it once from the DSH plugin marketplace.

---
## 1. Why build a custom client

| Existing option | Form | Why not just use it |
|---|---|---|
| `dsh-pocket` | Reverse proxy + QR code, **moves the desktop Web UI to the phone as-is** | The desktop layout does not fit a phone screen and is awkward to operate |
| `dsh-im-connect` | IM bot bridge (WeChat / Feishu / QQ…) | Weak rich interaction; you cannot see tool traces or approval cards |
| `Clarklevis1995/dsh-mobile` | **Native iOS SwiftUI client** | iOS only; the Android client is a gap in the ecosystem |

Conclusion: **the host-side gateway does not need to be rewritten; the client does.**

## 2. Architecture

```
┌────────────────────┐        dsh-mobile-v1 (WebSocket + JSON)
│  Android App       │  ────────────────────────────────────────┐
│  com.dsh.mobile    │                                          │
│  · session list    │        ws://<PC-IP>:3091/ws/mobile       │
│  · streaming       │        wss://<your-domain>/ws/mobile     │
│  · approval cards  │                                          ▼
└────────────────────┘              ┌────────────────────────────────────┐
                                    │ dsh-plugin-mobile-gateway          │
                                    │ v0.9.0 (MIT, third-party, reused)  │
                                    │  · Device pairing / token auth     │
                                    │  · Session & live event bridge     │
                                    │  · Human-in-the-loop projection    │
                                    └──────────────┬─────────────────────┘
                                                   │ Host Adapter
                                                   ▼
                                    ┌────────────────────────────────────┐
                                    │ DSH Host 0.2.0-rc.2                │
                                    │ (desktop profile / Electron)       │
                                    └────────────────────────────────────┘
```

- The gateway plugin baseline is exactly **DSH 0.2.0-rc.2 / Session format 4**, matching the local version.
- The app only implements the protocol and never touches DSH internals: **zero reverse-engineering risk at the protocol layer**.
- The LAN path uses the local endpoint; for WAN you reverse-proxy it yourself to `wss://domain/ws/mobile` and type that into the app.

## 3. Directory layout

```
dsh-mobile-android/
├── AndroidManifest.xml
├── build.ps1                      # Gradle-free APK build script
├── dist/dsh-mobile.apk            # build artifact
├── libs/core-3.5.3.jar            # zxing (QR scanning), pure Java, no native
├── res/                           # minimal resource set (theme/icons/cleartext network policy)
├── src/com/dsh/mobile/
│   ├── MainActivity.java          # single Activity, three-screen routing + event merging
│   ├── Store.java                 # local persistence (address/token/device ID)
│   ├── model/{SessionInfo,ChatItem}.java
│   ├── net/WsClient.java          # hand-written RFC6455 WebSocket (no third-party deps)
│   ├── net/GatewayClient.java     # dsh-mobile-v1 protocol client
│   └── ui/{Ui,ChatAdapter,ConversationView,SessionListView,SettingsView,QrScanActivity}.java
└── harness/                       # JVM integration harness (reuses the net layer above)
```

## 4. Building

```powershell
pwsh -File .\build.ps1
```

Requirements: JDK 21, Android SDK (`build-tools;36.0.0` + `platforms;android-36`).

**Two known pitfalls, both handled inside the script:**

1. **Paths must not contain Chinese characters** — `aapt2`/`d8`/`zipalign` are native tools and cannot open non-ASCII paths on Windows with ANSI code page 936. The script stages the source into `C:\dshbuild` first, builds there, then copies the artifact back into `dist\`.
2. **lambdas need `core-lambda-stubs.jar`** — `android.jar` does not contain `LambdaMetafactory`, so `core-lambda-stubs.jar` from build-tools must also be put on `-bootclasspath`.

Signing uses the fixed keystore `%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`. **Passwords never enter the repository**: `build.ps1` reads the environment variable `DSH_KS_PASS` first, then `keystore.local.ps1` in the repository root (gitignored).
**Changing the signing key makes over-installation impossible**, so back that file up offline.

## 5. Installing and pairing

1. Install `dist/dsh-mobile.apk` on the phone (just allow "unknown sources"; adb is not required).
2. After restarting DSH on the PC, open the **"Mobile devices"** panel → set the run mode to **Always on** → enter a device name → **Generate pairing QR code**.
3. On the phone: app → ⚙ in the top-right → **Scan to pair**, and scan that QR code.
4. WAN: point your reverse proxy at port `3091` on the PC and enable TLS, then manually enter `wss://your-domain/ws/mobile` in the app and save it.

> Cleartext `ws://` on the LAN is allowed only for private ranges / loopback / `.local` (enforced by `GatewayClient.cleartextProblem()`); WAN access must use `wss://`.

## 6. Implemented protocol capabilities

| Capability | Status |
|---|---|
| Pairing (Base64URL payload → one-time pairing code → long-lived device token) | ✅ |
| Token-authenticated connection (subprotocol + `Authorization`, dual channel) | ✅ |
| Heartbeat / exponential-backoff reconnect on disconnect / close 4003·4004 semantics | ✅ |
| Session list, automatic session creation, rename, archive | ✅ |
| Subscribe + `session-snapshot` atomic baseline | ✅ |
| Persistent `event` stream (turn/step, user/assistant message, tool call/result, session/title) | ✅ |
| Separate `assistant-stream` deltas (`start` / `chunk` / `end(committed|abandoned)`) | ✅ |
| Approval: `approval-requested` → `approval-response` → `approval-resolved` | ✅ |
| Question: `question-requested` / `question-answer` / `question-cancel` (including multi-select and custom input) | ✅ |
| Stopping the current turn (`session-cancel`) | ✅ |
| History pagination (`beforeSeq` + `historyFormatVersion`) | ✅ |

**Not done in v1** (explicitly left out): image/file upload, file download, command and skill menus, task and goal (todos/goal) panels, model and permission switching,
multi-gateway management, one-click Cloudflare tunnel toggle (operate it from the gateway panel instead).

## 7. Verification record (measured)

With an isolated DSH instance (`DSH_HOME=C:\dshverify\home`, `mgw` profile) plus the real gateway plugin 0.9.0, driving
`harness/` so that it directly reuses the app's `WsClient` + `GatewayClient` sources:

- Gateway startup log: `applying: version=0.9.0 ... path=/ws/mobile, webServer.port=3999`
- `HELLO protocol=3 dshVersion=0.2.0-rc.2 historyFormatVersion=4 authenticated=true`
- Sending a message with `sessionId` omitted → `mobile message created new session session-ec54…` → `sent`
- `subscribe(assistantStream:true)` → `subscribed` + `session-snapshot(events=6, cursor=5)`
- Real model turn: `turn/start → step/start → user/message → assistant/message「联调成功」("integration test succeeded") → step/end → turn/end`
- **Approval loop closed**: `approval/asked` → `approval-requested(pwsh, escalate sandbox)` →
  `approval-response(allowed-once)` → `approval-resolved(allowed-once)` + `accepted:true`
  → the command really executed, `assistant/message「命令已执行成功…hello-from-dsh」("command executed successfully…hello-from-dsh")`
- **Device-token reconnect** (the app's everyday path) re-verified; after reconnecting, a session with `running=true` is still visible

APK side: `minSdk 26 / targetSdk 36`, no native code (a single APK covers every ABI),
v2+v3 signature verification passes, `classes.dex` 528.8 KB, whole package 668.6 KB.

## 8. Rollback

- A manual snapshot was taken before installation: `C:\Users\Administrator\.dsh\undo-snapshots\manual\20261001-010451-5d2d`
- Only three disk changes: `profiles/desktop/package.json` (deps + bundles),
  `profiles/desktop/cordis.patch.yml` (overlay appended at the end), `profiles/desktop/node_modules/dsh-plugin-mobile-gateway*/`
- To revert: have DSH roll the snapshot back with `undo_restore`, or manually delete the `node_modules` directory above and those two text edits.

---

## 9. Real-device verification record (early hours of 2026-10-01, Honor PGT-AN10 / Android 16)

Measured on device with USB connected and `adb` authorized: **the whole path works end to end**, as the gateway-side log shows:

```
18:10:30 message accepted: text="Run the shell command echo phone-to-pc-ok …"
18:10:34 approval requested: tool=pwsh
18:10:47 approval response: outcome=allowed-once  accepted=true
```

On the phone screen: user bubble → tool row `● pwsh completed` → approval card (`Approve once` / `Reject`) → after tapping approve it becomes `✓ Approved`
→ the assistant reply renders in full (including Markdown inline code): `命令已执行。命令：echo phone-to-pc-ok 输出：phone-to-pc-ok` ("Command executed. Command: echo phone-to-pc-ok Output: phone-to-pc-ok").

### The 4 bugs this round exposed on the device, and how they were fixed

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| 1 | It connected, but **the session list never appeared** and no request could be sent out | `WsClient.sendText()` was a synchronous `out.write()`, yet it was called from the main thread (in the hello callback) → `NetworkOnMainThreadException`, which was then swallowed by `catch (Throwable ignored)` | `WsClient` gained a **single-threaded write queue**; all frames are sent asynchronously (which also guarantees frame order for free) |
| 2 | Tapping "Scan to pair" **did not open the camera** (it flashed and vanished) | `Camera.setPreviewDisplay()` was called from `onResume`, before the Surface existed → exception → `catch(Throwable){finish();}` exited silently | The camera handle is opened only when `hasSurface` is true; on failure the reason is **shown in the UI** instead of exiting |
| 3 | The connection tore itself down and rebuilt every few tens of seconds | `open()` closed the old connection on purpose, and the old connection's `onClosed` then triggered `scheduleReconnect` → they kicked each other | Introduced a **generation counter**: late callbacks from old connections are dropped; reconnect tasks are de-duplicated; `onDestroy` no longer disconnects (the client became process-level shared) |
| 4 | The system back key **exited the app immediately** | `onBackPressed()` was not overridden | Conversation/settings screens return to the list; the list screen goes to the background |

### Two practical capabilities added along the way

- **In-app protocol diagnostics**: the bottom of the settings screen shows `state / gen / ws` plus the last 40 sent/received frames (`→ sessions`, `← hello`…).
  This is exactly how `NetworkOnMainThreadException` was spotted at a glance — because this Honor device's `adb logcat` returns nothing.
- **Injected-context filtering**: DSH injects `<system-reminder>`, `Current runtime context`, and the like as `user/message`,
  which the desktop client does not display; without filtering, the phone showed one huge blue bubble. It is now filtered.

### Debugging notes for later reference (Honor devices)

- Getting exact coordinates with uiautomator: `adb shell uiautomator dump /sdcard/u.xml` + `adb pull`.
  **Do not guess coordinates**; once the input field has focus, the soft keyboard / multi-line input shifts the input bar.
- Installation silently rejected (`INSTALL_FAILED_ABORTED: User rejected permissions`):
  `adb shell settings put global verifier_verify_adb_installs 0` fixes it.
- This machine's `adb logcat` basically returns nothing, so we used two channels: in-app diagnostics plus gateway logs.
- For long unattended debugging: `adb shell svc power stayon true` + `locksettings set-disabled true`;
  restore them afterwards and turn the screen off with `input keyevent 223` (to avoid OLED burn-in).

### Correction (2026-10-01 03:50): the back-key problem was not "onBackPressed was not overridden"

On the first real-device test, pressing back still exited the app immediately. **Overriding `onBackPressed()` alone is not enough**: on Android 15+,
apps targeting SDK 35+ use **predictive back** by default, so the system no longer calls `onBackPressed()`
and directly performs the default "finish the Activity".

The final fix stands on two legs:

1. `<application android:enableOnBackInvokedCallback="false">` in the manifest — forces the legacy back path;
2. `dispatchKeyEvent` catches `KEYCODE_BACK` as the primary path, with `onBackPressed()` kept as a fallback;
   both paths may fire for the same key press, so a 400 ms time window de-duplicates them.

It shipped with the APK, and the manifest inside the APK confirms `enableOnBackInvokedCallback=false`;
**this item has not yet been re-tested on the device** (the phone was locked and needed a PIN, so automation could not continue).

---

## 10. Second batch of bugs fixed in the real-device re-test (afternoon 2026-10-01)

The app has been renamed to **DSH 掌上通**.

### 1. A screen full of "(empty response)" / no body text in compact mode — one root cause

**Root cause**: the assistant body text in history and snapshot events (`session-snapshot` / `history`) **is not in `data.text`**
but in the `{"type":"text"}` blocks of `data.message.content[]`; `data.text` exists only in **live** `event` frames.
I was only reading `data.text`, so every history load produced an empty string: full mode showed "(empty response)", and compact mode filtered out the empty assistant bubbles → the body text disappeared entirely.

**Fix**: added `textFromBlocks(payload, type)`, which handles both
`{text,reasoning}` (live) and `{message:{content:[{type:'text'|'reasoning',text}]}}` (history);
and **bubbles are created only when the body text is non-empty** + the filter layer skips empty assistant bubbles in every mode (belt and braces).

Measured: of 70 `assistant/message` events, 63 yield body text correctly; the 7 pure-tool turns no longer produce empty shells.

### 2. The tool row never showed its result

Same-origin problem: the result text of `tool/result` lives in `data.message.content[]`, and historically there was **no `isError` field**
(that is the shape of live frames). Both paths are now supported, and the text is truncated to 400 characters.

### 3. Injected-context filtering now keys off `source.kind`

Previously it guessed from text prefixes and missed `Time sampled while preparing…`. Measured `source.kind` per source:

```
sent by the user (incl. from the phone):  user                ← kept
injected:                                 agent-instructions / runtime-context / skill-catalog /
                                          time-context / tool-jobs / user-approval      ← all filtered
```

The gateway source itself (`lib/index.mjs:3270`) also uses `message.source.kind === 'user'` to identify user messages,
so this criterion is protocol-level, not a heuristic.

### 4. Entering a conversation stopped at the top + needing a one-tap jump to the bottom

- **Entering a conversation stopped at the top**: `setSelection()` was called right after `setItems()`, before the ListView had re-laid out.
  Changed to `list.post(() -> list.setSelection(n-1))`.
- **Added a floating "↓" button in the bottom-right**: it appears when not at the bottom, and tapping it **jumps to the bottom instantly** and then auto-hides
  (the earlier `smoothScrollToPosition` took ages on very long conversations and looked like it did nothing on the device).

### Full audit of event types (2377 real events / 6 sessions)

| Handled correctly | Ignored by design | Not yet handled (v1 boundary) |
|---|---|---|
| `user/message`, `assistant/message`, `tool/call`, `tool/result`, `turn/start`, `turn/end`, `session/title` | `step/start`, `step/end`, `agent/inbox/spliced`, `permission/preset`, `sandbox/mode`, `approval/policy`, `session/title-llm-request`, `developer/message`, `session/end-seed`, `subagent/catalog`, `workspace/changes` | `approval/asked+decided` (historical approval cards), `command/run+done` (slash commands), `deliverables/presented` (deliverable cards), `todo/write`, `goal/change` |

### Installation notes for Honor devices

`adb install` may pop up in sequence: ① install confirmation → ② "no ICP registration information found" → ③ app info page (you must first tick "I understand…" and then tap "Continue install") → ④ **fingerprint verification**.
Once you have gone through one round, later installs from the same source remember the authorization and usually stop asking for a fingerprint — so **during debugging you should batch your changes into a single install**.

---

## 11. Feature completion and second self-review (evening 2026-10-01)

This round we stopped waiting for the device to expose problems and instead **audited the shapes against the real event stream first, then wrote code**, and cross-reviewed the existing code.

### New features

| Feature | Basis (real event / response shape) |
|---|---|
| **Slash-command log** | `command/run {commandId,name,args}` + `command/done {commandId,kind,text}` (previously invisible) |
| **Deliverable cards** | `deliverables/presented {callId,files:[{description,path}]}` — file name + description, tap to copy the path |
| **Historical approvals visible** | `approval/asked {id,toolName,reason,callId}` + `approval/decided {id,outcome}`; reopening a session still shows what was approved |
| **Goal / task summary** | `goal/change`, `todo/write` events + `tasks`/`goal` queries + `tasks-updated`/`goal-updated` pushes; shows the current goal and task list at the top of the conversation (completed goals are hidden automatically) |
| **Image sending** | "＋" in the input bar to pick an image → read bytes in the background → standard Base64 → `message.images[]` (no `data:` prefix) |
| **Image display** | `content[].attachment.attachmentId` collected automatically from history → fetched with an `attachment` request → decoded in the background → shown above the bubble (downsampled to 1080 px) |

### Two real bugs caught by self-review

1. **No re-subscription to the current session after a reconnect** — the subscription is lost when the gateway disconnects, and after reconnecting we only re-fetched the session list.
   Consequence: after a network blip, the conversation you are viewing **no longer updates in real time** (it looks "stuck").
   Fix: on `onState(READY)`, if the current session is non-empty, re-`subscribe` and re-fetch tasks/goal.
2. **Approval results could not find their card** — to share a key with the historical `approval/asked` event, approval cards were indexed by `approvalId`,
   but `onInteractionResolved` still looked them up by `rpcId` → approval results never landed (the card stayed at "pending approval").
   Fix: unify the key on both sides; and ran a full `byKey` cross-check (`cmd:` / `approval:` / `tool:` / `question:` / `stream:` / `pending-user`) confirming they all match.

Other small fixes: tool arguments are extracted from raw JSON into readable "description / command / path"; local echo de-duplication for image-only messages.

### End-to-end verification (no device; the same protocol frames straight to the gateway)

**The image path works end to end** (a full regression of the new features):

```
① image-bearing message accepted            session-c0588e3e-…
② image block shape in history              {"type":"image","attachment":{"attachmentId":"sha256:c414…",
                                            "mediaType":"image/png","width":1,"height":1,"bytes":70,"name":"test.png"}}
③ attachment fetched                        70 bytes, correct PNG signature, bytes identical ✅
```

**Task/goal query shapes confirmed** (my earlier compatibility logic was correct):

```
tasks → {"todos": null}                                  ← todos may be null; already handled
goal  → {"goal": {"goal": {objective, phase, …}}}        ← indeed doubly nested; already handled
         phase:"complete"                                ← completed goals no longer occupy the summary bar
```

### Still not done in v1 (explicit boundary)

File download (`file-download-open/read`), **input-completion menus** for slash commands and skills, model and permission switching, session fork, full-text session search
(the local `search` channel fails to index because old v2-format sessions exist — a host-side issue).

### Test leftovers

During debugging I created several sessions directly over the protocol; 2 of them could not be archived immediately because their turns were still running.
They become archivable once the turn ends, or you can long-press → "Archive" in the app to hide them.

---

## 12. PC-side "Mobile Access" plugin (dsh-mobile-access)

Implemented per option A: **not one line of the protocol layer is rewritten** (it keeps using `dsh-plugin-mobile-gateway`); this plugin only does access orchestration and UI.

- Source: `~/.dsh/local-plugins/dsh-mobile-access/` (`index.js` host proxy + `client.js` panel + `cordis.patch.yml`)
- Wiring: add a `link:` dependency + `dsh.profile.bundles` in the desktop profile's `package.json`

### Architecture

```
Browser panel (client.js)  --fetch-->  /dsh-mobile-access/*
Host proxy (index.js)      --loopback--> http://127.0.0.1:<webPort>/mgw/*
dsh-plugin-mobile-gateway              (protocol layer, third-party MIT)
DSH Host 0.2.0-rc.2
```

**Why the routes are not under `/api`**: DSH's `/api/*` is blocked by the browser trust fence (unknown paths return 401),
which is also why the off-the-shelf gateway sits under `/mgw`. Measured comparison:

```
/api/dsh-mobile-access/status  -> 401   (blocked by the fence)
/dsh-mobile-access/status      -> 404→200 (reachable; 200 after restart)
/mgw/status                    -> 200
```

### Measured verification (2026-10-01 18:0x, after restarting the desktop app)

| Panel action | Proxy route | Result |
|---|---|---|
| Generate pairing QR code | `POST /dsh-mobile-access/pair` | ✅ HTTP 201, SVG 10338 characters, pairing string 420 characters |
| Gateway tri-state switch | `POST /dsh-mobile-access/gateway` | ✅ persistent ↔ temporary switching works |
| Device list | `GET /dsh-mobile-access/devices` | ✅ returns the device array |
| Revoke device | `POST /dsh-mobile-access/devices/<id>/revoke` | ✅ cleared 28 debug devices, keeping only the phone |

The panel entry (Settings → General → "Mobile Access") was confirmed to appear correctly.

### Bonus outcome

After the desktop app restarted, **the gateway loaded into the user's real desktop profile** —
that is, the phone app is now connected to the user's own DSH (address unchanged: `ws://192.168.1.100:3091/ws/mobile`) and no longer depends on any verification instance.

---

## 13. Full real-device regression of the new features (2026-10-01 19:0x, Honor PGT-AN10)

One APK install (696.6 KB, `DSH 掌上通`), tested item by item:

| Feature | Real-device result | Evidence |
|---|---|---|
| Session list grouped by workspace | ✅ | `程序开发 · 2` / `default-workspace · 2` / `desktop · 6` … |
| Real titles (lazy loaded) | ✅ | 「手机App联动dsh控制电脑」「安装 Excel 表格处理技能」「功能验证-任务与交付物」 |
| Full-mode assistant body | ✅ | no "(empty response)" anywhere |
| Compact mode | ✅ | `● Running: pwsh` (arguments not expanded) |
| Tool row + result + polished arguments | ✅ | `● todo_write completed → Updated todo list: 1 pending, 1 in progress, 1 completed.` |
| Opening a conversation stops at the latest message | ✅ | at the bottom, ↓ auto-hidden |
| Bottom-right ↓ one-tap return to bottom | ✅ | appears after scrolling up; taps jump to the bottom instantly and it collapses |
| System back key | ✅ | conversation → list; list → background |
| **Task summary (todo)** | ✅ | top bar `☑ 检查交付物卡片 / ◐ 检查任务提要 / ☐ 检查图片显示` |
| **Deliverable cards** | ✅ | "Deliverables" + README.md + dsh-mobile.apk + description + "tap to copy path" ×2 |
| **Question cards** | ✅ | `Agent is waiting for your answer` + header「颜色确认」+ question + options (with descriptions) + custom input + submit/skip |
| **Question submission loop** | ✅ | tapped「要」on the phone → submit → the card became `✓ Answered`; **the agent's receipt confirmed it received「要」** |
| **Image display** | ✅ | an ImageView node appears in the UI; agent sampling confirmed real pixels RGB(220,60,60) |
| Approval card (live) | ✅ | `Your approval is needed` → tapped "Approve once" → the command really executed |

### Additional debugging lessons

- The three gates of `adb install`: **① the screen must be unlocked** (when locked, Honor returns `INSTALL_FAILED_ABORTED` outright and the confirmation dialog never even appears);
  ② `verifier_verify_adb_installs` gets reset, so set it again before installing; ③ once fingerprint authorization passes it is remembered for a while.
- The phone's foreground may be occupied by another app (WeChat this time); before automating taps you **must first confirm this app is in the foreground**, otherwise the taps land on someone else.
- **Write Node test scripts with `-Encoding utf8`** — using `ascii` turns the Chinese in the script into `?`,
  so the agent receives a message with every character replaced by `?` and misreads the instruction (I fell into this trap once).

---

## 14. Public access fully working (2026-10-01 21:0x)

### Conclusion: no port forwarding, no self-hosted reverse proxy, no certificate switch

**The root cause is three layers stacked** (each measured and evidenced):

| # | Problem | Evidence |
|---|---|---|
| 1 | The user's existing public port **is unreachable from the WAN** | The phone on 5G can `ping 203.0.113.10`, but TCP `:16888` times out; the PC can still connect → it goes through **router hairpin**, while the WAN side is actually closed |
| 2 | The user's reverse proxy (Lucky) **does not forward WebSockets** | Requests with an `Upgrade` header to `/ /ws /mobile /ws/mobile /mgw` **all** return the proxy's own `404 not found`; direct connections to 3091/19387 both return `101 Switching Protocols` |
| 3 | The reverse proxy certificate is **self-signed** | `issued to: Lucky`, `UNABLE_TO_VERIFY_LEAF_SIGNATURE`; an equivalent simulation reproduces `DEPTH_ZERO_SELF_SIGNED_CERT` |

### Adopted solution: **reuse the gateway's built-in Cloudflare tunnel**

Key finding (credit to `dsh-pocket`, the very plugin in the user's screenshot):

```
dsh-plugin-mobile-gateway/lib/cloudflared-binary.mjs  auto-downloads cloudflared (with SHA256 verification)
dsh-plugin-mobile-gateway/lib/cloudflare-tunnel.mjs   supports named / quick modes, quick by default
dsh-pocket/lib/tunnel.mjs                             startQuickTunnel — the same idea
```

The gateway already had this capability; it simply was not enabled. How to enable it (plain HTTP API, no account needed):

```
POST /mgw/cloudflare   {"enabled":true,"mode":"quick"}   → spins up a tunnel with a random domain
POST /mgw/cloudflare/restart                             → restart
GET  /mgw/status       → cloudflare.{state,mode,publicUrl}
```

**Pitfall hit**: `cloudflared-binary.mjs` reuses a cached binary, but only when the sha256 of
`<version>-<platform>-<arch>.exe` under `cacheDir` matches. After the first download timed out it **reused the failed promise**,
so after placing the file you must "turn the tunnel off → turn it on again" for it to prepare anew.
(Local path: `~/.dsh/cloudflared-bin/2026.9.3-win32-x64.exe`, sha256 `f096265e…`, matching the official release.)

### Measured results

```
gateway endpoints:  wss://example-tunnel.trycloudflare.com/ws/mobile   ← first priority
                    ws://192.168.1.100:3091/ws/mobile
publicUrl:          wss://example-tunnel.trycloudflare.com/ws/mobile
cloudflare:         state=online  port=3082

phone (5G, mobile data, not on WiFi):
  connectedClients = 1
  HONOR PGT-AN10  online=True  connections=1
  in-app status: state=READY     ← real certificate, "allow self-signed certificates" not enabled
```

**While the tunnel is online, `publicUrl` is the tunnel address**, so "Generate pairing QR code" automatically carries the public address — scan it on the phone and you have WAN access.

### Added on the panel side

`dsh-mobile-access` gained a "Public access (Cloudflare tunnel)" card and three proxy routes:

```
POST /dsh-mobile-access/tunnel          → /mgw/cloudflare
POST /dsh-mobile-access/tunnel/restart  → /mgw/cloudflare/restart
```

The UI shows the tunnel state (not started/preparing/starting/online/error), the mode, the public address (one-tap copy), and start/stop/restart.
And **pairing prefers the tunnel's public address** (`manualUrl || tunnelUrl || lanUrl` inside `makePairing`).

> Note: the client bundle is packaged when DSH starts, so **this new panel card only becomes visible after restarting the desktop app**.

### Stability notes

In quick mode the domain **changes on every restart**. There are two ways to pin the domain:
1. **Named tunnel**: move the domain's NS to Cloudflare → create a Tunnel and get a token → configure `mode=named` + hostname + token in the panel/gateway (the user's domain is currently hosted at Alibaba Cloud `<your-dns-provider>`, so the NS must be moved first);
2. **Tailscale**: install Tailscale on the phone and use `ws://100.x.x.x:3091/ws/mobile` (the app already treats 100.64/10 and `*.ts.net` as LAN, so the cleartext restriction does not apply).

---

## 15. Feedback and collapsible settings (2026-10-01)

### Feedback (an expandable item in settings)

- Settings → "Feedback" → "Write feedback" → a feedback dialog appears: input field + **Send to PC** / **Copy** / Cancel
- Environment information is attached automatically: `v1.0 · public/LAN · state=READY gen=1 ws=open` + the current address
- "Send to PC" sends the feedback as a message into the current session (when not connected it automatically degrades to copying to the clipboard)
- Kept locally (SharedPreferences, newest first); the settings screen shows "N recorded · latest: …"

Measured: the dialog title, input field, automatically attached environment line and all three buttons work; `state=READY` shows it was connected through the tunnel at the time.

### Collapsible settings

Once there were many settings, expanding them all was noisy, so they became **title-only until tapped**:

```
Connection settings ▾   ← expanded by default
Chat display        ▸
About               ▸
Current status      ▸
How do I connect?   ▸
Feedback            ▸
```

Implementation: `SettingsView.section(parent, title, expanded)` returns the content container; tapping the title row toggles
the visibility of the content and the divider and flips the arrow between ▾/▸.

---

## 16. One-tap deliverable download to the phone (later on 2026-10-01)

### Protocol (from PROTOCOL.md, reviewed before use)

```
→ {"type":"file-download-open","requestId":"d1","sessionId":"…","path":"path relative to the working directory"}
← {"kind":"file-download-opened","requestId":"d1","transferId":"…","name":"…","size":733751,"chunkBytes":524288}
→ {"type":"file-download-read","transferId":"…","offset":0}
← {"kind":"file-download-chunk","transferId":"…","offset":0,"data":"<base64>","eof":false}
← the last chunk has eof:true and carries sha256
```

**Key constraint**: `path` **must be a relative path inside the session working directory**; an absolute path is rejected outright with `bad-request` (confirmed by measurement).
So absolute paths in deliverable cards must first be resolved against the session `cwd`; if the file is not inside that directory, say so explicitly instead of sending a request that is guaranteed to fail.

### Implementation

- Protocol layer: `file-download-open` / `-read` / `-cancel` plus `file-download-opened|chunk|cancelled|closed` dispatch
- Download flow: **chunked serial pulls** (strictly using "previous chunk offset + number of decoded bytes" as the next offset),
  writing to a temporary file while accumulating SHA-256; after `eof` the whole-file digest is verified, then the file is written to its final location and the temporary file is deleted.
  File IO runs on a separate thread (`dl-io`) and does not occupy the main thread
- Final location: **on API 29+ it goes to MediaStore's `下载/DSH 掌上通/`** (that is the localized "Downloads" folder, directly visible in the file manager, no permission needed);
  older versions fall back to the app's external downloads directory
- Interaction: a deliverable row is **tap = download to the phone, long-press = copy the path**; the row shows live state ("requesting / downloading N% / downloaded ✓ path / download failed: reason")

### Measured

First, pure protocol verification (Node, frames isomorphic to the app's):

```
② opened: name=dsh-mobile.apk size=733751 chunk=524288
③ received 733751 bytes / declared 733751
   server sha256 == the one I computed == local file sha256   ✅
absolute path → bad-request "file-download-open requires a relative workspace path"  ✅ (constraint confirmed)
```

Then on the real device (tapping the apk in the deliverable card):

```
App: downloaded ✓ 下载/DSH 掌上通/dsh-mobile.apk
Phone: 733751 bytes  → identical to the source file
```

### Known limitations

- Only files **inside the session working directory** can be downloaded (a hard gateway-side constraint). A session created on the phone has the profile directory as its `cwd`,
  while the agent's deliverables are usually in a project directory → those show "not in this session's working directory".
- The live-pushed `deliverables/presented` event is visible in session history (reopening the session renders the card),
  but **in the live turn** no card was rendered — still to be determined whether the host does not broadcast it live or the client misses it.

---

## 17. Tunnel domain rotation and QR auto-fill (late night 2026-10-01)

### The bug the user reported: public access would not connect

**The root cause is not a code bug but that the quick tunnel's domain changes on every restart**:

```
before restart (stored in the app): example-tunnel.trycloudflare.com   ← expired
after restart (gateway online):     example-tunnel.trycloudflare.com
(measured: the new address handshakes fine: hello / auth=true)
```

The app had the old domain stored, so of course it could not connect, and `connectedClients = 0`.

### Why the app cannot ask for the current public address itself

Checking the protocol (PROTOCOL.md):

- `endpoints` are **given only in the "pairing payload" and in the local admin interface `GET /mgw/status`**;
  `hello` / `paired` **do not contain** endpoints;
- `/mgw/*` is restricted by `adminLoopbackOnly`, so **the phone cannot reach it**.

→ Therefore **the pairing payload (QR code / pairing string) is the only automatic channel through which the app can obtain the current public address**;
"scan to auto-fill" is not an optional optimization but the only correct path.

### The four changes implemented

| Change | Description |
|---|---|
| **One scan fills both addresses** | Parse `endpoints[]` from the pairing payload: private ranges go into the "LAN" slot, public/tunnel addresses into the "public" slot; when only a public address exists it switches to "use public" automatically |
| **Automatic switch on failure** | On connection failure, **switch once** between LAN and public (only once, to avoid ping-ponging); added `GatewayClient.Listener.onReconnectScheduled` |
| **Explicit warning when expired** | When the address contains `trycloudflare.com` or the error contains 404, it says "the public address may have expired (the tunnel domain changes on every restart) → scan again" |
| **Security disclaimer** | Phone side: a dialog when first switching to "use public" plus a tick on "I understand" before proceeding (stored as a one-time confirmation); panel side: a dialog **every time** the public tunnel is enabled (matching dsh-pocket) |

### Want a permanently fixed address?

The quick tunnel cannot do it (in the protocol it is a random domain). Two options:

1. **Named tunnel**: move `example.com`'s NS from Alibaba Cloud (`<your-dns-provider>`) to Cloudflare,
   create a Tunnel to get token/hostname, then `POST /mgw/cloudflare {"enabled":true,"mode":"named","hostname":…,"token":…}`
2. **Tailscale**: join the phone and the PC to the same tailnet and enter `ws://100.x.x.x:3091/ws/mobile` in the app
   (the app already treats 100.64/10 and `*.ts.net` as LAN, so the cleartext restriction does not apply; the address never changes, and it does not expose the public internet)
