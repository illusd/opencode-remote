@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM OpenCode Remote - Windows 建置腳本
REM 用法: build-all.bat [clean|build|relay|desktop|android|install-service]

set ROOT_DIR=%~dp0
cd /d "%ROOT_DIR%"

REM 顏色 (使用 ANSI 轉義碼，Windows 10+ 支援)
for /f %%a in ('echo prompt $E ^| cmd') do set ESC=%%a
set RED=%ESC%[31m
set GREEN=%ESC%[32m
set YELLOW=%ESC%[33m
set BLUE=%ESC%[34m
set NC=%ESC%[0m

:LOG_INFO
echo %BLUE%[INFO]%NC% %*
goto :EOF

:LOG_SUCCESS
echo %GREEN%[SUCCESS]%NC% %*
goto :EOF

:LOG_WARN
echo %YELLOW%[WARN]%NC% %*
goto :EOF

:LOG_ERROR
echo %RED%[ERROR]%NC% %*
goto :EOF

REM 檢查工具
call :CHECK_TOOLS
if errorlevel 1 exit /b 1

if "%1"=="" set CMD=build
if "%1"=="clean" call :CLEAN_ALL & goto :END
if "%1"=="build" call :BUILD_ALL & goto :END
if "%1"=="relay" call :BUILD_RELAY & goto :END
if "%1"=="desktop" call :BUILD_DESKTOP & goto :END
if "%1"=="android" call :BUILD_ANDROID & goto :END
if "%1"=="install-service" call :INSTALL_SERVICE %2 %3 & goto :END

echo 用法: build-all.bat {clean|build|relay|desktop|android|install-service}
exit /b 1

:CHECK_TOOLS
call :LOG_INFO 檢查建置工具...

where node >nul 2>&1 || (call :LOG_ERROR 找不到 node.exe，請安裝 Node.js 20+ & exit /b 1)
where npm >nul 2>&1 || (call :LOG_ERROR 找不到 npm，請安裝 Node.js 20+ & exit /b 1)
where java >nul 2>&1 || (call :LOG_ERROR 找不到 java，請安裝 JDK 21 & exit /b 1)
if not exist "%ROOT_DIR%gradlew.bat" (call :LOG_ERROR 找不到 gradlew.bat & exit /b 1)

call :LOG_SUCCESS 所有工具已就緒
exit /b 0

:BUILD_RELAY
call :LOG_INFO 建置 Relay Server...
cd /d "%ROOT_DIR%relay-server"
if not exist "node_modules" (
    call :LOG_INFO 安裝 npm 依賴...
    npm ci
)
call :LOG_INFO TypeScript 編譯...
npm run build
call :LOG_SUCCESS Relay Server 建置完成
exit /b 0

:BUILD_DESKTOP
call :LOG_INFO 建置 Desktop Agent...
cd /d "%ROOT_DIR%desktop-agent"
call gradlew.bat clean shadowJar
for %%f in (build\libs\opencode-desktop-agent*.jar) do set JAR_PATH=%%f
if defined JAR_PATH (
    call :LOG_SUCCESS Desktop Agent 建置完成: %JAR_PATH%
) else (
    call :LOG_ERROR 找不到建置產物
    exit /b 1
)
exit /b 0

:BUILD_ANDROID
call :LOG_INFO 建置 Android App...
cd /d "%ROOT_DIR%android-app"
call gradlew.bat clean assembleRelease
for %%f in (app\build\outputs\apk\release\*.apk) do set APK_PATH=%%f
if defined APK_PATH (
    call :LOG_SUCCESS Android APK 建置完成: %APK_PATH%
    copy "%APK_PATH%" "%ROOT_DIR%opencode-remote.apk" >nul
    call :LOG_INFO APK 已複製至: %ROOT_DIR%opencode-remote.apk
) else (
    call :LOG_ERROR 找不到 APK 產物
    exit /b 1
)
exit /b 0

:INSTALL_SERVICE
if "%1"=="" (
    call :LOG_ERROR 用法: build-all.bat install-service ^<Relay_URL^> [Token]
    exit /b 1
)
call :LOG_INFO 安裝 Windows Service...
cd /d "%ROOT_DIR%desktop-agent"
if "%2"=="" (
    install-service.bat %1
) else (
    install-service.bat %1 %2
)
call :LOG_SUCCESS Service 安裝完成
exit /b 0

:BUILD_ALL
call :BUILD_RELAY
call :BUILD_DESKTOP
call :BUILD_ANDROID
call :LOG_SUCCESS 所有模組建置完成！
exit /b 0

:CLEAN_ALL
call :LOG_INFO 清理建置產物...
cd /d "%ROOT_DIR%relay-server" && if exist node_modules rmdir /s /q node_modules && if exist dist rmdir /s /q dist
cd /d "%ROOT_DIR%desktop-agent" && call gradlew.bat clean
cd /d "%ROOT_DIR%android-app" && call gradlew.bat clean
call :LOG_SUCCESS 清理完成
exit /b 0

:END