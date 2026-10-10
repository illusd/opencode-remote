package com.opencode.remote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 應用程式主類別
 * 啟用 Hilt 依賴注入
 */
@HiltAndroidApp
class OpenCodeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化工作 (如需)
    }
}