@echo off
chcp 65001 >nul
rem 双击即可：起 AVD dshphone → 等开机 → 覆盖安装 APK → 打开 App
rem 想带参数就直接用 pwsh 调 .ps1（见 tools\使用说明.md）
pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0启动模拟器并装机.ps1" %*
echo.
pause
