package com.opencode.remote.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.remote.data.model.ConnectionState
import com.opencode.remote.data.model.DeviceInfo
import com.opencode.remote.data.model.SessionInfo
import com.opencode.remote.ui.theme.OpenCodeTheme
import com.opencode.remote.ui.theme.Typography

/**
 * 主畫面
 * 顯示連線狀態、會話列表、快速操作
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSessionClick: (SessionInfo) -> Unit,
    onNewSession: () -> Unit,
    onProjectsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    
    Scaffold(
        topBar = {
            MainTopAppBar(
                connectionState = connectionState,
                connectedDevice = connectedDevice,
                onSettingsClick = onSettingsClick,
            )
        },
        floatingActionButton = {
            if (connectionState == ConnectionState.CONNECTED) {
                FloatingActionButton(
                    onClick = onNewSession,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "新增會話",
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                connectionState == ConnectionState.DISCONNECTED -> DisconnectedView(onConnect = { viewModel.connect() })
                connectionState == ConnectionState.CONNECTING -> LoadingView("連線中...")
                connectionState == ConnectionState.AUTHENTICATING -> LoadingView("認證中...")
                connectionState == ConnectionState.WAITING_FOR_PEER -> WaitingForPeerView()
                connectionState == ConnectionState.CONNECTED -> ConnectedView(
                    device = connectedDevice,
                    sessions = sessions,
                    onSessionClick = onSessionClick,
                    onProjectsClick = onProjectsClick,
                    selectedSessionId = uiState.selectedSessionId,
                )
                connectionState == ConnectionState.ERROR -> ErrorView(
                    message = uiState.error ?: "連線錯誤",
                    onRetry = { viewModel.connect() },
                )
                else -> DisconnectedView(onConnect = { viewModel.connect() })
            }
            
            // 配對對話框
            if (uiState.showPairingDialog) {
                PairingDialog(
                    onPair = { token -> viewModel.authenticate(token) },
                    onDismiss = { viewModel.hidePairingDialog() },
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                )
            }
        }
    }
}

/** 頂部應用列 */
@Composable
fun MainTopAppBar(
    connectionState: ConnectionState,
    connectedDevice: DeviceInfo?,
    onSettingsClick: () -> Unit,
) {
    val isConnected = connectionState == ConnectionState.CONNECTED
    
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 連線狀態指示器
                ConnectionIndicator(state = connectionState)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isConnected) connectedDevice?.name ?: "OpenCode Remote"
                    else "未連線",
                    style = Typography.titleLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (isConnected) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                        contentDescription = "設定",
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
}

/** 連線狀態指示器 */
@Composable
fun ConnectionIndicator(state: ConnectionState) {
    val (color, label) = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary to "已連線"
        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary to "連線中"
        ConnectionState.WAITING_FOR_PEER -> MaterialTheme.colorScheme.secondary to "等待配對"
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline to "離線"
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error to "錯誤"
    }
    
    Box(
        modifier = Modifier.size(10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        // 脈動動畫 (簡化版)
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, delayMillis = 0, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (state in setOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING)) alpha else 1f }
                .clip(androidx.compose.ui.graphics.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            // 實心圓
        }
    }
    .background(color, androidx.compose.ui.graphics.CircleShape)
}

/** 未連線畫面 */
@Composable
fun DisconnectedView(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("未連線到 Relay Server", style = Typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "請先在設定中配置 Relay Server URL，\n然後點擊下方按鈕連線",
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onConnect) {
            Text("連線", style = Typography.labelLarge)
        }
    }
}

/** 載入畫面 */
@Composable
fun LoadingView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = Typography.bodyLarge)
    }
}

/** 等待桌面端配對 */
@Composable
fun WaitingForPeerView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.HourglassTop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("等待桌面端連線...", style = Typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "手機已就緒，請在電腦上啟動 OpenCode Remote Agent 服務\n並使用相同 Token 完成配對",
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.TextAlign.Center,
        )
    }
}

/** 已連線畫面 */
@Composable
fun ConnectedView(
    device: DeviceInfo?,
    sessions: List<SessionInfo>,
    onSessionClick: (SessionInfo) -> Unit,
    onProjectsClick: () -> Unit,
    selectedSessionId: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 裝置資訊卡片
        device?.let { d ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (d.type == "desktop") 
                            androidx.compose.material.icons.Icons.Default.Computer 
                        else 
                            androidx.compose.material.icons.Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(d.name, style = Typography.titleMedium)
                        Text(
                            "${d.type} • ${d.platform} • v${d.version}",
                            style = Typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 線上狀態
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(androidx.compose.ui.graphics.CircleShape)
                            .background(if (d.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
        
        // 快速操作列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onProjectsClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Folder,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("專案")
            }
            
            OutlinedButton(
                onClick = { /* 開啟檔案瀏覽 */ },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.InsertDriveFile,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("檔案")
            }
        }
        
        // 會話列表
        Text(
            "會話列表",
            style = Typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 0),
        )
        
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("尚無會話", style = Typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "點擊右下角 + 建立新會話",
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(bottom = 80.dp), // FAB 空間
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions) { session ->
                    SessionItem(
                        session = session,
                        isSelected = session.id == selectedSessionId,
                        onClick = { onSessionClick(session) },
                    )
                }
            }
        }
    }
}

/** 會話列表項目 */
@Composable
fun SessionItem(
    session: SessionInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val statusColor = when (session.status) {
        "running" -> MaterialTheme.colorScheme.primary
        "idle" -> MaterialTheme.colorScheme.tertiary
        "exited" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            BorderStroke(0.dp, MaterialTheme.colorScheme.transparent),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 狀態指示器
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.ui.graphics.CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            // 會話資訊
            Column(modifier = Modifier.weight(1f)) {
                Text(session.name, style = Typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.TextOverflow.Ellipsis)
                Text(
                    session.projectPath,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.TextOverflow.Ellipsis,
                )
            }
            
            // 時間
            Text(
                formatRelativeTime(session.lastActiveAt),
                style = Typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 錯誤畫面 */
@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("連線錯誤", style = Typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("重試")
        }
    }
}

/** 格式化相對時間 */
fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "剛剛"
        diff < 3_600_000 -> "${diff / 60_000} 分鐘前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小時前"
        else -> "${diff / 86_400_000} 天前"
    }
}