package com.yvesds.voicetally3.di

import android.content.Context
import com.yvesds.voicetally3.data.CSVManager
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.data.SpeciesCacheManager
import com.yvesds.voicetally3.managers.StorageManager
import com.yvesds.voicetally3.utils.SpeechParsingUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.yvesds.voicetally3.data.AliasRepository


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPrefsHelper(
        @ApplicationContext context: Context
    ): SharedPrefsHelper = SharedPrefsHelper(context)

    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context,
        sharedPrefsHelper: SharedPrefsHelper
    ): StorageManager = StorageManager(context, sharedPrefsHelper)

    @Provides
    @Singleton
    fun provideCSVManager(
        @ApplicationContext context: Context,
        sharedPrefsHelper: SharedPrefsHelper,
        storageManager: StorageManager
    ): CSVManager = CSVManager(context, sharedPrefsHelper, storageManager)

    @Provides
    @Singleton
    fun provideSpeciesCacheManager(
        aliasRepository: AliasRepository,
        sharedPrefsHelper: SharedPrefsHelper
    ): SpeciesCacheManager = SpeciesCacheManager(aliasRepository, sharedPrefsHelper)

    @Provides
    @Singleton
    fun provideSpeechParsingUseCase() = SpeechParsingUseCase()


    @Provides
    @Singleton
    fun provideAliasRepository(
        csvManager: CSVManager,
        storageManager: StorageManager
    ): AliasRepository = AliasRepository(csvManager, storageManager)

}
