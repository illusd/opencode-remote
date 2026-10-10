package com.opencode.remote.di

import android.content.Context
import com.opencode.remote.data.local.*
import com.opencode.remote.data.remote.RelayConnectionManager
import com.opencode.remote.data.repository.AppRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 網路與資料層 DI 模組
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(createSettingsDataStore(context))
    }
    
    @Provides
    @Singleton
    fun provideConnectionManager(settingsStore: SettingsDataStore): RelayConnectionManager {
        // 這裡需要設定的初始值，實際應從 settingsStore 取得
        // 為了簡化，使用預設值
        return RelayConnectionManager(AppSettings())
    }
    
    @Provides
    @Singleton
    fun provideAppRepository(
        database: AppDatabase,
        connectionManager: RelayConnectionManager,
        settingsStore: SettingsDataStore,
    ): AppRepository {
        return AppRepository(database, connectionManager, settingsStore)
    }
}