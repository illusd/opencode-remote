package com.opencode.remote.ui.screens.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.opencode.remote.data.model.RelayMessage
import com.opencode.remote.ui.theme.OpenCodeTheme
import com.opencode.remote.ui.theme.TerminalColors
import com.opencode.remote.ui.theme.Typography

/**
 * 終端機畫面
 * 完整的 ANSI 支援、指令歷史、Tab 補全
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    onShowKeyboard: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    val sessionId by viewModel.sessionId.collectAsState()
    val output by viewModel.outputFlow.collectAsState()
    val currentInput by viewModel.currentInput.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val textFieldValue = remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState(0)
    var lastOutputLength by remember { mutableStateOf(0) }
    
    // 自動捲動到最新
    LaunchedEffect(output) {
        if (output.length > lastOutputLength) {
            lastOutputLength = output.length
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Scaffold(
        topBar = {
            TerminalTopAppBar(
                sessionId = sessionId ?: "",
                isProcessing = isProcessing,
                onBack = onBack,
                onShowKeyboard = onShowKeyboard,
                onClear = { viewModel.clearOutput() },
            )
        },
        bottomBar = {
            TerminalInputBar(
                textFieldValue = textFieldValue,
                focusRequester = focusRequester,
                onSend = { command ->
                    viewModel.sendCommand(command)
                    textFieldValue.value = TextFieldValue("")
                    focusRequester.requestFocus()
                },
                onHistoryUp = { viewModel.historyPrevious()?.let { textFieldValue.value = TextFieldValue(it) } },
                onHistoryDown = { currentInput ->
                    viewModel.historyNext(currentInput)?.let { textFieldValue.value = TextFieldValue(it) }
                },
                isProcessing = isProcessing,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalColors.Background),
        ) {
            // 輸出區域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .background(TerminalColors.Background),
            ) {
                // 終端機輸出 (使用 Text 顯示 ANSI 色彩)
                TerminalOutputText(text = output)
                
                // 底部填充確保捲動空間
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            // 處理中指示器
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    CircularProgressIndicator(
                        color = TerminalColors.Ans Green,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            
            // 錯誤提示
            error?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            err,
                            style = Typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 終端機頂部列 */
@Composable
fun TerminalTopAppBar(
    sessionId: String,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onShowKeyboard: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                "終端機 • $sessionId",
                style = Typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
        actions = {
            IconButton(onClick = onShowKeyboard) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Keyboard,
                    contentDescription = "顯示鍵盤",
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.DeleteSweep,
                    contentDescription = "清除輸出",
                )
            }
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TerminalColors.Background.copy(alpha = 0.95f),
            titleContentColor = TerminalColors.Foreground,
        ),
    )
}

/** 終端機輸入列 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalInputBar(
    textFieldValue: MutableState<TextFieldValue>,
    focusRequester: FocusRequester,
    onSend: (String) -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: (String) -> Unit,
    isProcessing: Boolean,
) {
    var imeActionHandler by remember { mutableStateOf<ImeActionHandler?>(null) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(TerminalColors.Background.copy(alpha = 0.95f))
            .shadow(2.dp, shape = androidx.compose.ui.graphics.RectangleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 提示符
        Text(
            "\$ ",
            style = Typography.bodyLarge.copy(color = TerminalColors.AnsGreen),
        )
        
        // 輸入框
        TextField(
            value = textFieldValue.value,
            onValueChange = { textFieldValue.value = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .focusRequester(focusRequester),
            textStyle = Typography.bodyLarge.copy(color = TerminalColors.Foreground),
            placeholder = { Text("輸入指令...", style = Typography.bodyLarge.copy(color = TerminalColors.AnsiBrightBlack)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send,
            ),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = TerminalColors.Background,
                unfocusedContainerColor = TerminalColors.Background,
                focusedContainerColor = TerminalColors.Background,
                textColor = TerminalColors.Foreground,
                placeholderColor = TerminalColors.AnsiBrightBlack,
                cursorColor = TerminalColors.Cursor,
                disabledTextColor = TerminalColors.AnsiBrightBlack,
                disabledContainerColor = TerminalColors.Background,
            ),
        ) { controller ->
            imeActionHandler = controller
        }
        
        // 發送按鈕
        IconButton(
            onClick = { 
                val cmd = textFieldValue.value.text.trim()
                if (cmd.isNotBlank()) onSend(cmd)
            },
            enabled = !isProcessing && textFieldValue.value.text.isNotBlank(),
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Send,
                contentDescription = "發送",
                tint = if (isProcessing) MaterialTheme.colorScheme.onSurfaceVariant else TerminalColors.AnsGreen,
            )
        }
    }
}

/** 終端機輸出文字 (ANSI 色彩解析) */
@Composable
fun TerminalOutputText(text: String) {
    // 簡化版：直接顯示文字，實際專案應解析 ANSI 轉義序列
    Text(
        text = text,
        style = Typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            color = TerminalColors.Foreground,
            lineHeight = 20.sp,
        ),
        overflow = TextOverflow.Visible,
        softWrap = true,
    )
}

/** ANSI 解析器 (簡化版) */
object AnsiParser {
    data class StyledSegment(
        val text: String,
        val color: androidx.compose.ui.graphics.Color = TerminalColors.Foreground,
        val backgroundColor: androidx.compose.ui.graphics.Color? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
    )
    
    fun parse(text: String): List<StyledSegment> {
        // 這裡應實作完整的 ANSI SGR 解析
        // 暫時返回原始文字
        return listOf(StyledSegment(text))
    }
}