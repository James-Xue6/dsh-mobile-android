@echo off
chcp 65001 >nul
setlocal
set RES=D:\AI\DSH\resources
set APP=%RES%\app.asar
set PATCHED=%RES%\app.asar.patched

echo ============================================================
echo  DSH v2 会话迁移补丁 - 安装（把 "instruction-hint" 加进 SOURCE_KINDS）
echo ============================================================
echo.

if not exist "%PATCHED%" (
  echo [X] 找不到 %PATCHED%
  echo     请先运行:node tools\dsh-asar-patch\patch-asar.mjs  （会在 DSH 关闭状态生成它）
  pause & exit /b 1
)

rem 时间戳（用 PowerShell 拿，避免 %DATE% 的本地化格式）
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TS=%%i
set BAK=%RES%\app.asar.bak-%TS%

echo [1/3] 备份原 app.asar ...
move /y "%APP%" "%BAK%" >nul 2>nul
if errorlevel 1 (
  echo [X] 无法重命名 %APP% —— DSH 还在运行、占着这个文件。
  echo     请先【完全退出 DSH】（托盘图标右键退出 / 任务管理器结束 DeepSeek Harness.exe），再双击本脚本。
  pause & exit /b 1
)
echo      备份为: %BAK%

echo [2/3] 安装补丁 ...
move /y "%PATCHED%" "%APP%" >nul
if errorlevel 1 (
  echo [X] 安装失败：已把备份还原回去
  move /y "%BAK%" "%APP%" >nul
  pause & exit /b 1
)
echo      已安装

echo [3/3] 启动 DSH ...
start "" "D:\AI\DSH\DeepSeek Harness.exe"
timeout /t 3 >nul

echo.
echo [OK] 完成。备份保留在: %BAK%
echo      若 DSH 起不来，把备份改回 app.asar 即可回滚：
echo        del "%APP%" ^&^& ren "%BAK%" app.asar
echo.
pause
