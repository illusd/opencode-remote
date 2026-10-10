package com.opencode.remote.data.model

import kotlinx.serialization.Serializable

/**
 * Relay 協定訊息類型
 */
@Serializable
sealed class RelayMessage(
    val type: String,
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    @Serializable
    data class Auth(
        val payload: AuthPayload,
        override val type: String = "auth",
        override val requestId: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class AuthResponse(
        val payload: AuthResponsePayload,
        override val type: String = "auth_response",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class Command(
        val payload: CommandPayload,
        override val type: String = "command",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class Output(
        val payload: OutputPayload,
        override val type: String = "output",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class Paired(
        val payload: PairedPayload,
        override val type: String = "paired",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class PeerDisconnected(
        val payload: PeerDisconnectedPayload,
        override val type: String = "peer_disconnected",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class Heartbeat(
        val payload: HeartbeatPayload,
        override val type: String = "heartbeat",
        override val requestId: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class HeartbeatAck(
        val payload: HeartbeatAckPayload,
        override val type: String = "heartbeat_ack",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class Error(
        val payload: ErrorPayload,
        override val type: String = "error",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class Welcome(
        val payload: WelcomePayload,
        override val type: String = "welcome",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class SessionList(
        val payload: SessionListPayload,
        override val type: String = "session_list",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class SessionCreate(
        val payload: SessionCreatePayload,
        override val type: String = "session_create",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class SessionClose(
        val payload: SessionClosePayload,
        override val type: String = "session_close",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class ProjectList(
        val payload: ProjectListPayload,
        override val type: String = "project_list",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class FileTree(
        val payload: FileTreePayload,
        override val type: String = "file_tree",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class FileRead(
        val payload: FileReadPayload,
        override val type: String = "file_read",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)

    @Serializable
    data class FileWrite(
        val payload: FileWritePayload,
        override val type: String = "file_write",
        override val requestId: String,
        override val timestamp: Long,
    ) : RelayMessage(type, requestId, timestamp)
}

// ===== Payload 定義 =====

@Serializable
data class AuthPayload(
    val token: String,
    val clientType: String, // "mobile" | "desktop"
    val clientId: String,
    val version: String,
)

@Serializable
data class AuthResponsePayload(
    val success: Boolean,
    val sessionId: String? = null,
    val pairedDevice: PairedDevice? = null,
    val error: String? = null,
)

@Serializable
data class PairedDevice(
    val clientId: String,
    val clientType: String,
    val name: String,
)

@Serializable
data class CommandPayload(
    val sessionId: String,
    val command: String,
    val args: List<String> = emptyList(),
    val cwd: String? = null,
)

@Serializable
data class OutputPayload(
    val sessionId: String,
    val stream: String, // "stdout" | "stderr" | "system"
    val data: String,
    val isComplete: Boolean,
    val exitCode: Int? = null,
)

@Serializable
data class PairedPayload(
    val sessionId: String,
    val peer: PairedDevice,
)

@Serializable
data class PeerDisconnectedPayload(
    val sessionId: String,
    val peerType: String,
)

@Serializable
data class HeartbeatPayload(
    val clientId: String,
)

@Serializable
data class HeartbeatAckPayload(
    val serverTime: Long,
)

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
    val details: String? = null,
    val requestId: String? = null,
)

@Serializable
data class WelcomePayload(
    val message: String,
    val clientId: String,
    val serverTime: Long,
)

@Serializable
data class SessionInfo(
    val id: String,
    val name: String,
    val projectPath: String,
    val status: String, // "running" | "idle" | "exited"
    val createdAt: Long,
    val lastActiveAt: Long,
)

@Serializable
data class SessionListPayload(
    val sessions: List<SessionInfo>,
)

@Serializable
data class SessionCreatePayload(
    val projectPath: String,
    val name: String? = null,
)

@Serializable
data class SessionClosePayload(
    val sessionId: String,
    val force: Boolean = false,
)

@Serializable
data class ProjectInfo(
    val path: String,
    val name: String,
    val gitBranch: String? = null,
    val gitStatus: String? = null, // "clean" | "dirty" | "unknown"
    val lastOpened: Long,
)

@Serializable
data class ProjectListPayload(
    val projects: List<ProjectInfo>,
)

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val modifiedAt: Long,
    val children: List<FileNode>? = null,
)

@Serializable
data class FileTreePayload(
    val projectPath: String,
    val tree: List<FileNode>,
)

@Serializable
data class FileReadPayload(
    val path: String,
    val encoding: String = "utf-8",
)

@Serializable
data class FileReadResponsePayload(
    val path: String,
    val content: String,
    val encoding: String,
)

@Serializable
data class FileWritePayload(
    val path: String,
    val content: String,
    val encoding: String = "utf-8",
)

// ===== 連線狀態 =====
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    AUTHENTICATED,
    WAITING_FOR_PEER,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

// ===== 裝置資訊 =====
@Serializable
data class DeviceInfo(
    val clientId: String,
    val name: String,
    val type: String, // "mobile" | "desktop"
    val version: String,
    val platform: String,
    var lastSeen: Long = System.currentTimeMillis(),
    var isOnline: Boolean = false,
)

// ===== App 設定 =====
@Serializable
data class AppSettings(
    val relayUrl: String = "",
    val savedToken: String = "",
    val autoConnect: Boolean = true,
    val theme: String = "system", // "light" | "dark" | "system"
    val fontSize: Float = 14f,
    val fontFamily: String = "monospace",
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val keepScreenOn: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
)