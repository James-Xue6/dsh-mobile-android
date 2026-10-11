@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
rem [V3.55] 一键安装：自动关闭 DSH -> 备份 -> 装入补丁 -> 校验 -> 自动重启 DSH
rem 顶部四行参数化，便于用"临时副本"做不碰真档案的逻辑自测。
rem 注意：本文件必须 CRLF 行尾（cmd 对 LF-only 的 .bat 会闪退）。
set RES=D:\AI\DSH\resources
set EXE=D:\AI\DSH\DeepSeek Harness.exe
set PROC=DeepSeek Harness.exe
set EXPECT=121348401

set APP=%RES%\app.asar
set PATCHED=%RES%\app.asar.patched

echo ============================================================
echo  DSH v2 会话补丁 - 一键安装（会自动关掉 DSH，装完自动开回来）
echo ============================================================
echo.

if not exist "%PATCHED%" (
  echo [X] 找不到 %PATCHED%
  echo     请先运行：node tools\dsh-asar-patch\patch-asar.mjs
  echo.
  pause & exit /b 1
)
if not exist "%APP%" (
  echo [X] 找不到 %APP%
  echo.
  pause & exit /b 1
)

for %%A in ("%APP%") do set CURSIZE=%%~zA
if "!CURSIZE!"=="%EXPECT%" (
  echo [i] 已经装过了（app.asar = !CURSIZE! 字节），无需重复安装。
  echo.
  pause & exit /b 0
)

echo 即将：强行关闭 DSH → 备份原档案 → 装入补丁 → 校验 → 自动把 DSH 开回来。
echo 不想现在关，请立刻关掉本窗口（5 秒后自动继续）...
timeout /t 5 >nul

echo.
echo [1/5] 关闭 DSH ...
taskkill /IM "%PROC%" /F >nul 2>nul

set /a N=0
:waitlock
set BUSY=0
if exist "%RES%\app.asar.probe" del "%RES%\app.asar.probe" >nul 2>nul
ren "%APP%" "app.asar.probe" >nul 2>nul
if exist "%RES%\app.asar.probe" (
  ren "%RES%\app.asar.probe" "app.asar" >nul 2>nul
) else (
  set BUSY=1
)
if "!BUSY!"=="1" (
  set /a N+=1
  if !N! GEQ 60 (
    echo.
    echo [X] 等了 60 秒文件仍被占用，放弃（未改任何内容）。
    echo.
    pause & exit /b 1
  )
  <nul set /p "=."
  timeout /t 1 >nul
  goto waitlock
)
echo      文件句柄已释放 ✓

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TS=%%i
set BAK=%RES%\app.asar.bak-%TS%

echo [2/5] 备份 → %BAK%
move /y "%APP%" "%BAK%" >nul
if errorlevel 1 (
  echo [X] 备份改名失败（还有进程占着）
  echo.
  pause & exit /b 1
)

echo [3/5] 装入补丁 ...
move /y "%PATCHED%" "%APP%" >nul
if errorlevel 1 (
  echo [X] 装入失败：还原备份
  move /y "%BAK%" "%APP%" >nul
  echo.
  pause & exit /b 1
)

for %%A in ("%APP%") do set NEWSIZE=%%~zA
echo [4/5] 校验：app.asar = !NEWSIZE! 字节（期望 %EXPECT%）
if not "!NEWSIZE!"=="%EXPECT%" (
  echo [X] 校验不过：回滚
  del "%APP%" >nul 2>nul
  move /y "%BAK%" "%APP%" >nul
  echo     已回滚到原档案。
  echo.
  pause & exit /b 1
)

echo [5/5] 重新启动 DSH ...
if not "%EXE%"=="" start "" "%EXE%"
timeout /t 2 >nul

echo.
echo [OK] 补丁已装入并校验通过。备份：%BAK%
echo      回滚：先退 DSH，再  del "%APP%"  ^&^&  ren "%BAK%" app.asar
echo.
pause
