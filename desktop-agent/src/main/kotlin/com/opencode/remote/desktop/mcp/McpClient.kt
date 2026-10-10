package com.opencode.remote.desktop.mcp

import com.opencode.remote.desktop.config.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("McpClient")

/**
 * MCP (Model Context Protocol) Client
 * 
 * 透過 stdio 與 OpenCode 子進程通訊
 * 實作 JSON-RPC 2.0 over stdio
 */
class McpClient(private val config: AppConfig) {
    private var process: Process? = null
    private var stdin: java.io.OutputStream? = null
    private var stdoutReader: java.io.BufferedReader? = null
    private var stderrReader: java.io.BufferedReader? = null
    
    private val requestIdCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<McpResponse>>()
    private val pendingMutex = Mutex()
    
    private val outputChannel = Channel<String>(Channel.UNLIMITED)
    private val errorChannel = Channel<String>(Channel.UNLIMITED)
    private val systemChannel = Channel<String>(Channel.UNLIMITED)
    
    val ready = CompletableDeferred<Unit>()
    private var isStarted = false
    private val startMutex = Mutex()
    
    /** 取得輸出流 (供外部消費) */
    val stdoutFlow = outputChannel.receiveAsFlow()
    val stderrFlow = errorChannel.receiveAsFlow()
    val systemFlow = systemChannel.receiveAsFlow()
    
    /** 啟動 OpenCode 進程並建立 MCP 連線 */
    suspend fun start(): Boolean = startMutex.withLock {
        if (isStarted) return@withLock true
        
        logger.info("Starting OpenCode process: ${config.opencodePath}")
        
        return@withLock try {
            // 啟動 OpenCode 進程
            val pb = ProcessBuilder(config.opencodePath, "mcp", "stdio")
                .directory(java.io.File(config.workingDir))
                .redirectErrorStream(false)
            
            // 設定環境變數
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["FORCE_COLOR"] = "1"
            
            process = pb.start()
            stdin = process.outputStream
            stdoutReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            stderrReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
            
            // 啟動讀取協程
            launch(Dispatchers.IO) { readStdout() }
            launch(Dispatchers.IO) { readStderr() }
            launch(Dispatchers.Default) { processMessages() }
            
            // 等待初始化完成
            initialize().await()
            
            isStarted = true
            ready.complete(Unit)
            logger.info("MCP client started successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to start MCP client", e)
            ready.completeExceptionally(e)
            false
        }
    }
    
    /** 初始化 MCP 連線 */
    private suspend fun initialize(): Deferred<Unit> = CoroutineScope(Dispatchers.Default).async {
        // 發送 initialize 請求
        val initRequest = McpRequest(
            id = nextRequestId(),
            method = "initialize",
            params = mapOf(
                "protocolVersion" to "2024-11-05",
                "capabilities" to mapOf(
                    "tools" to mapOf(),
                    "resources" to mapOf(),
                    "prompts" to mapOf(),
                ),
                "clientInfo" to mapOf(
                    "name" to config.clientName,
                    "version" to config.version,
                )
            )
        )
        
        val response = sendRequest(initRequest).await()
        if (response.error != null) {
            throw RuntimeException("MCP initialize failed: ${response.error?.message}")
        }
        
        // 發送 initialized 通知
        sendNotification("notifications/initialized", mapOf())
    }
    
