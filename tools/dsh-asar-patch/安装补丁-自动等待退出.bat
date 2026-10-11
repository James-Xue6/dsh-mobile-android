@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
set RES=D:\AI\DSH\resources
set APP=%RES%\app.asar
set PATCHED=%RES%\app.asar.patched
set EXE=D:\AI\DSH\DeepSeek Harness.exe
set EXPECT=121348401

echo ============================================================
echo  DSH v2 会话迁移补丁 - 安装（会自动等 DSH 退出）
echo ============================================================
echo.

if not exist "%PATCHED%" (
  echo [X] 找不到 %PATCHED%
  echo     请先运行：node tools\dsh-asar-patch\patch-asar.mjs
  pause & exit /b 1
)

rem 已经装过了？
for %%A in ("%APP%") do set CURSIZE=%%~zA
if "!CURSIZE!"=="%EXPECT%" (
  echo [i] app.asar 已经是打过补丁的版本（!CURSIZE! 字节）—— 无需再装。
  echo.
  pause & exit /b 0
)

echo [1/4] 现在开始**等待 DSH 完全退出**（最多等 10 分钟）。
echo       请：托盘图标右键 -^> 退出；或任务管理器结束 “DeepSeek Harness.exe”。
echo       本窗口会一直守着，退出后**自动**装补丁并重启 DSH。
echo.
set /a WAITED=0
:wait
tasklist /fi "imagename eq DeepSeek Harness.exe" 2>nul | find /i "DeepSeek Harness.exe" >nul
if not errorlevel 1 (
  set /a WAITED+=5
  if !WAITED! GEQ 600 (
    echo [X] 等了 10 分钟 DSH 还没退出，放弃（什么都没改）。
    pause & exit /b 1
  )
  <nul set /p "=."
  timeout /t 5 >nul
  goto wait
)
echo.
echo       DSH 已退出 ✓
echo.

rem 时间戳
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TS=%%i
set BAK=%RES%\app.asar.bak-%TS%

echo [2/4] 备份原 app.asar ...
move /y "%APP%" "%BAK%" >nul
if errorlevel 1 (
  echo [X] 无法重命名 %APP% —— 还有进程占着它。
  echo     请确认任务管理器里没有 “DeepSeek Harness.exe” 之后再双击本脚本。
  pause & exit /b 1
)
echo       备份：%BAK%

echo [3/4] 装入补丁 ...
move /y "%PATCHED%" "%APP%" >nul
if errorlevel 1 (
  echo [X] 安装失败：已把备份还原回去
  move /y "%BAK%" "%APP%" >nul
  pause & exit /b 1
)

rem 校验
for %%A in ("%APP%") do set NEWSIZE=%%~zA
if not "!NEWSIZE!"=="%EXPECT%" (
  echo [X] 装完后大小是 !NEWSIZE!（应为 %EXPECT%）—— 还原备份。
  del "%APP%" >nul 2>nul
  move /y "%BAK%" "%APP%" >nul
  pause & exit /b 1
)
echo       已安装 ✓ 大小 !NEWSIZE!（原 %EXPECT% 期望值）

echo [4/4] 启动 DSH ...
start "" "%EXE%"
timeout /t 3 >nul

echo.
echo [OK] 完成。补丁已生效（下次启动 DSH 即带 instruction-hint 白名单项）。
echo      备份保留在：%BAK%
echo      回滚：先退 DSH，再  del "%APP%"  ^&^&  ren "%BAK%" app.asar
echo.
pause
