package com.opencode.remote.desktop.relay

import com.opencode.remote.desktop.config.AppConfig
import com.opencode.remote.desktop.mcp.McpClient
import com.opencode.remote.desktop.mcp.McpRequest
import com.opencode.remote.desktop.mcp.McpResponse
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RelayClient")

/**
 * Relay Server WebSocket Client
 * 
 * 功能：
 * 1. 連線到 Relay Server (支援 wss://)
 * 2. Token 配對認證
 * 3. 雙向訊息轉發：手機指令 -> MCP -> OpenCode -> MCP -> 手機輸出
 * 4. 自動重連、心跳、斷線恢復
 */
class RelayClient(
    private val config: AppConfig,
    private val mcpClient: McpClient,
) {
    private var httpClient: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    
    private val sendChannel = Channel<String>(Channel.UNLIMITED)
    private val reconnectJob: Job? = null
    private var isConnected = false
    private var isAuthenticated = false
    private var currentSessionId: String? = null
    private var reconnectAttempts = 0
    private val connectionMutex = Mutex()
    
    // 訊息處理器
    private val messageHandlers = mutableMapOf<String, (JsonObject) -> Unit>()
    
    init {
        registerDefaultHandlers()
    }
    
    /** 註冊預設訊息處理器 */
    private fun registerDefaultHandlers() {
        messageHandlers["auth_response"] = { handleAuthResponse(it) }
        messageHandlers["command"] = { handleCommand(it) }
        messageHandlers["paired"] = { handlePaired(it) }
        messageHandlers["peer_disconnected"] = { handlePeerDisconnected(it) }
        messageHandlers["heartbeat_ack"] = { /* 心跳回應，更新時間戳 */ }
        messageHandlers["error"] = { handleError(it) }
        messageHandlers["welcome"] = { logger.info("Server welcome: ${it.get("message")?.jsonPrimitive?.content}") }
    }
    
    /** 連線到 Relay Server (含自動重連) */
    suspend fun connect(): Boolean = connectionMutex.withLock {
        if (isConnected) return@withLock true
        
        return@withLock try {
            setupHttpClient()
            
            while (true) {
                if (attemptConnection()) {
                    isConnected = true
                    reconnectAttempts = 0
                    logger.info("Connected to relay server")
                    
                    // 發送認證
                    sendAuth()
                    
                    // 啟動訊息接收循環
                    receiveLoop()
                    
                    // 連線斷開後跳出
                    break
                }
                
                // 重連邏輯
                if (!shouldReconnect()) {
                    logger.error("Max reconnect attempts reached, giving up")
                    return false
                }
                
                val delay = config.reconnectIntervalSec * 1000L
                logger.warn("Connection failed, reconnecting in ${config.reconnectIntervalSec}s... (attempt $reconnectAttempts)")
                delay(delay)
            }
            
            true
        } catch (e: Exception) {
            logger.error("Relay client error", e)
            false
        } finally {
            isConnected = false
            cleanup()
        }
    }
    
    /** 建立 HTTP Client */
    private fun setupHttpClient() {
        httpClient = HttpClient(CIO) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.NONE // 生產環境關閉
            }
            expectSuccess = false
        }
    }
    
    /** 嘗試建立 WebSocket 連線 */
    private suspend fun attemptConnection(): Boolean {
        return try {
            val url = buildWebSocketUrl()
            logger.debug("Connecting to $url")
            
            httpClient?.webSocket(
                method = HttpMethod.Get,
                url = url,
                block = { session ->
                    webSocketSession = session
                    logger.debug("WebSocket session established")
                }
            )
            true
        } catch (e: Exception) {
            logger.warn("WebSocket connection failed: ${e.message}")
            false
        }
    }
    
    /** 建立 WebSocket URL (帶查詢參數) */
    private fun buildWebSocketUrl(): String {
        val baseUrl = config.relayUrl
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}clientId=${config.clientId}&type=desktop&version=${config.version}&platform=windows"
    }
    
    /** 發送認證訊息 */
    private suspend fun sendAuth() {
        val authMsg = JsonObject(
            "type" to JsonPrimitive("auth"),
            "requestId" to JsonPrimitive(java.util.UUID.randomUUID().toString()),
            "timestamp" to JsonPrimitive(System.currentTimeMillis()),
            "payload" to JsonObject(
                "token" to JsonPrimitive(config.token),
                "clientType" to JsonPrimitive("desktop"),
                "clientId" to JsonPrimitive(config.clientId),
                "version" to JsonPrimitive(config.version),
            )
        )
        sendRaw(authMsg.toString())
    }
    
    /** 訊息接收循環 */
    private suspend fun receiveLoop() {
        webSocketSession?.incoming?.consumeEach { frame ->
            when (frame) {
                is Frame.Text -> handleIncomingMessage(frame.readText())
                is Frame.Binary -> logger.warn("Received binary frame (unexpected)")
                is Frame.Close -> {
                    logger.info("WebSocket closed: ${frame.reason}")
                    return@consumeEach
                }
            }
        }
    }
    
    /** 處理接收到的訊息 */
    private fun handleIncomingMessage(text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: "unknown"
            val requestId = json["requestId"]?.jsonPrimitive?.content
            
            logger.trace("Received: $type (requestId: $requestId)")
            
            messageHandlers[type]?.invoke(json)
            
        } catch (e: Exception) {
            logger.warn("Failed to handle message: $text", e)
        }
    }
    
    /** 處理認證回應 */
    private fun handleAuthResponse(json: JsonObject) {
        val payload = json["payload"]?.jsonObject ?: return
        val success = payload["success"]?.jsonPrimitive?.boolean ?: false
        
        if (success) {
            isAuthenticated = true
            currentSessionId = payload["sessionId"]?.jsonPrimitive?.content
            
            if (currentSessionId != null) {
                logger.info("Session established: $currentSessionId")
                // 通知 MCP 客戶端會話就緒
            } else {
                logger.info("Authenticated, waiting for peer...")
            }
        } else {
            val error = payload["error"]?.jsonPrimitive?.content ?: "Unknown error"
            logger.error("Authentication failed: $error")
            webSocketSession?.close(CloseReason(CloseReason.Codes.POLICY_VIOLATION, "Auth failed"))
        }
    }
    
    /** 處理配對完成 */
    private fun handlePaired(json: JsonObject) {
        val payload = json["payload"]?.jsonObject ?: return
        currentSessionId = payload["sessionId"]?.jsonPrimitive?.content
        val peer = payload["peer"]?.jsonObject
        val peerId = peer?.get("clientId")?.jsonPrimitive?.content
        val peerType = peer?.get("type")?.jsonPrimitive?.content
        
        logger.info("Paired with $peerType client: $peerId")
    }
    
    /** 處理對端斷線 */
    private fun handlePeerDisconnected(json: JsonObject) {
        val payload = json["payload"]?.jsonObject ?: return
        val peerType = payload["peerType"]?.jsonPrimitive?.content
        logger.warn("Peer disconnected: $peerType")
        currentSessionId = null
    }
    
    /** 處理手機發來的指令 */
    private suspend fun handleCommand(json: JsonObject) {
        val payload = json["payload"]?.jsonObject ?: return
        val requestId = json["requestId"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString()
        val command = payload["command"]?.jsonPrimitive?.content ?: ""
        val sessionId = payload["sessionId"]?.jsonPrimitive?.content
        
        if (sessionId != currentSessionId) {
            sendError(requestId, "session_mismatch", "Session ID mismatch")
            return
        }
        
        logger.info("Received command: $command")
        
        // 轉發給 MCP (呼叫 OpenCode 工具)
        // 這裡假設 OpenCode 暴露了 "execute_command" 工具
        launch {
            try {
                val response = mcpClient.callTool("execute_command", mapOf(
                    "command" to command,
                    "sessionId" to sessionId,
                )).await()
                
                // 發送結果回手機
                sendOutput(requestId, sessionId!!, response)
            } catch (e: Exception) {
                logger.error("Command execution failed", e)
                sendError(requestId, "execution_failed", e.message ?: "Unknown error")
            }
        }
    }
    
    /** 發送指令執行結果 */
    private fun sendOutput(requestId: String, sessionId: String, response: McpResponse) {
        val outputMsg = JsonObject(
            "type" to JsonPrimitive("output"),
            "requestId" to JsonPrimitive(requestId),
            "timestamp" to JsonPrimitive(System.currentTimeMillis()),
            "payload" to JsonObject(
                "sessionId" to JsonPrimitive(sessionId),
                "stream" to JsonPrimitive("stdout"),
                "data" to JsonPrimitive(response.result?.toString() ?? ""),
                "isComplete" to JsonPrimitive(true),
                "exitCode" to JsonPrimitive(0),
            )
        )
        sendRaw(outputMsg.toString())
    }
    
    /** 發送錯誤回應 */
    private fun sendError(requestId: String, code: String, message: String) {
        val errorMsg = JsonObject(
            "type" to JsonPrimitive("error"),
            "requestId" to JsonPrimitive(requestId),
            "timestamp" to JsonPrimitive(System.currentTimeMillis()),
            "payload" to JsonObject(
                "code" to JsonPrimitive(code),
                "message" to JsonPrimitive(message),
            )
        )
        sendRaw(errorMsg.toString())
    }
    
    /** 處理伺服器錯誤 */
    private fun handleError(json: JsonObject) {
        val payload = json["payload"]?.jsonObject ?: return
        val code = payload["code"]?.jsonPrimitive?.content ?: "unknown"
        val message = payload["message"]?.jsonPrimitive?.content ?: "unknown"
        logger.error("Server error: $code - $message")
    }
    
    /** 發送原始 JSON 字串 */
    private suspend fun sendRaw(text: String) {
        if (!isConnected || webSocketSession == null) return
        
        try {
            webSocketSession?.send(Frame.Text(text))
        } catch (e: Exception) {
            logger.warn("Failed to send message", e)
        }
    }
    
    /** 發送心跳 */
    suspend fun sendHeartbeat() {
        if (!isConnected) return
        
        val heartbeatMsg = JsonObject(
            "type" to JsonPrimitive("heartbeat"),
            "requestId" to JsonPrimitive(java.util.UUID.randomUUID().toString()),
            "timestamp" to JsonPrimitive(System.currentTimeMillis()),
            "payload" to JsonObject(
                "clientId" to JsonPrimitive(config.clientId),
            )
        )
        sendRaw(heartbeatMsg.toString())
    }
    
    /** 判斷是否應該重連 */
    private fun shouldReconnect(): Boolean {
        if (config.maxReconnectAttempts > 0 && reconnectAttempts >= config.maxReconnectAttempts) {
            return false
        }
        reconnectAttempts++
        return true
    }
    
    /** 清理資源 */
    private fun cleanup() {
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client shutdown"))
        } catch (e: Exception) {
            logger.debug("Error closing WebSocket", e)
        }
        webSocketSession = null
    }
    
    /** 關閉客戶端 */
    suspend fun shutdown() {
        logger.info("Shutting down relay client...")
        cleanup()
        httpClient?.close()
        httpClient = null
    }
}