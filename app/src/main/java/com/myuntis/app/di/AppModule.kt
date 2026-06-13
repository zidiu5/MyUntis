package com.myuntis.app.di

import android.content.Context
import com.myuntis.app.data.local.DataStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// =============================================================
// APP MODULE - Hilt DI
// =============================================================
// Provides app-level dependencies that don't fit in NetworkModule.
// =============================================================
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStoreManager(
        @ApplicationContext context: Context
    ): DataStoreManager {
        return DataStoreManager(context)
    }
}