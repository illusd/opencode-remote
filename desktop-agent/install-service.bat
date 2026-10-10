@echo off
chcp 65001 >nul
title OpenCode Remote Agent - Service Installer

echo ╔══════════════════════════════════════════════════════════════╗
echo ║  OpenCode Remote Agent - Windows Service 安裝程式          ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

REM 檢查管理員權限
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ❌ 請以「系統管理員身份執行」此腳本
    echo.
    echo 右鍵點擊此檔案 → 選擇「以系統管理員身份執行」
    pause
    exit /b 1
)

echo ✅ 管理員權限確認通過
echo.

REM 參數處理
set RELAY_URL=%1
set TOKEN=%2

if "%RELAY_URL%"=="" (
    echo 用法: install-service.bat ^<Relay_URL^> [Token]
    echo.
    echo 範例:
    echo   install-service.bat wss://your-relay.fly.dev
    echo   install-service.bat wss://your-relay.fly.dev abc123token
    echo.
    echo 參數說明:
    echo   Relay_URL  - Relay Server WebSocket URL (必填，格式: wss://domain.com)
    echo   Token      - 配對 Token (選填，不提供則自動產生)
    echo.
    pause
    exit /b 1
)

REM 驗證 URL 格式
echo %RELAY_URL% | findstr /R "^wss://" >nul
if %errorLevel% neq 0 (
    echo %RELAY_URL% | findstr /R "^ws://" >nul
    if %errorLevel% neq 0 (
        echo ❌ Relay URL 格式錯誤，必須以 wss:// 或 ws:// 開頭
        pause
        exit /b 1
    )
)

echo 設定參數:
echo   Relay URL: %RELAY_URL%
if not "%TOKEN%"=="" echo   Token: %TOKEN%
echo.

REM 設定環境變數供 JVM 讀取
set OPENCODE_RELAY_URL=%RELAY_URL%
if not "%TOKEN%"=="" set OPENCODE_TOKEN=%TOKEN%

REM 尋找 jar 檔
set JAR_PATH=
for %%f in (
    "build\libs\opencode-desktop-agent.jar"
    "build\libs\opencode-desktop-agent-all.jar"
    "..\desktop-agent\build\libs\opencode-desktop-agent.jar"
    "%CD%\desktop-agent\build\libs\opencode-desktop-agent.jar"
) do (
    if exist "%%f" set JAR_PATH=%%~ff
)

if "%JAR_PATH%"=="" (
    echo ❌ 找不到打包後的 jar 檔案
    echo 請先在專案根目錄執行: .\gradlew :desktop-agent:shadowJar
    pause
    exit /b 1
)

echo ✅ 找到應用程式: %JAR_PATH%
echo.

REM 執行安裝 (透過 Java 呼叫 WindowsService.install)
echo 🔧 正在安裝 Windows Service...
java -cp "%JAR_PATH%" com.opencode.remote.desktop.service.WindowsService install "%RELAY_URL%" "%TOKEN%"

if %errorLevel% equ 0 (
    echo.
    echo ╔══════════════════════════════════════════════════════════════╗
    echo ║  安裝完成！                                                ║
    echo ║                                                            ║
    echo ║  服務名稱: OpenCode Remote Agent                           ║
    echo ║  服務 ID:  OpenCodeRemoteAgent                             ║
    echo ║                                                            ║
    echo ║  管理指令:                                                 ║
    echo ║    啟動:   net start OpenCodeRemoteAgent                   ║
    echo ║    停止:   net stop OpenCodeRemoteAgent                    ║
    echo ║    狀態:   sc query OpenCodeRemoteAgent                    ║
    echo ║    解除:   以管理員執行 uninstall-service.bat              ║
    echo ║                                                            ║
    echo ║  手機配對: 服務啟動後會在日誌顯示 Token                    ║
    echo ║            或執行: java -jar %JAR_PATH% --token            ║
    echo ╚══════════════════════════════════════════════════════════════╝
) else (
    echo.
    echo ❌ 安裝失敗，請檢查上方錯誤訊息
)

pause