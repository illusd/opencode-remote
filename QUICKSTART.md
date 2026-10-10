# OpenCode Remote - 快速開始指南

## 🎯 專案概覽

這個專案讓你可以用手機完整控制電腦上的 OpenCode Desktop，包含：
- ✅ 完整終端機操作 (ANSI 色彩、歷史、Tab 補全)
- ✅ 專案管理 (列表、開啟、切換分支)
- ✅ 檔案瀏覽 (樹狀、編輯、上傳下載)
- ✅ 會話管理 (建立、切換、關閉)
- ✅ 雲端中繼 (免費、全球可用、無需開放端口)

## 🏗️ 架構

```
手機 App (Kotlin/Compose) ←→ Vercel/Fly.io Relay ←→ Windows Service ←→ OpenCode (MCP stdio)
```

## 🚀 5 分鐘快速部署

### 1. 部署 Relay Server (選擇一種)

#### 方案 A: Fly.io (推薦，免費、支援 WebSocket、不休眠)
```bash
cd relay-server
flyctl launch --name opencode-relay --region hkg
flyctl deploy
# 記下 URL: https://opencode-relay.fly.dev
```

#### 方案 B: Railway (免費額度夠用)
```bash
cd relay-server
railway login
railway link
railway up
```

#### 方案 C: Render.com (免費但會休眠)
- 連接 GitHub repo 到 Render
- 建立 Web Service，Docker 模式
- 記下 URL

### 2. 安裝 Desktop Agent (Windows)

```powershell
# 1. 下載專案
git clone https://github.com/your-repo/opencode-remote.git
cd opencode-remote

# 2. 建置 Desktop Agent
cd desktop-agent
.\gradlew.bat shadowJar

# 3. 安裝為 Windows Service (需管理員權限)
.\install-service.bat wss://your-relay.fly.dev
# 如果有 Token: .\install-service.bat wss://your-relay.fly.dev YOUR_TOKEN
```

安裝成功後，服務會在背景運行，日誌顯示配對 Token。

### 3. 建置並安裝 Android App

```bash
cd android-app
./gradlew assembleRelease
# APK 位於: app/build/outputs/apk/release/app-release.apk
```

安裝到手機 (需啟用「未知來源應用程式」)。

### 4. 配對完成

1. 開啟手機 App
2. 輸入桌面端日誌顯示的 Token
3. 點擊「開始配對」
4. 完成！現在可以在手機上操作 OpenCode

## 📱 手機 App 功能

### 主畫面
- 連線狀態指示器 (綠=已連線、黃=連線中、紅=錯誤)
- 已配對裝置資訊
- 會話列表 (建立、切換、刪除)
- 快速進入：專案、檔案、終端機

### 終端機
- 完整 ANSI 色彩支援
- 指令歷史 (上下鍵切換)
- Tab 補全
- 複製/貼上
- 清除輸出
- 軟鍵盤工具列 (Ctrl、Alt、Tab、方向鍵)

### 專案管理
- 專案列表 (顯示 Git 分支、狀態)
- 一鍵開啟專案
- 重新整理列表

### 檔案瀏覽
- 樹狀目錄結構
- 檔案內容查看/編輯
- 新增檔案/資料夾
- 刪除、重新命名

## 🔧 進階設定

### Desktop Agent 設定檔
位置: `%USERPROFILE%\.opencode-agent\config.conf`

```hocon
relay-url = "wss://your-relay.fly.dev"
token = "auto-generated-token"
opencode-path = "opencode"
working-dir = "C:\Users\YourName"
client-id = "auto-generated-uuid"
reconnect-interval-sec = 5
heartbeat-interval-sec = 25
log-level = "INFO"
```

### 環境變數覆蓋
```powershell
$env:OPENCODE_RELAY_URL = "wss://your-relay.fly.dev"
$env:OPENCODE_TOKEN = "your-token"
$env:OPENCODE_PATH = "C:\path\to\opencode.exe"
$env:OPENCODE_WORKDIR = "C:\Projects"
```

### 手動啟動/停止 Service
```powershell
# 啟動
net start OpenCodeRemoteAgent

# 停止
net stop OpenCodeRemoteAgent

# 狀態
sc query OpenCodeRemoteAgent

# 解除安裝 (管理員)
.\uninstall-service.bat
```

## 🛠️ 開發指南

### 專案結構
```
opencode-remote/
├── relay-server/          # Node.js + TypeScript (Fly.io/Vercel)
│   ├── api/relay.ts       # WebSocket endpoint
│   ├── lib/               # 協定、認證、會話管理
│   └── standalone-server.ts  # 獨立伺服器模式
├── desktop-agent/         # Kotlin/JVM (Windows Service)
│   ├── src/main/kotlin/
│   │   ├── Main.kt
│   │   ├── config/        # 設定管理
│   │   ├── mcp/           # MCP Client
│   │   ├── relay/         # Relay Client
│   │   └── service/       # Windows Service
│   └── install-service.bat
└── android-app/           # Kotlin + Jetpack Compose
    ├── app/src/main/kotlin/com/opencode/remote/
    │   ├── data/          # Repository, Local, Remote
    │   ├── ui/            # Compose Screens, ViewModels
    │   └── di/            # Hilt Modules
    └── build.gradle.kts
```

### 本地開發

#### Relay Server
```bash
cd relay-server
npm run dev  # 使用 vercel dev 或 tsx watch
```

#### Desktop Agent
```bash
cd desktop-agent
./gradlew run --args="--relay-url=ws://localhost:8080"
```

#### Android App
- 用 Android Studio 開啟 `android-app/`
- 點擊 Run 或 `./gradlew installDebug`

## 🐛 常見問題

### Q: 手機連不上 Relay Server
- 確認 Relay Server URL 正確 (wss:// 開頭)
- 檢查防火牆/網路是否阻擋
- 查看 Relay Server 日誌: `flyctl logs` 或 Railway/Render 日誌

### Q: Desktop Agent 無法啟動 OpenCode
- 確認 `opencode` 在 PATH 中，或設定完整路徑
- 手動執行 `opencode mcp stdio` 測試
- 檢查工作目錄權限

### Q: 配對失敗
- Token 有效期 10 分鐘，過期請重新產生
- 確認雙方使用相同 Token
- 檢查 Relay Server 是否正常運行

### Q: 手機終端機顯示亂碼
- 確認 OpenCode 輸出 ANSI 色彩
- 手機 App 設定中調整字體/編碼

## 🔐 安全性

- **Token 配對**: 256-bit 隨機 Token，一次性使用
- **加密傳輸**: WSS (TLS 1.3) 端到端加密
- **無公開端口**: 所有連線主動向外發起
- **本地儲存**: Token 僅儲存在裝置本地

## 📄 授權

MIT License - 自由使用、修改、分發

## 🤝 貢獻

歡迎提交 Issue 和 PR！

---

**下一步**: 閱讀 [README.md](README.md) 了解完整架構細節