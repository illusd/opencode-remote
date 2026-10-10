# OpenCode Remote Control

手機遠端控制電腦上 OpenCode Desktop 的完整解決方案。

## 架構概覽

```
┌─────────────┐   WSS (Token)   ┌──────────────┐   MCP/stdio    ┌──────────────────┐
│  Android    │ ◄─────────────►  │  Vercel      │ ◄────────────►  │  Desktop Agent   │
│  App        │   雲端中繼       │  Relay       │   JSON-RPC     │  (Windows Service)│
│  (Compose)  │                  │  Server      │                │                  │
└─────────────┘                  └──────────────┘                └────────┬─────────┘
                                                                          │
                                                                    ┌─────▼─────┐
                                                                    │ OpenCode  │
                                                                    │  Desktop  │
                                                                    └───────────┘
```

## 三大模組

### 1. Relay Server (`relay-server/`)
- **部署**: Vercel (免費、自動 HTTPS、WebSocket 支援)
- **功能**: Token 配對驗證、雙向訊息轉發、連線心跳、重連支援
- **技術**: Node.js + `ws` + Vercel Serverless Functions

### 2. Desktop Agent (`desktop-agent/`)
- **運行**: Windows System Service (NSSM 或 WinSW)
- **功能**: 
  - 啟動/管理 OpenCode 子進程 (stdio MCP)
  - MCP JSON-RPC 協定處理
  - 與 Relay Server WebSocket 通訊
  - Token 配對、自動重連、斷線恢復
- **技術**: Kotlin/JVM (Ktor Client + kotlinx.coroutines) 或 Go

### 3. Android App (`android-app/`)
- **技術**: Kotlin + Jetpack Compose + Material3
- **架構**: MVVM + Clean Architecture + Hilt DI
- **核心畫面**:
  - 連線設定 / Token 配對
  - 專案列表 / Session 管理
  - 終端機 (完整 ANSI 支援、歷史、Tab 補全)
  - 檔案瀏覽器 (樹狀、編輯、上傳下載)
  - 設定 (主題、字體、快捷鍵、通知)
  - 指令歷史 / 搜尋 / 匯出

## 快速開始

### 前置需求
- Node.js 20+ (Relay Server)
- JDK 21 + Android Studio (Android App)
- Windows 10/11 (Desktop Agent)
- OpenCode CLI 已安裝並可用

### 1. 部署 Relay Server
```bash
cd relay-server
npm install
vercel deploy --prod
# 記下部署網址: https://your-relay.vercel.app
```

### 2. 安裝 Desktop Agent
```bash
cd desktop-agent
./install-service.bat https://your-relay.vercel.app
# 產生 Token 給手機配對
```

### 3. 建置 Android App
```bash
cd android-app
./gradlew assembleRelease
# 安裝 app/build/outputs/apk/release/app-release.apk 到手機
```

## 專案結構

```
opencode-remote/
├── relay-server/           # Vercel WebSocket 中繼伺服器
│   ├── api/
│   │   └── relay.ts       # WebSocket endpoint
│   ├── lib/
│   │   ├── auth.ts        # Token 驗證
│   │   ├── sessions.ts    # 會話管理
│   │   └── protocol.ts    # 訊息協定定義
│   ├── package.json
│   ├── vercel.json
│   └── tsconfig.json
│
├── desktop-agent/          # Windows 系統服務
│   ├── src/main/kotlin/
│   │   ├── main.kt
│   │   ├── mcp/
│   │   │   ├── client.kt       # MCP stdio client
│   │   │   ├── protocol.kt     # MCP 協定定義
│   │   │   └── handlers.kt     # 請求處理器
│   │   ├── relay/
│   │   │   ├── client.kt       # WebSocket client
│   │   │   └── messages.kt     # Relay 協定
│   │   ├── service/
│   │   │   ├── windows.kt      # Windows Service 安裝
│   │   │   └── lifecycle.kt    # 生命週期管理
│   │   └── config.kt           # 設定管理
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── install-service.bat
│
└── android-app/            # Android 手機 App
    ├── app/src/main/kotlin/com/opencode/remote/
    │   ├── ui/
    │   │   ├── screens/
    │   │   │   ├── pairing/        # Token 配對
    │   │   │   ├── projects/       # 專案列表
    │   │   │   ├── sessions/       # Session 管理
    │   │   │   ├── terminal/       # 終端機
    │   │   │   ├── files/          # 檔案瀏覽
    │   │   │   └── settings/       # 設定
    │   │   ├── components/         # 共用 UI 元件
    │   │   └── theme/              # Material3 主題
    │   ├── data/
    │   │   ├── repository/         # 資料倉庫
    │   │   ├── remote/             # WebSocket 連線
    │   │   └── local/              # Room DB / DataStore
    │   ├── domain/                 # Use Cases
    │   └── di/                     # Hilt Modules
    ├── build.gradle.kts
    └── gradle.properties
```

## 協定規格

### Relay 訊息格式
```json
{
  "type": "auth|command|output|heartbeat|error",
  "payload": {},
  "requestId": "uuid",
  "timestamp": 1234567890
}
```

### MCP 轉發
- Desktop Agent 將手機指令轉為 MCP `tools/call` 請求
- OpenCode 回應透過 MCP `notifications/message` 回傳
- Agent 再封裝為 Relay `output` 訊息送回手機

## 安全性
- **Token 配對**: 256-bit 隨機 Token，首次連線時輸入，後續自動儲存
- **加密傳輸**: WSS (TLS 1.3) 端到端加密
- **無公開端口**: 所有連線主動向外發起，不需開放防火牆

## 授權
MIT License