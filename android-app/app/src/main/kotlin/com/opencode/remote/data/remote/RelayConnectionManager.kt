package com.opencode.remote.data.remote

import com.opencode.remote.data.model.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RelayConnection")

/**
 * Relay Server WebSocket 連線管理器
 * 
 * 功能：
 * 1. 建立/維持 WebSocket 連線
 * 2. 自動重連、心跳
 * 3. 訊息發送/接收 (Flow 基礎)
 * 4. 連線狀態管理
 */
class RelayConnectionManager(
    private val settings: AppSettings,
) {
    private var httpClient: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    
    // 狀態
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state
    
    private val _incomingMessages = Channel<RelayMessage>(Channel.UNLIMITED)
    val incomingMessages: ReceiveChannel<RelayMessage> = _incomingMessages
    
    private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
    val connectedDevice: StateFlow<DeviceInfo?> = _connectedDevice
    
    private var currentSessionId: String? = null
    private var isAuthenticated = false
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val baseReconnectDelay = 5_000L
    private val maxReconnectDelay = 60_000L
    
    // 待回應的請求
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<RelayMessage>>()
    
    init {
        setupHttpClient()
    }
    
    /** 初始化 HTTP Client */
    private fun setupHttpClient() {
        httpClient = HttpClient(Android) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }
            install(Logging) {
                level = LogLevel.NONE
            }
            expectSuccess = false
        }
    }
    
    /** 連線到 Relay Server */
    suspend fun connect(): Boolean {
        if (_state.value == ConnectionState.CONNECTING) return false
        
        _state.value = ConnectionState.CONNECTING
        
        return try {
            val url = buildWebSocketUrl()
            logger.info("Connecting to $url")
            
            httpClient?.webSocket(
                method = HttpMethod.Get,
                url = url,
                block = { session ->
                    webSocketSession = session
                    logger.debug("WebSocket session established")
                }
            )
            
            // 等待連線建立
            delay(500)
            true
        } catch (e: Exception) {
            logger.warn("Connection failed: ${e.message}")
            false
        }
    }
    
    /** 建立 WebSocket URL */
    private fun buildWebSocketUrl(): String {
        val baseUrl = settings.relayUrl
        val clientId = getClientId()
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}clientId=$clientId&type=mobile&version=1.0.0&platform=android"
    }
    
    /** 取得/產生客戶端 ID */
    private fun getClientId(): String {
        // 實際應從 DataStore 讀取
        return "mobile_${java.util.UUID.randomUUID().toString().substring(0, 8)}"
    }
    
    /** 發送認證 */
    suspend fun authenticate(token: String): Boolean {
        _state.value = ConnectionState.AUTHENTICATING
        
        val authMsg = RelayMessage.Auth(
            payload = AuthPayload(
                token = token,
                clientType = "mobile",
                clientId = getClientId(),
                version = "1.0.0",
            )
        )
        
        return sendAndWaitResponse(authMsg) { response ->
            when (response) {
                is RelayMessage.AuthResponse -> {
                    if (response.payload.success) {
                        isAuthenticated = true
                        currentSessionId = response.payload.sessionId
                        _state.value = if (response.payload.sessionId != null) {
                            ConnectionState.CONNECTED
                        } else {
                            ConnectionState.WAITING_FOR_PEER
                        }
                        
                        response.payload.pairedDevice?.let { device ->
                            _connectedDevice.value = DeviceInfo(
                                clientId = device.clientId,
                                name = device.name,
                                type = device.clientType,
                                version = "1.0.0",
                                platform = "unknown",
                                isOnline = true,
                            )
                        }
                        
                        // 啟動心跳
                        startHeartbeat()
                        true
                    } else {
                        _state.value = ConnectionState.ERROR
                        logger.error("Auth failed: ${response.payload.error}")
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    /** 發送訊息並等待回應 */
    private suspend fun <T : RelayMessage> sendAndWaitResponse(
        message: RelayMessage,
        onResponse: (RelayMessage) -> Boolean
    ): Boolean {
        val requestId = message.requestId
        val completer = CompletableDeferred<RelayMessage>()
        
        pendingRequests[requestId] = completer
        sendRaw(message)
        
        return try {
            val response = withTimeout(10_000) { completer.await() }
            onResponse(response)
        } catch (e: TimeoutCancellationException) {
            logger.warn("Request timeout: $requestId")
            false
        } finally {
            pendingRequests.remove(requestId)
        }
    }
    
    /** 發送原始訊息 */
    private suspend fun sendRaw(message: RelayMessage) {
        if (webSocketSession == null) return
        
        try {
            val json = Json.encodeToString(message)
            webSocketSession?.send(Frame.Text(json))
        } catch (e: Exception) {
            logger.warn("Send failed", e)
        }
    }
    
    /** 啟動接收循環 */
    fun startReceiving(scope: CoroutineScope) {
        scope.launch {
            webSocketSession?.incoming?.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> handleIncomingMessage(frame.readText())
                    is Frame.Close -> {
                        logger.info("WebSocket closed: ${frame.reason}")
                        handleDisconnect()
                    }
                }
            }
        }
    }
    
    /** 處理接收到的訊息 */
    private fun handleIncomingMessage(text: String) {
        try {
            val json = Json.decodeFromJsonElement(text).jsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: "unknown"
            
            // 解析為對應類型
            val message = when (type) {
                "auth_response" -> Json.decodeFromString(RelayMessage.AuthResponse.serializer(), text)
                "paired" -> Json.decodeFromString(RelayMessage.Paired.serializer(), text)
                "peer_disconnected" -> Json.decodeFromString(RelayMessage.PeerDisconnected.serializer(), text)
                "output" -> Json.decodeFromString(RelayMessage.Output.serializer(), text)
                "heartbeat_ack" -> Json.decodeFromString(RelayMessage.HeartbeatAck.serializer(), text)
                "error" -> Json.decodeFromString(RelayMessage.Error.serializer(), text)
                "welcome" -> Json.decodeFromString(RelayMessage.Welcome.serializer(), text)
                "session_list" -> Json.decodeFromString(RelayMessage.SessionList.serializer(), text)
                "project_list" -> Json.decodeFromString(RelayMessage.ProjectList.serializer(), text)
                "file_tree" -> Json.decodeFromString(RelayMessage.FileTree.serializer(), text)
                else -> {
                    logger.debug("Unhandled message type: $type")
                    return
                }
            }
            
            // 檢查是否為待回應請求
            message.requestId?.let { requestId ->
                pendingRequests[requestId]?.complete(message)
                pendingRequests.remove(requestId)
                return
            }
            
            // 否則發送到訊息流
            _incomingMessages.trySend(message)
            
        } catch (e: Exception) {
            logger.warn("Failed to parse message: $text", e)
        }
    }
    
    /** 處理斷線 */
    private fun handleDisconnect() {
        _state.value = ConnectionState.DISCONNECTED
        isAuthenticated = false
        currentSessionId = null
        _connectedDevice.value = null
        stopHeartbeat()
        cancelPendingRequests()
        scheduleReconnect()
    }
    
    /** 排程重連 */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var attempts = 0
            while (attempts < maxReconnectAttempts && _state.value != ConnectionState.CONNECTED) {
                attempts++
                _state.value = ConnectionState.RECONNECTING
                
                val delayMs = (baseReconnectDelay * 2.0.pow(attempts - 1)).toLong().coerceAtMost(maxReconnectDelay)
                logger.info("Reconnecting in ${delayMs}ms (attempt $attempts/$maxReconnectAttempts)")
                delay(delayMs)
                
                if (connect()) {
                    // 重新認證
                    if (settings.savedToken.isNotBlank()) {
                        authenticate(settings.savedToken)
                    }
                    break
                }
            }
        }
    }
    
    /** 啟動心跳 */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isConnected) {
                delay(25_000) // 25 秒
                if (isConnected) {
                    sendHeartbeat()
                }
            }
        }
    }
    
    /** 停止心跳 */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    /** 發送心跳 */
    private suspend fun sendHeartbeat() {
        val msg = RelayMessage.Heartbeat(payload = HeartbeatPayload(clientId = getClientId()))
        sendRaw(msg)
    }
    
    /** 發送指令 */
    suspend fun sendCommand(command: String, args: List<String> = emptyList()): Boolean {
        currentSessionId?.let { sessionId ->
            val msg = RelayMessage.Command(
                payload = CommandPayload(
                    sessionId = sessionId,
                    command = command,
                    args = args,
                )
            )
            sendRaw(msg)
            return true
        }
        return false
    }
    
    /** 請求專案列表 */
    suspend fun requestProjects(): Boolean {
        currentSessionId?.let { sessionId ->
            val msg = RelayMessage.ProjectList(
                payload = ProjectListPayload(projects = emptyList()), // 請求用空列表
                requestId = java.util.UUID.randomUUID().toString(),
            )
            sendRaw(msg)
            return true
        }
        return false
    }
    
    /** 取消所有待處理請求 */
    private fun cancelPendingRequests() {
        pendingRequests.values.forEach { it.completeExceptionally(CancellationException("Disconnected")) }
        pendingRequests.clear()
    }
    
    /** 關閉連線 */
    suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
        reconnectJob?.cancel()
        stopHeartbeat()
        cancelPendingRequests()
        
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnect"))
        } catch (e: Exception) {
            logger.debug("Close error", e)
        }
        
        webSocketSession = null
    }
    
    /** 是否已連線 */
    val isConnected: Boolean
        get() = _state.value in setOf(ConnectionState.CONNECTED, ConnectionState.WAITING_FOR_PEER)
    
    /** 清理資源 */
    fun destroy() {
        runBlocking { disconnect() }
        httpClient?.close()
        httpClient = null
    }
}