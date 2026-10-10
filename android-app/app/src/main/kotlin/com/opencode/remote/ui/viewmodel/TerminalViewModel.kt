package com.opencode.remote.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.remote.data.model.*
import com.opencode.remote.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 終端機 ViewModel
 * 處理指令輸入、輸出顯示、ANSI 解析、歷史記錄
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    
    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId
    
    // 輸出緩衝區 (最多保留 10000 行)
    private val outputBuffer = Channel<String>(10000)
    val outputFlow = outputBuffer.receiveAsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    
    // 當前行輸入
    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput
    
    // 指令歷史 (用於上下鍵)
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1
    
    // 狀態
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    /** 設定當前會話 */
    fun setSession(sessionId: String) {
        _sessionId.value = sessionId
        clearOutput()
        loadHistory()
    }
    
    /** 清除輸出 */
    fun clearOutput() {
        outputBuffer.trySend("")
    }
    
    /** 發送指令 */
    fun sendCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        
        _isProcessing.value = true
        _error.value = null
        
        // 加入歷史
        if (commandHistory.isEmpty() || commandHistory.last() != trimmed) {
            commandHistory.add(trimmed)
            if (commandHistory.size > 1000) commandHistory.removeAt(0)
        }
        historyIndex = commandHistory.size
        
        // 顯示輸入的指令
        appendOutput("\$ $trimmed\n")
        
        viewModelScope.launch {
            try {
                val success = repository.sendCommand(trimmed)
                if (!success) {
                    appendOutput("\n[錯誤] 發送失敗，請檢查連線\n")
                    _error.value = "發送失敗"
                }
            } catch (e: Exception) {
                appendOutput("\n[錯誤] ${e.message}\n")
                _error.value = e.message
            } finally {
                _isProcessing.value = false
            }
        }
        
        _currentInput.value = ""
    }
    
    /** 處理來自伺服器的輸出 */
    fun handleOutput(message: RelayMessage.Output) {
        appendOutput(message.data)
    }
    
    /** 附加輸出到緩衝區 */
    private fun appendOutput(text: String) {
        outputBuffer.trySend(text)
    }
    
    /** 載入指令歷史 */
    private fun loadHistory() {
        _sessionId.value?.let { sessionId ->
            viewModelScope.launch {
                val history = repository.getCommandHistory(sessionId, limit = 100).first()
                commandHistory.clear()
                commandHistory.addAll(history.reversed().map { it.command })
                historyIndex = commandHistory.size
            }
        }
    }
    
    /** 歷史導航：上一條 */
    fun historyPrevious(): String? {
        if (commandHistory.isEmpty()) return null
        if (historyIndex > 0) {
            historyIndex--
            return commandHistory[historyIndex]
        }
        return if (historyIndex == 0) commandHistory[0] else null
    }
    
    /** 歷史導航：下一條 */
    fun historyNext(currentInput: String): String? {
        if (commandHistory.isEmpty()) return null
        if (historyIndex < commandHistory.size - 1) {
            historyIndex++
            return commandHistory[historyIndex]
        } else {
            historyIndex = commandHistory.size
            return currentInput // 回到當前輸入
        }
    }
    
    /** 更新當前輸入 */
    fun updateInput(input: String) {
        _currentInput.value = input
    }
    
    /** 清除錯誤 */
    fun clearError() {
        _error.value = null
    }
    
    override fun onCleared() {
        outputBuffer.close()
        super.onCleared()
    }
}

// 在檔案頂部添加 import
// import kotlinx.coroutines.channels.Channel
// import kotlinx.serialization.json.Json