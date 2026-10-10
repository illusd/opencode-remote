package com.opencode.remote.desktop

import com.opencode.remote.desktop.config.AppConfig
import com.opencode.remote.desktop.mcp.McpClient
import com.opencode.remote.desktop.relay.RelayClient
import com.opencode.remote.desktop.service.WindowsService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Main")

/**
 * OpenCode Desktop Agent 主入口
 * 
 * 功能：
 * 1. 讀取配置 (relay URL, token, OpenCode 路徑等)
 * 2. 啟動 OpenCode 子進程並建立 MCP stdio 連線
 * 3. 連線到 Relay Server (WebSocket)
 * 4. 雙向轉發訊息：手機指令 -> MCP -> OpenCode -> MCP -> 手機輸出
 * 5. 支援 Windows Service 模式
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun main(args: Array<String>) = runBlocking {
    val config = AppConfig.load(args)
    
    logger.info("""
        ╔══════════════════════════════════════════════════════════╗
        ║  OpenCode Desktop Agent v${config.version}                        ║
        ║  Relay: ${config.relayUrl}                                  ║
        ║  OpenCode: ${config.opencodePath}                              ║
        ╚══════════════════════════════════════════════════════════╝
    """.trimIndent())
    
    // 檢查是否以 Service 模式運行
    val isService = args.contains("--service") || System.getenv("OPENCODE_SERVICE") == "true"
    
    if (isService) {
        // Windows Service 模式
        WindowsService.run(config)
    } else {
        // 互動模式 (前台運行，方便除錯)
        runInteractive(config)
    }
}

/** 前台互動模式 */
private suspend fun runInteractive(config: AppConfig) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 啟動 MCP Client (連接 OpenCode)
    val mcpClient = McpClient(config)
    val mcpJob = scope.launch { mcpClient.start() }
    
    // 等待 MCP 就緒
    mcpClient.ready.await()
    logger.info("MCP client ready")
    
    // 啟動 Relay Client (連接雲端)
    val relayClient = RelayClient(config, mcpClient)
    val relayJob = scope.launch { relayClient.connect() }
    
    // 等待關閉信號
    awaitShutdownSignal()
    
    logger.info("Shutting down...")
    relayJob.cancel()
    mcpJob.cancel()
    scope.coroutineContext.cancelChildren()
}

/** 等待關閉信號 (Ctrl+C) */
private suspend fun awaitShutdownSignal() {
    val signalJob = Job()
    Runtime.getRuntime().addShutdownHook(Thread { signalJob.cancel() })
    try {
        signalJob.join()
    } catch (e: CancellationException) {
        // 正常關閉
    }
}