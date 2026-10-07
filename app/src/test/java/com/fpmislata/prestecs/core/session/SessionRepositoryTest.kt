package com.fpmislata.prestecs.core.session

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.testing.TestBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SessionRepositoryTest {
    private var backend: TestBackend? = null

    private fun backend(canSwitchEnvironment: Boolean = true) =
        TestBackend(canSwitchEnvironment = canSwitchEnvironment).also { backend = it }

    @After
    fun tearDown() {
        backend?.close()
    }

    private suspend fun SessionRepository.settledState() = state.first { it !is SessionState.Loading }

    @Test
    fun `starts logged out on the default environment`() = runBlocking {
        val repository = backend().sessionRepository

        assertEquals(SessionState.LoggedOut(Environment.STAGING, ""), repository.settledState())
    }

    @Test
    fun `stores the token encrypted`() = runBlocking {
        val backend = backend()
        backend.sessionRepository.logIn(Environment.PROD, "nom.cognom", "t0k3n")

        assertEquals(
            SessionState.LoggedIn(Session(Environment.PROD, "t0k3n")),
            backend.sessionRepository.settledState(),
        )
        val raw = backend.dataStore.data.first()[stringPreferencesKey("token")]
        assertNotEquals("t0k3n", raw)
    }

    @Test
    fun `log out keeps environment and username`() = runBlocking {
        val repository = backend().sessionRepository
        repository.logIn(Environment.PROD, "nom.cognom", "t0k3n")

        repository.logOut()

        assertEquals(SessionState.LoggedOut(Environment.PROD, "nom.cognom"), repository.settledState())
    }

    @Test
    fun `switching environment signs out`() = runBlocking {
        val repository = backend().sessionRepository
        repository.logIn(Environment.STAGING, "nom.cognom", "t0k3n")

        repository.selectEnvironment(Environment.LOCAL)

        assertEquals(SessionState.LoggedOut(Environment.LOCAL, "nom.cognom"), repository.settledState())
    }

    @Test
    fun `release builds can't switch environment`() = runBlocking {
        val repository = backend(canSwitchEnvironment = false).sessionRepository
        repository.logIn(Environment.STAGING, "nom.cognom", "t0k3n")

        repository.selectEnvironment(Environment.LOCAL)

        assertEquals(
            SessionState.LoggedIn(Session(Environment.STAGING, "t0k3n")),
            repository.settledState(),
        )
    }

    @Test
    fun `a token that can't be decrypted means logged out`() = runBlocking {
        val backend = backend()
        backend.sessionRepository.logIn(Environment.STAGING, "nom.cognom", "t0k3n")
        backend.dataStore.edit { it[stringPreferencesKey("token")] = "garbage" }

        assertEquals(
            SessionState.LoggedOut(Environment.STAGING, "nom.cognom"),
            backend.sessionRepository.settledState(),
        )
    }
}
