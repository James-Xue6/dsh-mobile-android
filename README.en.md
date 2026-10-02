**English** | [简体中文](README.md)

# DSH Mobile for Android (DSH 掌上通)

Control **DeepSeek Harness** on your PC from your phone. A native Android client whose UI follows the shape of
chat products like Doubao / Trae: session list → bubble chat → real-time streaming output → inline approval
and question cards.

---

## Install it (about 3 minutes)

### 1. PC: install the plugin

On your computer (needs PowerShell 7 / `pwsh`):

```powershell
git clone https://github.com/James-Xue6/dsh-mobile-android.git
cd dsh-mobile-android
pwsh -File .\pc-plugin\install.ps1
```

The script copies the plugin into `~/.dsh/local-plugins/`, drops the APK into the plugin directory, and
registers the dependency in your DSH profile.

> With SSH, replace the first command with `git clone git@github.com:James-Xue6/dsh-mobile-android.git`.

### 2. Restart the DSH desktop app once

Then click the **"Mobile devices"** button at the bottom of the left sidebar — it is the only entry point on the PC.

### 3. Phone: install the app

The first screen of the drawer is the "Mobile access" card. Click **"Download app"**, then scan the QR code
**with your phone on the same Wi-Fi** to download and install it (the APK is served straight from your own
computer on LAN port 8099 — no cloud drive, no CDN).

You can also download the APK directly:

- CDN (usually faster in mainland China): `https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@v0.8/dist/dsh-mobile.apk`
- GitHub: `https://github.com/James-Xue6/dsh-mobile-android/raw/v0.8/dist/dsh-mobile.apk`

### 4. Pair by QR code

On the same card click **"Generate LAN QR code"** (at home) or **"Generate public QR code"** (away), and scan it
with the app. **Both the LAN and the public address are filled in at once**, so you can use the LAN at home and
switch to the public address when you go out, with no manual typing.

> The computer needs `dsh-plugin-mobile-gateway` (the protocol layer). The install script registers it
> automatically; if it is missing, install it once from the DSH plugin marketplace.

---

## How to use it

1. **Pick a session** — the app opens on the session list (grouped by workspace); tap one to enter, or create a new one.
2. **Send a message** — the assistant reply streams in character by character; tool calls collapse into a single row.
3. **Answer it** — when a decision is needed a card appears:
   - **Approval card**: `Approve once` / `Reject`;
   - **Question card**: options (multi-select) plus custom input;
   - **Deliverable card**: tap to download the file to your phone, long-press to copy the path.
4. The top of a conversation shows the current **goal** and **task list**; the `↓` button at the bottom right jumps back to the newest message.

---

## Troubleshooting

**The phone cannot connect to the PC**
- Make sure the phone and the PC are on the same Wi-Fi, and that the gateway is on (in the "Advanced settings" section of the "Mobile devices" drawer).
- In the app: ⚙ in the top-right → "Current status" shows `state / gen / ws` plus the last 40 frames, which pinpoints where it stalls.
- "The public address may have expired": the public tunnel uses a temporary domain that **changes on every PC restart**; just scan the QR code again.

**The QR code will not scan / the camera flashes and closes**
- Grant the camera permission (system settings → Apps → DSH 掌上通 → Permissions).
- Or tap "Copy pairing string" and paste it into the app manually.

**The "Mobile devices" button is missing after restarting DSH**
- The plugin only takes effect after you **restart the DSH desktop app once**.
- If it is still missing, confirm `dsh-plugin-mobile-gateway` is installed (search for it in the DSH plugin marketplace).

**Over-installation fails with "App not installed"**
- The signing key differs from the previous build. Rebuild with the original keystore
  (`%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`); if that file is lost you must uninstall first.
  **Back that .jks up offline.**

**I want a fixed public address (no re-scan after every restart)**
- Join the phone and the PC to the same Tailscale network and enter `ws://100.x.x.x:3091/ws/mobile` in the app
  (the app already treats `100.64/10` and `*.ts.net` as LAN, so the cleartext rule does not apply and nothing is exposed publicly).
- Or move your domain's NS to Cloudflare, create a named tunnel, and configure `mode=named` in the panel.

---

## For developers

| To do this | Command |
|---|---|
| Build the APK | `pwsh -File .\build.ps1` |
| Run the network-layer end-to-end tests | `pwsh -File .\harness\build.ps1`, then `node tools\run-e2e.mjs` |
| Per-scenario verdict analysis | `node tools\e2e-assert.mjs` |
| Emulator + install (UI checks) | `tools\启动模拟器并装机.bat` |
| Release | `pwsh -File .\release.ps1 -Version 0.5 -Notes "what changed"` |

- **Build requirements**: JDK 21 + Android SDK (`build-tools;36.0.0` and `platforms;android-36`).
  The source path **must not contain non-ASCII characters** (native tools cannot open such paths under code page 936);
  the script stages the build into `C:\dshstage` automatically.
- **Signing**: fixed keystore `%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`; the password comes from the
  `DSH_KS_PASS` environment variable or `keystore.local.ps1` in the repository root (gitignored, **never committed**).
- **Update prompt**: on launch the app reads `dist/version.json` from `@main` and compares versions; the release script refreshes it.
- Test scaffolding notes and pitfalls: [tools/使用说明.md](tools/使用说明.md).
  PC plugin details: [pc-plugin/dsh-mobile-access/README.md](pc-plugin/dsh-mobile-access/README.md).

### Layout

```
dsh-mobile-android/
├── AndroidManifest.xml
├── build.ps1 / release.ps1      # Gradle-free build / one-command release
├── dist/                        # distributed APK, checksum and update manifest
├── libs/core-3.5.3.jar          # zxing QR scanning, pure Java, no native
├── res/                         # theme / icons / network policy
├── src/com/dsh/mobile/          # the app: MainActivity + model/ + net/ + ui/
├── harness/                     # JVM integration harness reusing the app's real net layer
├── pc-plugin/                   # PC-side "Mobile access" plugin + installer
└── tools/                       # mock gateway, e2e scripts, emulator install
```

### Architecture

The app implements only the `dsh-mobile-v1` protocol (WebSocket + JSON) and never touches DSH internals;
the protocol layer is the third-party MIT plugin `dsh-plugin-mobile-gateway`, while `pc-plugin/` in this
repository only does access orchestration and UI.

```
Android App ──ws/wss──> dsh-plugin-mobile-gateway ──> DSH Host (desktop profile)
```

---

## License

This repository does not ship a `LICENSE` file yet. Please confirm the terms with the author before
redistributing or using it commercially.
