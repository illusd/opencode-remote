package com.opencode.remote.data.repository

import com.opencode.remote.data.local.*
import com.opencode.remote.data.model.*
import com.opencode.remote.data.remote.RelayConnectionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * 應用程式資料倉庫
 * 整合本地資料庫、遠端連線、設定儲存
 */
class AppRepository(
    private val database: AppDatabase,
    private val connectionManager: RelayConnectionManager,
    private val settingsStore: SettingsDataStore,
) {
    
    // ===== 連線狀態 =====
    val connectionState: StateFlow<ConnectionState> = connectionManager.state
    val connectedDevice: StateFlow<DeviceInfo?> = connectionManager.connectedDevice
    val incomingMessages: Flow<RelayMessage> = connectionManager.incomingMessages
    
    // ===== 會話管理 =====
    val sessionsFlow: Flow<List<SessionInfo>> = database.sessionDao().getAll()
        .map { entities -> entities.map { it.toSessionInfo() } }
    
    suspend fun getSession(sessionId: String): SessionInfo? {
        return database.sessionDao().getById(sessionId)?.toSessionInfo()
    }
    
    suspend fun createSession(name: String, projectPath: String): SessionInfo {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = SessionEntity(
            sessionId = sessionId,
            name = name,
            projectPath = projectPath,
            status = "running",
            createdAt = now,
            lastActiveAt = now,
        )
        database.sessionDao().insert(entity)
        return entity.toSessionInfo()
    }
    
    suspend fun updateSessionStatus(sessionId: String, status: String) {
        val now = System.currentTimeMillis()
        database.sessionDao().touch(sessionId, now)
        // 實際應更新 status 欄位，這裡簡化
    }
    
    suspend fun deleteSession(sessionId: String) {
        database.sessionDao().delete(sessionId)
        database.commandHistoryDao().deleteBySession(sessionId)
    }
    
    // ===== 指令歷史 =====
    fun getCommandHistory(sessionId: String, limit: Int = 100, offset: Int = 0): Flow<List<CommandHistoryEntity>> {
        return database.commandHistoryDao().getBySession(sessionId, limit, offset)
    }
    
    suspend fun searchCommandHistory(sessionId: String, query: String, limit: Int = 50): List<CommandHistoryEntity> {
        return database.commandHistoryDao().searchBySession(sessionId, "%$query%", limit).first()
    }
    
    suspend fun addCommandHistory(
        sessionId: String,
        command: String,
        args: List<String>,
        output: String?,
        exitCode: Int?,
        isComplete: Boolean,
    ) {
        val entity = CommandHistoryEntity(
            sessionId = sessionId,
            command = command,
            argsJson = Json.encodeToString(args),
            output = output,
            exitCode = exitCode,
            timestamp = System.currentTimeMillis(),
            isComplete = isComplete,
        )
        database.commandHistoryDao().insert(entity)
    }
    
    suspend fun clearCommandHistory(sessionId: String) {
        database.commandHistoryDao().deleteBySession(sessionId)
    }
    
    // ===== 專案管理 =====
    val projectsFlow: Flow<List<ProjectEntity>> = database.projectDao().getAll()
    
    suspend fun saveProject(project: ProjectEntity) {
        database.projectDao().insert(project.copy(lastOpened = System.currentTimeMillis()))
    }
    
    suspend fun getProject(path: String): ProjectEntity? {
        return database.projectDao().getByPath(path)
    }
    
    suspend fun deleteProject(path: String) {
        database.projectDao().delete(path)
    }
    
    // ===== 配對裝置 =====
    val pairedDevicesFlow: Flow<List<PairedDeviceEntity>> = database.pairedDeviceDao().getAll()
    
    suspend fun savePairedDevice(device: DeviceInfo, tokenHash: String?) {
        val entity = PairedDeviceEntity(
            clientId = device.clientId,
            name = device.name,
            type = device.type,
            version = device.version,
            platform = device.platform,
            lastPairedAt = System.currentTimeMillis(),
            tokenHash = tokenHash,
        )
        database.pairedDeviceDao().insert(entity)
    }
    
    suspend fun deletePairedDevice(clientId: String) {
        database.pairedDeviceDao().delete(clientId)
    }
    
    // ===== 設定 =====
    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow
    
    suspend fun getSettings(): AppSettings = settingsStore.getSettings()
    
    suspend fun updateSettings(block: AppSettings.() -> Unit) {
        settingsStore.updateSettings(block)
    }
    
    // ===== 連線控制 =====
    suspend fun connect(): Boolean {
        return connectionManager.connect()
    }
    
    suspend fun authenticate(token: String): Boolean {
        val result = connectionManager.authenticate(token)
        if (result) {
            // 儲存 token
            settingsStore.updateSettings { savedToken = token }
        }
        return result
    }
    
    suspend fun disconnect() {
        connectionManager.disconnect()
    }
    
    suspend fun sendCommand(command: String, args: List<String> = emptyList()): Boolean {
        return connectionManager.sendCommand(command, args)
    }
    
    suspend fun requestProjects(): Boolean {
        return connectionManager.requestProjects()
    }
    
    // ===== 生命週期 =====
    fun destroy() {
        connectionManager.destroy()
    }
}