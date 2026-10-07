package com.fpmislata.prestecs.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fpmislata.prestecs.core.config.Environment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the current session. The token is stored encrypted; the password is
 * never stored. Only the username is kept in clear, to prefill the login form.
 */
class SessionRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val defaultEnvironment: Environment,
    private val canSwitchEnvironment: Boolean,
) {
    /**
     * Stored session, re-emitted on every change. Cold: each collector reads
     * DataStore, so the first value is always current. Never emits
     * [SessionState.Loading]; that is for UI holders before the first value.
     */
    val state: Flow<SessionState> = dataStore.data
        .map { it.toSessionState() }
        .distinctUntilChanged()

    suspend fun currentSession(): Session? = (state.first() as? SessionState.LoggedIn)?.session

    suspend fun logIn(environment: Environment, username: String, token: String) {
        dataStore.edit {
            it[ENVIRONMENT] = environment.name
            it[USERNAME] = username
            it[TOKEN] = cipher.encrypt(token)
        }
    }

    suspend fun logOut() {
        dataStore.edit { it.remove(TOKEN) }
    }

    /** Switching backend always signs out: a token belongs to one backend. */
    suspend fun selectEnvironment(environment: Environment) {
        if (!canSwitchEnvironment) return
        dataStore.edit {
            it[ENVIRONMENT] = environment.name
            it.remove(TOKEN)
        }
    }

    private fun Preferences.toSessionState(): SessionState {
        val environment = environment()
        val token = this[TOKEN]?.let(cipher::decrypt)
        return if (token != null) {
            SessionState.LoggedIn(Session(environment, token))
        } else {
            SessionState.LoggedOut(environment, this[USERNAME].orEmpty())
        }
    }

    private fun Preferences.environment(): Environment {
        if (!canSwitchEnvironment) return defaultEnvironment
        return Environment.entries.firstOrNull { it.name == this[ENVIRONMENT] } ?: defaultEnvironment
    }

    private companion object {
        val ENVIRONMENT = stringPreferencesKey("environment")
        val USERNAME = stringPreferencesKey("username")
        val TOKEN = stringPreferencesKey("token")
    }
}
