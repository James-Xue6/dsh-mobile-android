@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  DSH 掌上通 · 一键启动物联网关（mock 网关，零依赖 Node）
rem  用法：
rem     启动物网关.bat                    ^<- 默认 fault=none host=10.0.2.2
rem     启动物网关.bat silent             ^<- 换一档故障
rem     启动物网关.bat none 3191 192.168.1.20   ^<- 端口 / 局域网 IP
rem  配对串会打印在窗口里，同时写到 tools\pairing.txt
rem  停止：按 Ctrl+C
rem ============================================================

set "FAULT=%~1"
if "%FAULT%"=="" set "FAULT=none"
set "PORT=%~2"
if "%PORT%"=="" set "PORT=3191"
set "HOST=%~3"
if "%HOST%"=="" set "HOST=10.0.2.2"

set "NODE="
for /f "delims=" %%i in ('where node 2^>nul') do if not defined NODE set "NODE=%%i"
if not defined NODE if exist "%LOCALAPPDATA%\hermes\node\node.exe" set "NODE=%LOCALAPPDATA%\hermes\node\node.exe"
if not defined NODE (
  echo [错误] 找不到 node.exe。请装 Node 18+ 并加入 PATH。
  pause
  exit /b 1
)

echo ================================================================
echo   DSH 掌上通 · mock 网关
echo   故障档 : %FAULT%
echo   端口   : %PORT%
echo   配对主机: %HOST%   (模拟器=10.0.2.2；真机=电脑局域网 IP)
echo   配对串 : %~dp0pairing.txt
echo   日志   : %~dp0mock-gateway.jsonl  ^(每帧收发+close 方向/码^)
echo   停止   : Ctrl+C
echo ================================================================
echo.

"%NODE%" "%~dp0mock-gateway.mjs" --port %PORT% --fault %FAULT% --host %HOST%

echo.
echo 网关已退出。
pause
