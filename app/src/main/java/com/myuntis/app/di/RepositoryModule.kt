package com.myuntis.app.di

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.repository.AuthRepository
import com.myuntis.app.data.repository.ClassRegRepository
import com.myuntis.app.data.repository.ExamRepository
import com.myuntis.app.data.repository.GradesRepository
import com.myuntis.app.data.repository.HomeworkRepository
import com.myuntis.app.data.repository.TimetableRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAuthRepository(
        api: UntisApiService, ds: DataStoreManager
    ): AuthRepository = AuthRepository(api, ds)

    @Provides @Singleton
    fun provideTimetableRepository(
        api: UntisApiService, ds: DataStoreManager, auth: AuthRepository
    ): TimetableRepository = TimetableRepository(api, ds, auth)

    @Provides @Singleton
    fun provideHomeworkRepository(
        api: UntisApiService, ds: DataStoreManager
    ): HomeworkRepository = HomeworkRepository(api, ds)

    @Provides @Singleton
    fun provideGradesRepository(
        api: UntisApiService, ds: DataStoreManager
    ): GradesRepository = GradesRepository(api, ds)

    @Provides @Singleton
    fun provideClassRegRepository(
        api: UntisApiService, ds: DataStoreManager
    ): ClassRegRepository = ClassRegRepository(api, ds)

    @Provides @Singleton
    fun provideExamRepository(
        api: UntisApiService, ds: DataStoreManager
    ): ExamRepository = ExamRepository(api, ds)
}