package com.fpmislata.prestecs.core.session

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.testing.TestBackend
import com.fpmislata.prestecs.testing.assertStateBecomes
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

    @Test
    fun `starts logged out on the default environment`() = runBlocking {
        val repository = backend().sessionRepository

        repository.assertStateBecomes(SessionState.LoggedOut(Environment.STAGING, ""))
    }

    @Test
    fun `stores the token encrypted`() = runBlocking {
        val backend = backend()
        backend.sessionRepository.logIn(Environment.PROD, "nom.cognom", "t0k3n")

        backend.sessionRepository.assertStateBecomes(SessionState.LoggedIn(Session(Environment.PROD, "t0k3n")))
        val raw = backend.dataStore.data.first()[stringPreferencesKey("token")]
        assertNotEquals("t0k3n", raw)
    }

    @Test
    fun `log out keeps environment and username`() = runBlocking {
        val repository = backend().sessionRepository
        repository.logIn(Environment.PROD, "nom.cognom", "t0k3n")

        repository.logOut()

        repository.assertStateBecomes(SessionState.LoggedOut(Environment.PROD, "nom.cognom"))
    }

    @Test
    fun `switching environment signs out`() = runBlocking {
        val repository = backend().sessionRepository
        repository.logIn(Environment.STAGING, "nom.cognom", "t0k3n")

        repository.selectEnvironment(Environment.LOCAL)

        repository.assertStateBecomes(SessionState.LoggedOut(Environment.LOCAL, "nom.cognom"))
    }

    @Test
    fun `release builds can't switch environment`() = runBlocking {
        val repository = backend(canSwitchEnvironment = false).sessionRepository
        repository.logIn(Environment.STAGING, "nom.cognom", "t0k3n")

        repository.selectEnvironment(Environment.LOCAL)

        // Read DataStore directly: the state flow could still show the session
        // from before selectEnvironment.
        assertEquals(Session(Environment.STAGING, "t0k3n"), repository.currentSession())
        // Read DataStore directly: the state flow could still show the session
        // from before selectEnvironment.
        assertEquals(Session(Environment.STAGING, "t0k3n"), repository.currentSession())
        repository.assertStateBecomes(SessionState.LoggedIn(Session(Environment.STAGING, "t0k3n")))
    }

    @Test
    fun `a token that can't be decrypted means logged out`() = runBlocking {
        val backend = backend()
        backend.sessionRepository.logIn(Environment.STAGING, "nom.cognom", "t0k3n")
        backend.dataStore.edit { it[stringPreferencesKey("token")] = "garbage" }

        backend.sessionRepository.assertStateBecomes(SessionState.LoggedOut(Environment.STAGING, "nom.cognom"))
    }
}
