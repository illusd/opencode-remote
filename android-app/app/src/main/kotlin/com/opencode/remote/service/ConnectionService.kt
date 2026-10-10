package com.opencode.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opencode.remote.R
import com.opencode.remote.ui.navigation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 前景服務 - 保持 WebSocket 連線在後台運行
 */
class ConnectionService : Service() {
    
    companion object {
        const val CHANNEL_ID = "opencode_connection"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START_CONNECTION"
        const val ACTION_STOP = "STOP_CONNECTION"
        const val ACTION_RECONNECT = "RECONNECT"
    }
    
    private var job: Job? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        
        when (action) {
            ACTION_START -> startConnection()
            ACTION_STOP -> stopConnection()
            ACTION_RECONNECT -> reconnect()
        }
        
        return START_STICKY
    }
    
    private fun startConnection() {
        if (job?.isActive == true) return
        
        _connectionState.value = ConnectionState.CONNECTING
        updateNotification("連線中...", "正在連接 Relay Server")
        
        job = CoroutineScope(Dispatchers.IO).launch {
            // 這裡實際連接 RelayConnectionManager
            // 為了演示，模擬連線過程
            try {
                // connectionManager.connect()
                // connectionManager.authenticate(savedToken)
                
                _connectionState.value = ConnectionState.CONNECTED
                updateNotification("已連線", "OpenCode Remote Agent 運行中")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                updateNotification("連線失敗", e.message ?: "未知錯誤")
            }
        }
    }
    
    private fun stopConnection() {
        job?.cancel()
        job = null
        _connectionState.value = ConnectionState.DISCONNECTED
        stopForeground(true)
        stopSelf()
    }
    
    private fun reconnect() {
        stopConnection()
        startConnection()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenCode 連線服務",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "維持與 OpenCode Desktop Agent 的連線"
                setShowBadge(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun updateNotification(title: String, text: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification) // 需要建立此圖示
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopConnection()
        super.onDestroy()
    }
}

/** 開機自啟接收器 */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val serviceIntent = Intent(context, ConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}