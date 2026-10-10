package com.opencode.remote.desktop.service

import com.opencode.remote.desktop.config.AppConfig
import com.opencode.remote.desktop.MainKt
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Windows Service 管理
 * 
 * 使用 WinSW (Windows Service Wrapper) 將 JVM 應用程式安裝為系統服務
 * 需要管理員權限執行安裝/解除安裝
 */
object WindowsService {
    
    private const val SERVICE_ID = "OpenCodeRemoteAgent"
    private const val SERVICE_NAME = "OpenCode Remote Agent"
    private const val SERVICE_DESC = "Allows remote control of OpenCode Desktop from mobile devices"
    
    /** 以 Service 模式運行 (由 WinSW 呼叫) */
    fun run(config: AppConfig) = runBlocking {
        MainKt.runInteractive(config)
    }
    
    /** 安裝 Windows Service */
    fun install(config: AppConfig): Boolean {
        return try {
            // 1. 找到打包後的 fat jar
            val jarPath = findFatJar()
            if (jarPath == null) {
                println("❌ 找不到 fat jar，請先執行 ./gradlew shadowJar")
                return false
            }
            
            // 2. 下載 WinSW (如果不存在)
            val winswPath = ensureWinSW()
            if (winswPath == null) {
                println("❌ 無法取得 WinSW")
                return false
            }
            
            // 3. 產生 WinSW 配置檔
            val configDir = File(System.getProperty("user.home"), ".opencode-agent/service")
            configDir.mkdirs()
            
            val xmlContent = generateWinSWConfig(jarPath, config, configDir)
            val xmlFile = File(configDir, "$SERVICE_ID.xml")
            xmlFile.writeText(xmlContent)
            
            // 4. 複製 WinSW 可執行檔
            val winswExe = File(configDir, "$SERVICE_ID.exe")
            Files.copy(Paths.get(winswPath), winswExe.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            
            // 5. 安裝服務 (需要管理員權限)
            println("🔧 安裝 Windows Service (需要管理員權限)...")
            val pb = ProcessBuilder(winswExe.absolutePath, "install")
                .directory(configDir)
                .inheritIO()
            val process = pb.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                println("✅ 服務安裝成功！")
                println("   服務名稱: $SERVICE_NAME")
                println("   服務 ID: $SERVICE_ID")
                println("   啟動: net start $SERVICE_ID")
                println("   停止: net stop $SERVICE_ID")
                return true
            } else {
                println("❌ 服務安裝失敗 (exit code: $exitCode)")
                return false
            }
        } catch (e: Exception) {
            println("❌ 安裝過程發生錯誤: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /** 解除安裝 Windows Service */
    fun uninstall(): Boolean {
        return try {
            val configDir = File(System.getProperty("user.home"), ".opencode-agent/service")
            val winswExe = File(configDir, "$SERVICE_ID.exe")
            
            if (!winswExe.exists()) {
                println("❌ 找不到服務安裝檔案")
                return false
            }
            
            println("🔧 解除安裝 Windows Service (需要管理員權限)...")
            val pb = ProcessBuilder(winswExe.absolutePath, "uninstall")
                .directory(configDir)
                .inheritIO()
            val process = pb.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                println("✅ 服務已解除安裝")
                return true
            } else {
                println("❌ 解除安裝失敗 (exit code: $exitCode)")
                return false
            }
        } catch (e: Exception) {
            println("❌ 解除安裝過程發生錯誤: ${e.message}")
            return false
        }
    }
    
    /** 啟動服務 */
    fun start(): Boolean = runServiceCommand("start", "啟動服務")
    
    /** 停止服務 */
    fun stop(): Boolean = runServiceCommand("stop", "停止服務")
    
    /** 重啟服務 */
    fun restart(): Boolean {
        stop()
        Thread.sleep(2000)
        return start()
    }
    
    /** 查詢服務狀態 */
    fun status(): Boolean = runServiceCommand("status", "查詢狀態")
    
    private fun runServiceCommand(command: String, action: String): Boolean {
        return try {
            val configDir = File(System.getProperty("user.home"), ".opencode-agent/service")
            val winswExe = File(configDir, "$SERVICE_ID.exe")
            
            if (!winswExe.exists()) {
                println("❌ 服務未安裝")
                return false
            }
            
            println("🔧 $action...")
            val pb = ProcessBuilder(winswExe.absolutePath, command)
                .directory(configDir)
                .inheritIO()
            val process = pb.start()
            val exitCode = process.waitFor()
            
            exitCode == 0
        } catch (e: Exception) {
            println("❌ $action 失敗: ${e.message}")
            false
        }
    }
    
    /** 尋找 fat jar */
    private fun findFatJar(): String? {
        // 嘗試多個可能位置
        val candidates = listOf(
            File("build/libs/opencode-desktop-agent.jar"),
            File("build/libs/opencode-desktop-agent-all.jar"),
            File("../desktop-agent/build/libs/opencode-desktop-agent.jar"),
            File(System.getProperty("user.dir"), "desktop-agent/build/libs/opencode-desktop-agent.jar"),
        )
        
        return candidates.find { it.exists() }?.absolutePath
    }
    
    /** 確保 WinSW 可用 */
    private fun ensureWinSW(): String? {
        val cacheDir = File(System.getProperty("user.home"), ".opencode-agent/winsw")
        cacheDir.mkdirs()
        
        val winswExe = File(cacheDir, "WinSW.NET4.exe")
        if (winswExe.exists()) return winswExe.absolutePath
        
        println("⬇️ 下載 WinSW...")
        return try {
            val url = "https://github.com/winsw/winsw/releases/download/v3.4.0/WinSW.NET4.exe"
            val connection = java.net.URL(url).openConnection()
            connection.connect()
            Files.copy(connection.getInputStream(), winswExe.toPath())
            println("✅ WinSW 下載完成")
            winswExe.absolutePath
        } catch (e: Exception) {
            println("❌ WinSW 下載失敗: ${e.message}")
            null
        }
    }
    
    /** 產生 WinSW XML 配置 */
    private fun generateWinSWConfig(jarPath: String, config: AppConfig, configDir: File): String {
        val logDir = File(configDir, "logs")
        logDir.mkdirs()
        
        // 產生應用程式參數
        val args = mutableListOf<String>()
        args.add("--service")
        args.add("--relay-url=${config.relayUrl}")
        if (config.token.isNotBlank()) args.add("--token=${config.token}")
        args.add("--opencode=${config.opencodePath}")
        args.add("--workdir=${config.workingDir}")
        args.add("--client-id=${config.clientId}")
        args.add("--client-name=${config.clientName}")
        args.add("--log-level=${config.logLevel}")
        
        return """
            <service>
                <id>$SERVICE_ID</id>
                <name>$SERVICE_NAME</name>
                <description>$SERVICE_DESC</description>
                <executable>java</executable>
                <arguments>-jar "$jarPath" ${args.joinToString(" ")}</arguments>
                <logmode>rotate</logmode>
                <logpath>$logDir</logpath>
                <log><![CDATA[<configuration>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <file>$logDir/%service%.log</file>
                        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                            <fileNamePattern>$logDir/%service%-%d{yyyy-MM-dd}.log</fileNamePattern>
                            <maxHistory>30</maxHistory>
                        </rollingPolicy>
                        <encoder>
                            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                        </encoder>
                    </appender>
                    <root level="${config.logLevel}">
                        <appender-ref ref="FILE" />
                    </root>
                </configuration>]]></log>
                <onfailure action="restart" delay="10 sec" />
                <startmode>Automatic</startmode>
                <delayedAutoStart>true</delayedAutoStart>
            </service>
        """.trimIndent()
    }
}