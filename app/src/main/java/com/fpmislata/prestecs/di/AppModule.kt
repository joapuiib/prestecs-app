package com.fpmislata.prestecs.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.fpmislata.prestecs.BuildConfig
import com.fpmislata.prestecs.core.config.AppConfig
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.network.buildTypeInterceptors
import com.fpmislata.prestecs.core.session.KeystoreTokenCipher
import com.fpmislata.prestecs.core.session.SessionRepository
import com.fpmislata.prestecs.core.session.TokenCipher
import com.fpmislata.prestecs.data.api.ApiClients
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(
        defaultEnvironment = if (BuildConfig.DEBUG) Environment.STAGING else Environment.PROD,
        canSwitchEnvironment = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply { buildTypeInterceptors().forEach(::addInterceptor) }
        .build()

    @Provides
    @Singleton
    fun provideApiClients(okHttpClient: OkHttpClient, json: Json): ApiClients =
        ApiClients(okHttpClient, json)

    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = KeystoreTokenCipher()

    @Provides
    @Singleton
    fun provideSessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }

    @Provides
    @Singleton
    fun provideSessionRepository(
        dataStore: DataStore<Preferences>,
        cipher: TokenCipher,
        config: AppConfig,
    ): SessionRepository = SessionRepository(
        dataStore = dataStore,
        cipher = cipher,
        defaultEnvironment = config.defaultEnvironment,
        canSwitchEnvironment = config.canSwitchEnvironment,
    )
}
