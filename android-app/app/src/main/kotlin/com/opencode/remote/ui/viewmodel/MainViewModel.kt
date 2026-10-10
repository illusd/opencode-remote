package com.opencode.remote.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.remote.data.model.*
import com.opencode.remote.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主畫面 ViewModel
 * 處理連線狀態、會話列表、裝置資訊
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    
    // ===== 狀態流 =====
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val connectedDevice: StateFlow<DeviceInfo?> = repository.connectedDevice
    val sessions: StateFlow<List<SessionInfo>> = repository.sessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    val projects: StateFlow<List<ProjectEntity>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    val pairedDevices: StateFlow<List<PairedDeviceEntity>> = repository.pairedDevicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    // ===== UI 狀態 =====
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState
    
    data class MainUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val showPairingDialog: Boolean = false,
        val selectedSessionId: String? = null,
    )
    
    // ===== 連線控制 =====
    fun connect() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val success = repository.connect()
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "連線失敗，請檢查 Relay Server 設定"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "連線錯誤"
                )
            }
        }
    }
    
    fun authenticate(token: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, showPairingDialog = false)
        viewModelScope.launch {
            try {
                val success = repository.authenticate(token)
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        error = "配對失敗，Token 無效或已過期",
                        showPairingDialog = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "認證錯誤",
                    showPairingDialog = true,
                )
            }
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _uiState.value = _uiState.value.copy(
                selectedSessionId = null,
                showPairingDialog = false,
            )
        }
    }
    
    fun showPairingDialog() {
        _uiState.value = _uiState.value.copy(showPairingDialog = true)
    }
    
    fun hidePairingDialog() {
        _uiState.value = _uiState.value.copy(showPairingDialog = false)
    }
    
    fun selectSession(sessionId: String) {
        _uiState.value = _uiState.value.copy(selectedSessionId = sessionId)
    }
    
    // ===== 會話操作 =====
    fun createSession(name: String, projectPath: String) {
        viewModelScope.launch {
            val session = repository.createSession(name, projectPath)
            _uiState.value = _uiState.value.copy(selectedSessionId = session.id)
        }
    }
    
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.selectedSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(selectedSessionId = null)
            }
        }
    }
    
    // ===== 專案操作 =====
    fun refreshProjects() {
        viewModelScope.launch {
            repository.requestProjects()
        }
    }
    
    // ===== 訊息處理 (供 UI 收集) =====
    val outputMessages = repository.incomingMessages
        .filter { it is RelayMessage.Output }
        .map { it as RelayMessage.Output }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    val pairedEvents = repository.incomingMessages
        .filter { it is RelayMessage.Paired }
        .map { it as RelayMessage.Paired }
        .onEach { event ->
            // 配對成功，更新裝置資訊
        }
        .launchIn(viewModelScope)
}