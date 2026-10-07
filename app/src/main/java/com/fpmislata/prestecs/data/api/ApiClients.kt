package com.fpmislata.prestecs.data.api

import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.session.Session
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds Retrofit services for a given backend. The base URL depends on the
 * environment and the token on the session, so services are created per
 * session instead of being singletons.
 */
class ApiClients(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiUrl: (Environment) -> String = { it.apiUrl },
    private val moodleUrl: (Environment) -> String? = { it.moodleUrl },
) {
    private var cached: Pair<Session, PrestecsApi>? = null

    @Synchronized
    fun prestecs(session: Session): PrestecsApi {
        cached?.let { (cachedSession, api) -> if (cachedSession == session) return api }
        val client = okHttpClient.newBuilder()
            .addInterceptor(bearer(session.token))
            .build()
        return retrofit(apiUrl(session.environment), client)
            .create(PrestecsApi::class.java)
            .also { cached = session to it }
    }

    /** Null for environments without Moodle ([Environment.LOCAL]). */
    fun moodle(environment: Environment): MoodleAuthApi? =
        moodleUrl(environment)?.let { retrofit(it, okHttpClient).create(MoodleAuthApi::class.java) }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()

    private fun bearer(token: String) = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}
