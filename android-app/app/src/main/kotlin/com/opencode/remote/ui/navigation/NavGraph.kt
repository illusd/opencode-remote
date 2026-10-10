package com.opencode.remote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.rememberNavController
import com.opencode.remote.ui.screens.main.MainScreen
import com.opencode.remote.ui.screens.pairing.PairingScreen
import com.opencode.remote.ui.screens.pairing.PairingViewModel
import com.opencode.remote.ui.screens.terminal.TerminalScreen
import com.opencode.remote.ui.screens.terminal.TerminalViewModel
import com.opencode.remote.ui.viewmodel.MainViewModel
import dagger.hilt.android.EntryPoint
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * 導航圖
 */
@Composable
fun AppNavHost(
    startDestination: String = "main",
) {
    val navController = rememberNavController()
    val mainViewModel = hiltViewModel<MainViewModel>()
    val terminalViewModel = hiltViewModel<TerminalViewModel>()
    val pairingViewModel = hiltViewModel<PairingViewModel>()
    
    NavHost(navController, startDestination) {
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                onSessionClick = { session ->
                    terminalViewModel.setSession(session.id)
                    navController.navigate("terminal/${session.id}")
                },
                onNewSession = { /* 顯示新建會話對話框 */ },
                onProjectsClick = { navController.navigate("projects") },
                onSettingsClick = { navController.navigate("settings") },
            )
        }
        
        composable(
            route = "terminal/${navArgument("sessionId") { type = androidx.navigation.NavType.StringType } }",
            arguments = listOf(navArgument("sessionId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.getString() ?: ""
            TerminalScreen(
                viewModel = terminalViewModel,
                onBack = { navController.popBackStack() },
                onShowKeyboard = { /* 顯示軟鍵盤 */ },
                onHideKeyboard = { /* 隱藏軟鍵盤 */ },
            )
        }
        
        composable("pairing") {
            PairingScreen(viewModel = pairingViewModel)
        }
        
        composable("projects") {
            // ProjectsScreen 待實作
            Text("專案列表畫面 - 待實作")
        }
        
        composable("settings") {
            // SettingsScreen 待實作
            Text("設定畫面 - 待實作")
        }
    }
}

/** 主活動入口點 */
@AndroidEntryPoint
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCodeTheme {
                AppNavHost()
            }
        }
    }
}

/** 啟動畫面 */
@AndroidEntryPoint
class SplashActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // 啟動畫面邏輯
        setContent {
            OpenCodeTheme {
                Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        androidx.compose.material.icons.Icons.Default.Terminal
                        androidx.compose.material3.Text("OpenCode Remote", style = com.opencode.remote.ui.theme.Typography.headlineMedium)
                    }
                }
            }
        }
    }
}