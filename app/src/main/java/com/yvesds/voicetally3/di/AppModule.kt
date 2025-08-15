package com.yvesds.voicetally3.di

import android.content.Context
import com.yvesds.voicetally3.data.AliasRepository
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Dispatchers expliciet en injecteerbaar
    @Provides
    @Singleton
    @Named("Default")
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @Named("IO")
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideSharedPrefsHelper(
        @ApplicationContext context: Context
    ): SharedPrefsHelper = SharedPrefsHelper(context)

    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context,
        sharedPrefsHelper: SharedPrefsHelper,
        @Named("IO") io: CoroutineDispatcher
    ): StorageManager = StorageManager(context, sharedPrefsHelper, io)

    @Provides
    @Singleton
    fun provideCSVManager(
        @ApplicationContext context: Context,
        sharedPrefsHelper: SharedPrefsHelper,
        storageManager: StorageManager,
        @Named("IO") io: CoroutineDispatcher
    ): CSVManager = CSVManager(context, sharedPrefsHelper, storageManager, io)

    @Provides
    @Singleton
    fun provideAliasRepository(
        csvManager: CSVManager,
        storageManager: StorageManager
    ): AliasRepository = AliasRepository(csvManager, storageManager)

    @Provides
    @Singleton
    fun provideSpeciesCacheManager(
        aliasRepository: AliasRepository,
        sharedPrefsHelper: SharedPrefsHelper
    ): SpeciesCacheManager = SpeciesCacheManager(aliasRepository, sharedPrefsHelper)

    /** Dispatcher voor parsing-werk (CPU-bound). */
    @Provides
    @Singleton
    fun provideSpeechParsingUseCase(
        @Named("Default") dispatcher: CoroutineDispatcher
    ): SpeechParsingUseCase = SpeechParsingUseCase(dispatcher)
}
