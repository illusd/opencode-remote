package com.opencode.remote.desktop.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.uri
import pureconfig.ConfigSource
import pureconfig.generic.derivation.default.*
import java.io.File
import java.net.URI
import java.time.Duration

/**
 * 應用程式配置
 * 支援：命令列參數 > 環境變數 > 配置檔案 > 預設值
 */
data class AppConfig(
    /** Relay Server WebSocket URL (wss:// 或 ws://) */
    val relayUrl: String = "wss://your-relay.fly.dev",
    
    /** 配對 Token (首次啟動自動產生並顯示) */
    val token: String = "",
    
    /** OpenCode CLI 可執行檔路徑 */
    val opencodePath: String = "opencode",
    
    /** OpenCode 工作目錄 */
    val workingDir: String = System.getProperty("user.home"),
    
    /** 客戶端唯一 ID (首次啟動自動產生) */
    val clientId: String = "",
    
    /** 客戶端名稱 */
    val clientName: String = "Desktop Agent",
    
    /** 客戶端版本 */
    val version: String = "1.0.0",
    
    /** 重連間隔 (秒) */
    val reconnectIntervalSec: Int = 5,
    
    /** 最大重連次數 (0 = 無限) */
    val maxReconnectAttempts: Int = 0,
    
    /** 心跳間隔 (秒) */
    val heartbeatIntervalSec: Int = 25,
    
    /** MCP 請求超時 (秒) */
    val mcpTimeoutSec: Int = 30,
    
    /** 日誌等級 */
    val logLevel: String = "INFO",
    
    /** 配置檔案路徑 */
    val configFile: File = File(System.getProperty("user.home"), ".opencode-agent/config.conf"),
) {
    /** 驗證配置有效性 */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!relayUrl.startsWith("ws://") && !relayUrl.startsWith("wss://")) {
            errors.add("relayUrl 必須以 ws:// 或 wss:// 開頭")
        }
        if (opencodePath.isBlank()) {
            errors.add("opencodePath 不能為空")
        }
        if (reconnectIntervalSec < 1) {
            errors.add("reconnectIntervalSec 必須 >= 1")
        }
        return errors
    }
    
    /** 儲存配置到檔案 */
    fun save(): Boolean {
        try {
            configFile.parentFile?.mkdirs()
            val hocon = this.toHocon()
            configFile.writeText(hocon)
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /** 轉為 HOCON 格式 */
    fun toHocon(): String {
        return """
            relay-url = "$relayUrl"
            token = "$token"
            opencode-path = "$opencodePath"
            working-dir = "$workingDir"
            client-id = "$clientId"
            client-name = "$clientName"
            version = "$version"
            reconnect-interval-sec = $reconnectIntervalSec
            max-reconnect-attempts = $maxReconnectAttempts
            heartbeat-interval-sec = $heartbeatIntervalSec
            mcp-timeout-sec = $mcpTimeoutSec
            log-level = "$logLevel"
        """.trimIndent()
    }
}

/** 配置載入器 */
object AppConfig {
    private val DEFAULT_CONFIG = AppConfig()
    
    /** 從多種來源載入配置 */
    fun load(args: Array<String>): AppConfig {
        // 1. 解析命令列參數
        val cliConfig = parseCli(args)
        
        // 2. 讀取配置檔案
        val fileConfig = loadFromFile(cliConfig.configFile)
        
        // 3. 合併配置 (CLI > File > Default)
        var config = DEFAULT_CONFIG
        config = config.copy(
            relayUrl = cliConfig.relayUrl ?: fileConfig.relayUrl ?: config.relayUrl,
            token = cliConfig.token ?: fileConfig.token ?: config.token,
            opencodePath = cliConfig.opencodePath ?: fileConfig.opencodePath ?: config.opencodePath,
            workingDir = cliConfig.workingDir ?: fileConfig.workingDir ?: config.workingDir,
            clientId = cliConfig.clientId ?: fileConfig.clientId ?: config.clientId,
            clientName = cliConfig.clientName ?: fileConfig.clientName ?: config.clientName,
            version = cliConfig.version ?: fileConfig.version ?: config.version,
            reconnectIntervalSec = cliConfig.reconnectIntervalSec ?: fileConfig.reconnectIntervalSec ?: config.reconnectIntervalSec,
            maxReconnectAttempts = cliConfig.maxReconnectAttempts ?: fileConfig.maxReconnectAttempts ?: config.maxReconnectAttempts,
            heartbeatIntervalSec = cliConfig.heartbeatIntervalSec ?: fileConfig.heartbeatIntervalSec ?: config.heartbeatIntervalSec,
            mcpTimeoutSec = cliConfig.mcpTimeoutSec ?: fileConfig.mcpTimeoutSec ?: config.mcpTimeoutSec,
            logLevel = cliConfig.logLevel ?: fileConfig.logLevel ?: config.logLevel,
            configFile = cliConfig.configFile ?: fileConfig.configFile ?: config.configFile,
        )
        
        // 4. 環境變數覆蓋
        config = applyEnvOverrides(config)
        
        // 5. 自動產生缺失值
        config = autoGenerate(config)
        
        // 6. 驗證
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("配置錯誤:\n${errors.joinToString("\n")}")
        }
        
        return config
    }
    
    private data class CliConfig(
        val relayUrl: String? = null,
        val token: String? = null,
        val opencodePath: String? = null,
        val workingDir: String? = null,
        val clientId: String? = null,
        val clientName: String? = null,
        val version: String? = null,
        val reconnectIntervalSec: Int? = null,
        val maxReconnectAttempts: Int? = null,
        val heartbeatIntervalSec: Int? = null,
        val mcpTimeoutSec: Int? = null,
        val logLevel: String? = null,
        val configFile: File? = null,
    )
    
    private fun parseCli(args: Array<String>): CliConfig {
        object : CliktCommand("opencode-agent") {
            val relayUrl by option("--relay-url", "-r", help = "Relay Server WebSocket URL").string()
            val token by option("--token", "-t", help = "Pairing token").string()
            val opencodePath by option("--opencode", "-o", help = "OpenCode executable path").file().map { it.absolutePath }
            val workingDir by option("--workdir", "-w", help = "Working directory").file().map { it.absolutePath }
            val clientId by option("--client-id", help = "Client unique ID").string()
            val clientName by option("--client-name", help = "Client display name").string()
            val version by option("--version", help = "Client version").string()
            val reconnectIntervalSec by option("--reconnect-interval", help = "Reconnect interval (seconds)").int()
            val maxReconnectAttempts by option("--max-reconnect", help = "Max reconnect attempts (0=infinite)").int()
            val heartbeatIntervalSec by option("--heartbeat-interval", help = "Heartbeat interval (seconds)").int()
            val mcpTimeoutSec by option("--mcp-timeout", help = "MCP request timeout (seconds)").int()
            val logLevel by option("--log-level", help = "Log level (DEBUG/INFO/WARN/ERROR)").string()
            val configFile by option("--config", "-c", help = "Config file path").file()
            
            override fun run() {}
        }.parse(args).let { cli ->
            CliConfig(
                relayUrl = cli.relayUrl,
                token = cli.token,
                opencodePath = cli.opencodePath,
                workingDir = cli.workingDir,
                clientId = cli.clientId,
                clientName = cli.clientName,
                version = cli.version,
                reconnectIntervalSec = cli.reconnectIntervalSec,
                maxReconnectAttempts = cli.maxReconnectAttempts,
                heartbeatIntervalSec = cli.heartbeatIntervalSec,
                mcpTimeoutSec = cli.mcpTimeoutSec,
                logLevel = cli.logLevel,
                configFile = cli.configFile,
            )
        }
    }
    
    private fun loadFromFile(file: File): AppConfig {
        if (!file.exists()) return DEFAULT_CONFIG
        return try {
            ConfigSource.file(file).loadOrThrow<AppConfig>()
        } catch (e: Exception) {
            DEFAULT_CONFIG
        }
    }
    
    private fun applyEnvOverrides(config: AppConfig): AppConfig {
        return config.copy(
            relayUrl = System.getenv("OPENCODE_RELAY_URL") ?: config.relayUrl,
            token = System.getenv("OPENCODE_TOKEN") ?: config.token,
            opencodePath = System.getenv("OPENCODE_PATH") ?: config.opencodePath,
            workingDir = System.getenv("OPENCODE_WORKDIR") ?: config.workingDir,
            clientId = System.getenv("OPENCODE_CLIENT_ID") ?: config.clientId,
            logLevel = System.getenv("OPENCODE_LOG_LEVEL") ?: config.logLevel,
        )
    }
    
    private fun autoGenerate(config: AppConfig): AppConfig {
        var result = config
        
        // 自動產生 clientId
        if (result.clientId.isBlank()) {
            val storedId = File(System.getProperty("user.home"), ".opencode-agent/client-id").readTextOrNull()?.trim()
            result = result.copy(clientId = storedId ?: java.util.UUID.randomUUID().toString())
            // 儲存以便下次復用
            File(System.getProperty("user.home"), ".opencode-agent/client-id").writeText(result.clientId)
        }
        
        return result
    }
}