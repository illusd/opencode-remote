#!/usr/bin/env bash
# OpenCode Remote - 完整建置腳本
# 用法: ./build-all.sh [clean|build|install|deploy]

set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# 顏色輸出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 檢查必要工具
check_tools() {
    log_info "檢查建置工具..."
    
    local missing=()
    
    command -v node >/dev/null 2>&1 || missing+=("node")
    command -v npm >/dev/null 2>&1 || missing+=("npm")
    command -v java >/dev/null 2>&1 || missing+=("java")
    command -v ./gradlew >/dev/null 2>&1 || missing+=("gradlew")
    
    if [ ${#missing[@]} -ne 0 ]; then
        log_error "缺少必要工具: ${missing[*]}"
        log_info "請安裝: Node.js 20+, JDK 21, Android Studio"
        exit 1
    fi
    
    log_success "所有工具已就緒"
}

# 建置 Relay Server
build_relay() {
    log_info "建置 Relay Server..."
    cd "$ROOT_DIR/relay-server"
    
    if [ ! -d "node_modules" ]; then
        log_info "安裝 npm 依賴..."
        npm ci
    fi
    
    log_info "TypeScript 編譯..."
    npm run build
    
    log_success "Relay Server 建置完成"
}

# 部署 Relay Server 到 Fly.io
deploy_relay_fly() {
    log_info "部署 Relay Server 到 Fly.io..."
    cd "$ROOT_DIR/relay-server"
    
    if ! command -v flyctl >/dev/null 2>&1; then
        log_warn "flyctl 未安裝，嘗試安裝..."
        curl -L https://fly.io/install.sh | sh
        export PATH="$HOME/.fly/bin:$PATH"
    fi
    
    flyctl deploy
    log_success "Relay Server 部署完成"
}

# 建置 Desktop Agent
build_desktop() {
    log_info "建置 Desktop Agent..."
    cd "$ROOT_DIR/desktop-agent"
    
    ./gradlew clean shadowJar
    
    local jar_path=$(find build/libs -name "opencode-desktop-agent*.jar" | head -1)
    if [ -n "$jar_path" ]; then
        log_success "Desktop Agent 建置完成: $jar_path"
    else
        log_error "找不到建置產物"
        exit 1
    fi
}

# 安裝 Windows Service
install_service() {
    local relay_url="$1"
    local token="$2"
    
    if [ -z "$relay_url" ]; then
        log_error "用法: $0 install <Relay_URL> [Token]"
        exit 1
    fi
    
    log_info "安裝 Windows Service..."
    cd "$ROOT_DIR/desktop-agent"
    
    if [ -n "$token" ]; then
        ./install-service.bat "$relay_url" "$token"
    else
        ./install-service.bat "$relay_url"
    fi
    
    log_success "Service 安裝完成"
}

# 建置 Android App
build_android() {
    log_info "建置 Android App..."
    cd "$ROOT_DIR/android-app"
    
    ./gradlew clean assembleRelease
    
    local apk_path=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
    if [ -n "$apk_path" ]; then
        log_success "Android APK 建置完成: $apk_path"
        
        # 複製到根目錄方便取得
        cp "$apk_path" "$ROOT_DIR/opencode-remote.apk"
        log_info "APK 已複製至: $ROOT_DIR/opencode-remote.apk"
    else
        log_error "找不到 APK 產物"
        exit 1
    fi
}

# 建置所有
build_all() {
    check_tools
    build_relay
    build_desktop
    build_android
    log_success "所有模組建置完成！"
}

# 清理
clean_all() {
    log_info "清理建置產物..."
    cd "$ROOT_DIR/relay-server" && rm -rf node_modules dist
    cd "$ROOT_DIR/desktop-agent" && ./gradlew clean
    cd "$ROOT_DIR/android-app" && ./gradlew clean
    log_success "清理完成"
}

# 主程式
case "${1:-build}" in
    clean)
        clean_all
        ;;
    build)
        build_all
        ;;
    relay)
        build_relay
        ;;
    deploy-relay)
        deploy_relay_fly
        ;;
    desktop)
        build_desktop
        ;;
    install-service)
        install_service "$2" "$3"
        ;;
    android)
        build_android
        ;;
    *)
        echo "用法: $0 {clean|build|relay|deploy-relay|desktop|install-service|android}"
        echo ""
        echo "指令:"
        echo "  clean           - 清理所有建置產物"
        echo "  build           - 建置所有模組 (預設)"
        echo "  relay           - 僅建置 Relay Server"
        echo "  deploy-relay    - 部署 Relay Server 到 Fly.io"
        echo "  desktop         - 僅建置 Desktop Agent"
        echo "  install-service - 安裝 Windows Service (需 Relay URL)"
        echo "  android         - 僅建置 Android App"
        exit 1
        ;;
esac