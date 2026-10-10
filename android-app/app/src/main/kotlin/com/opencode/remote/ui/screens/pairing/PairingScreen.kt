package com.opencode.remote.ui.screens.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.opencode.remote.ui.theme.OpenCodeTheme
import com.opencode.remote.ui.theme.TerminalColors
import com.opencode.remote.ui.theme.Typography

/**
 * 配對對話框
 * 輸入 Token 進行裝置配對
 */
@Composable
fun PairingDialog(
    onPair: (String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    var token by remember { mutableStateOf("") }
    val showToken = remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 標題
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "配對桌面端",
                    style = Typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "在桌面端啟動服務後，將顯示的 Token 輸入此處完成配對",
                    style = Typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                // Token 輸入框
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("配對 Token") },
                    placeholder = { Text("例: ABC123DEF456") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    visualTransformation = if (showToken.value) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken.value = !showToken.value }) {
                            Icon(
                                imageVector = if (showToken.value) 
                                    androidx.compose.material.icons.Icons.Default.VisibilityOff 
                                else 
                                    androidx.compose.material.icons.Icons.Default.Visibility,
                                contentDescription = if (showToken.value) "隱藏" else "顯示",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    singleLine = true,
                    isEnabled = !isLoading,
                )
                
                // 錯誤訊息
                error?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        err,
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = { onPair(token) },
                        modifier = Modifier.weight(1f),
                        enabled = token.isNotBlank() && !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text("配對")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 配對畫面 (全螢幕版)
 */
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("裝置配對") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 圖示與說明
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "連接桌面端 OpenCode",
                    style = Typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "1. 確保電腦已安裝並啟動 OpenCode Remote Agent 服務\n" +
                    "2. 服務啟動後會在日誌顯示配對 Token\n" +
                    "3. 將 Token 輸入下方完成配對",
                    style = Typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.TextAlign.Center,
                    lineHeight = 24.sp,
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                // Token 輸入
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("輸入配對 Token", style = Typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = uiState.token,
                            onValueChange = { viewModel.onTokenChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Token") },
                            placeholder = { Text("從桌面端日誌複製 Token") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            visualTransformation = if (uiState.showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.toggleTokenVisibility() }) {
                                    Icon(
                                        imageVector = if (uiState.showToken) 
                                            androidx.compose.material.icons.Icons.Default.VisibilityOff 
                                        else 
                                            androidx.compose.material.icons.Icons.Default.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            singleLine = true,
                            isEnabled = !uiState.isLoading,
                        )
                        
                        uiState.error?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(err, style = Typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 配對按鈕
                Button(
                    onClick = { viewModel.pair() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.token.isNotBlank() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (uiState.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp).padding(end = 12.dp)
                            )
                            Text("配對中...")
                        }
                    } else {
                        Text("開始配對", style = Typography.labelLarge)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 快速操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.generateToken() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("產生 Token")
                    }
                    
                    OutlinedButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("設定")
                    }
                }
            }
        }
    }
}

/**
 * 配對 ViewModel (簡化版，實際應用 Hilt)
 */
class PairingViewModel : androidx.lifecycle.ViewModel() {
    var token by mutableStateOf("")
        private set
    var showToken by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    
    val uiState: StateFlow<PairingUiState>
        get() = MutableStateFlow(PairingUiState(token, showToken, isLoading, error)).asStateFlow()
    
    data class PairingUiState(
        val token: String,
        val showToken: Boolean,
        val isLoading: Boolean,
        val error: String?,
    )
    
    fun onTokenChange(newToken: String) {
        token = newToken.uppercase()
        error = null
    }
    
    fun toggleTokenVisibility() {
        showToken = !showToken
    }
    
    fun pair() {
        // 實際由 MainViewModel 處理
    }
    
    fun generateToken() {
        // 產生新 Token
    }
    
    fun openSettings() {
        // 開啟設定
    }
}