    /** 讀取 stdout */
    private suspend fun readStdout() {
        stdoutReader?.use { reader ->
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    outputChannel.trySend(line)
                }
            }
        }
        outputChannel.close()
    }
    
    /** 讀取 stderr */
    private suspend fun readStderr() {
        stderrReader?.use { reader ->
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    errorChannel.trySend(line)
                }
            }
        }
        errorChannel.close()
    }
    
    /** 處理接收到的 MCP 訊息 */
    private suspend fun processMessages() {
        for (line in outputChannel) {
            try {
                val message = McpMessage.fromJson(line)
                when (message) {
                    is McpResponse -> handleResponse(message)
                    is McpNotification -> handleNotification(message)
                    is McpRequest -> handleIncomingRequest(message)
                }
            } catch (e: Exception) {
                logger.warn("Failed to parse MCP message: $line", e)
            }
        }
    }
    
    /** 處理回應 */
    private suspend fun handleResponse(response: McpResponse) {
        pendingMutex.withLock {
            pendingRequests.remove(response.id)?.complete(response)
        }
    }
    
    /** 處理通知 (OpenCode 主動推送) */
    private suspend fun handleNotification(notification: McpNotification) {
        when (notification.method) {
            "notifications/message" -> {
                // OpenCode 輸出訊息
                val data = notification.params?.get("data") as? String ?? ""
                val stream = notification.params?.get("stream") as? String ?? "stdout"
                systemChannel.trySend("$stream:$data")
            }
            "notifications/progress" -> {
                // 進度通知
                systemChannel.trySend("progress:${notification.params}")
            }
            else -> {
                logger.debug("Unhandled notification: ${notification.method}")
            }
        }
    }
    
    /** 處理傳入請求 (OpenCode 向我們請求) */
    private suspend fun handleIncomingRequest(request: McpRequest) {
        // 目前不支援伺服器端請求，直接回錯誤
        sendResponse(McpResponse(
            id = request.id,
            error = McpError(-32601, "Method not found: ${request.method}")
        ))
    }
    
    /** 發送請求並等待回應 */
    suspend fun sendRequest(request: McpRequest): Deferred<McpResponse> = CoroutineScope(Dispatchers.Default).async {
        val completer = CompletableDeferred<McpResponse>()
        
        pendingMutex.withLock {
            pendingRequests[request.id] = completer
        }
        
        sendMessage(request)
        
        // 設定超時
        val timeoutJob = launch {
            delay(config.mcpTimeoutSec * 1000L)
            pendingMutex.withLock {
                pendingRequests.remove(request.id)?.completeExceptionally(
                    TimeoutException("MCP request timeout: ${request.method}")
                )
            }
        }
        
        try {
            completer.await()
        } finally {
            timeoutJob.cancel()
        }
    }
    
    /** 發送通知 (無回應) */
    suspend fun sendNotification(method: String, params: Map<String, Any?>): Boolean {
        val notification = McpNotification(method = method, params = params)
        return sendMessage(notification)
    }
    
    /** 呼叫工具 (對應手機指令) */
    suspend fun callTool(name: String, arguments: Map<String, Any?>): McpResponse {
        val request = McpRequest(
            id = nextRequestId(),
            method = "tools/call",
            params = mapOf("name" to name, "arguments" to arguments)
        )
        return sendRequest(request).await()
    }
    
    /** 列出可用工具 */
    suspend fun listTools(): McpResponse {
        val request = McpRequest(
            id = nextRequestId(),
            method = "tools/list",
            params = mapOf()
        )
        return sendRequest(request).await()
    }
    
    /** 發送原始訊息 */
    private fun sendMessage(message: McpMessage): Boolean {
        return try {
            val json = message.toJson()
            stdin?.write("$json\n".toByteArray())
            stdin?.flush()
            true
        } catch (e: Exception) {
            logger.error("Failed to send MCP message", e)
            false
        }
    }
    
    /** 發送回應 (給 OpenCode) */
    private fun sendResponse(response: McpResponse): Boolean {
        return sendMessage(response)
    }
    
    /** 產生下一個請求 ID */
    private fun nextRequestId(): String = requestIdCounter.incrementAndGet().toString()
    
    /** 關閉連線 */
    suspend fun shutdown() {
        logger.info("Shutting down MCP client...")
        
        // 發送 shutdown 通知
        sendNotification("notifications/shutdown", mapOf())
        
        // 關閉進程
        process?.destroy()
        try {
            process?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            process?.destroyForcibly()
        }
        
        // 取消所有待處理請求
        pendingMutex.withLock {
            pendingRequests.values.forEach { it.completeExceptionally(CancellationException("Client shutdown")) }
            pendingRequests.clear()
        }
        
        outputChannel.close()
        errorChannel.close()
        systemChannel.close()
        
        isStarted = false
        logger.info("MCP client stopped")
    }
}

/** MCP 訊息基礎類別 */
sealed interface McpMessage {
    val jsonrpc: String = "2.0"
    fun toJson(): String
    companion object {
        fun fromJson(json: String): McpMessage = kotlinx.serialization.json.Json.decodeFromString(McpMessageSerializer, json)
    }
}

/** MCP 請求 */
@kotlinx.serialization.Serializable
data class McpRequest(
    val id: String,
    val method: String,
    val params: Map<String, Any?> = emptyMap(),
) : McpMessage {
    override fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(McpRequest.serializer(), this)
}

/** MCP 回應 */
@kotlinx.serialization.Serializable
data class McpResponse(
    val id: String,
    val result: Any? = null,
    val error: McpError? = null,
) : McpMessage {
    override fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(McpResponse.serializer(), this)
}

/** MCP 通知 (無 ID) */
@kotlinx.serialization.Serializable
data class McpNotification(
    val method: String,
    val params: Map<String, Any?>? = null,
) : McpMessage {
    override fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(McpNotification.serializer(), this)
}

/** MCP 錯誤 */
@kotlinx.serialization.Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: Any? = null,
)

/** 自定義序列化器處理 Any? 類型 */
object McpMessageSerializer : kotlinx.serialization.KSerializer<McpMessage> {
    override val descriptor = kotlinx.serialization.descriptors.PolymorphicKind.SEALED.classDesc("McpMessage")
    
    override fun serialize(encoder: kotlinx.serialization.Encoder, value: McpMessage) {
        when (value) {
            is McpRequest -> McpRequest.serializer().serialize(encoder, value)
            is McpResponse -> McpResponse.serializer().serialize(encoder, value)
            is McpNotification -> McpNotification.serializer().serialize(encoder, value)
        }
    }
    
    override fun deserialize(decoder: kotlinx.serialization.Decoder): McpMessage {
        // 簡單實作：先解析 JSON 物件，再判斷類型
        val json = kotlinx.serialization.json.Json.decodeFromJsonElement(decoder)
        val obj = json.jsonObject
        
        return when {
            obj.containsKey("method") && !obj.containsKey("id") -> McpNotification.serializer().deserialize(decoder)
            obj.containsKey("method") && obj.containsKey("id") -> McpRequest.serializer().deserialize(decoder)
            else -> McpResponse.serializer().deserialize(decoder)
        }
    }
}