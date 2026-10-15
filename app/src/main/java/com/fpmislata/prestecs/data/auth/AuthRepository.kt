package com.fpmislata.prestecs.data.auth

import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.core.network.apiCall
import com.fpmislata.prestecs.core.session.Session
import com.fpmislata.prestecs.core.session.SessionRepository
import com.fpmislata.prestecs.data.api.ApiClients
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

sealed interface LoginResult {
    data object Success : LoginResult

    data object InvalidCredentials : LoginResult

    /** Moodle refused the login for another reason; [message] comes from Moodle. */
    data class MoodleError(val message: String?) : LoginResult

    data class Failure(val error: ApiError) : LoginResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiClients: ApiClients,
    private val sessionRepository: SessionRepository,
    private val json: Json,
) {
    suspend fun logIn(environment: Environment, username: String, password: String): LoginResult {
        val user = username.trim()
        val token = when (val moodle = apiClients.moodle(environment)) {
            null -> Environment.LOCAL_TOKEN

            else -> when (val result = apiCall(json) { moodle.token(user, password) }) {
                is ApiResult.Failure -> return LoginResult.Failure(result.error)

                is ApiResult.Success -> result.value.token ?: return when (result.value.errorcode) {
                    "invalidlogin" -> LoginResult.InvalidCredentials
                    else -> LoginResult.MoodleError(result.value.error)
                }
            }
        }

        // Make sure the prestecs API accepts the token before keeping it. An
        // empty lookup is the cheapest authenticated call: it answers
        // {"found": false}.
        val api = apiClients.prestecs(Session(environment, token))
        val check = apiCall(json) { api.lookup("") }
        if (check is ApiResult.Failure) return LoginResult.Failure(check.error)

        sessionRepository.logIn(environment, user, token)
        return LoginResult.Success
    }

    suspend fun logOut() = sessionRepository.logOut()
}
