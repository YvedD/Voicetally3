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
import javax.inject.Singleton

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

    /**
     * Dispatcher voor parsing-werk (CPU-bound).
     * We gebruiken Default zodat UI nooit geblokkeerd raakt.
     */
    @Provides
    @Singleton
    fun provideParsingDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * UseCase zonder vaste alias-map in de constructor.
     * De alias-map wordt per aanroep (execute) doorgegeven.
     */
    @Provides
    @Singleton
    fun provideSpeechParsingUseCase(
        dispatcher: CoroutineDispatcher
    ): SpeechParsingUseCase = SpeechParsingUseCase(dispatcher)
}
