@echo off
chcp 65001 >nul
title OpenCode Remote Agent - Service Uninstaller

echo ╔══════════════════════════════════════════════════════════════╗
echo ║  OpenCode Remote Agent - Windows Service 解除安裝            ║
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

echo ⚠️  即將解除安裝 OpenCode Remote Agent 服務
echo    這將會停止並移除系統服務，但不會刪除設定檔
echo.

set /p CONFIRM=確定要繼續嗎? (y/N): 
if /i not "%CONFIRM%"=="y" (
    echo 已取消
    pause
    exit /b 0
)

echo.
echo 🔧 正在解除安裝...

REM 嘗試透過 Java 呼叫解除安裝
set JAR_PATH=
for %%f in (
    "build\libs\opencode-desktop-agent.jar"
    "build\libs\opencode-desktop-agent-all.jar"
    "..\desktop-agent\build\libs\opencode-desktop-agent.jar"
    "%CD%\desktop-agent\build\libs\opencode-desktop-agent.jar"
) do (
    if exist "%%f" set JAR_PATH=%%~ff
)

if not "%JAR_PATH%"=="" (
    java -cp "%JAR_PATH%" com.opencode.remote.desktop.service.WindowsService uninstall
) else (
    echo ⚠️  找不到 jar，嘗試直接呼叫 WinSW...
    
    set WINSW_DIR=%USERPROFILE%\.opencode-agent\service
    set WINSW_EXE=%WINSW_DIR%\OpenCodeRemoteAgent.exe
    
    if exist "%WINSW_EXE%" (
        "%WINSW_EXE%" uninstall
    ) else (
        echo ❌ 找不到 WinSW 執行檔
        echo 請手動執行: sc delete OpenCodeRemoteAgent
    )
)

echo.
if %errorLevel% equ 0 (
    echo ✅ 服務已成功解除安裝
) else (
    echo ❌ 解除安裝失敗
    echo 嘗試手動執行: sc delete OpenCodeRemoteAgent
)

pause