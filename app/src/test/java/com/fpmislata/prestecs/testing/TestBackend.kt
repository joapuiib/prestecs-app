package com.fpmislata.prestecs.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.session.SessionRepository
import com.fpmislata.prestecs.core.session.TokenCipher
import com.fpmislata.prestecs.data.api.ApiClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files

/** Reversible stand-in for the Keystore cipher (not available on the JVM). */
class FakeTokenCipher : TokenCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()

    override fun decrypt(encoded: String) =
        if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
}

/**
 * Real Retrofit/OkHttp stack and DataStore, with every environment pointed at
 * a [MockWebServer]: the API under `/api/`, Moodle under `/moodle/`.
 */
class TestBackend(
    canSwitchEnvironment: Boolean = true,
    defaultEnvironment: Environment = Environment.STAGING,
) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("prestecs-test").toFile()

    val apiClients = ApiClients(
        okHttpClient = OkHttpClient(),
        json = json,
        apiUrl = { server.url("/api/").toString() },
        moodleUrl = { env -> env.moodleUrl?.let { server.url("/moodle/").toString() } },
    )

    val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "session.preferences_pb") }

    val sessionRepository = SessionRepository(
        dataStore = dataStore,
        cipher = FakeTokenCipher(),
        defaultEnvironment = defaultEnvironment,
        canSwitchEnvironment = canSwitchEnvironment,
        scope = scope,
    )

    fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse(code, Headers.headersOf("Content-Type", "application/json"), body))
    }

    fun takeRequest() = checkNotNull(server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)) {
        "No request was sent"
    }

    override fun close() {
        scope.cancel()
        server.close()
        dir.deleteRecursively()
    }
}
