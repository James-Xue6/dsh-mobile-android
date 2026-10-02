@echo off
chcp 936 >nul
rem 双击即可：起 AVD dshphone → 等开机 → 覆盖安装 APK → 打开 App
rem 编码说明：本文件用 GBK 保存，与控制台 chcp 936 匹配（不要改成 UTF-8，否则中文路径会乱码）
set "PWSH=C:\Program Files\PowerShell\7\pwsh.exe"
if not exist "%PWSH%" set "PWSH=C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"
"%PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0启动模拟器并装机.ps1" %*
echo.
pause
