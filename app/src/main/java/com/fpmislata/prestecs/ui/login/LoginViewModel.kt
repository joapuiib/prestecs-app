package com.fpmislata.prestecs.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fpmislata.prestecs.core.config.AppConfig
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.session.SessionRepository
import com.fpmislata.prestecs.core.session.SessionState
import com.fpmislata.prestecs.data.auth.AuthRepository
import com.fpmislata.prestecs.data.auth.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginError {
    data object MissingFields : LoginError

    data object InvalidCredentials : LoginError

    data class Moodle(val message: String?) : LoginError

    data class Api(val error: ApiError) : LoginError
}

data class LoginUiState(
    val environment: Environment,
    val canSwitchEnvironment: Boolean,
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: LoginError? = null,
) {
    /** The local backend has no Moodle: no username or password needed. */
    val needsCredentials: Boolean get() = environment.moodleUrl != null
}

/** What the user typed. A null [username] means "not edited": show the last one used. */
private data class LoginForm(
    val username: String? = null,
    val password: String = "",
    val isLoading: Boolean = false,
    val error: LoginError? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val config: AppConfig,
) : ViewModel() {

    private val form = MutableStateFlow(LoginForm())

    // Environment and last username come from the stored session; the rest
    // from the form.
    val state: StateFlow<LoginUiState> = combine(
        sessionRepository.state.filterIsInstance<SessionState.LoggedOut>(),
        form,
    ) { stored, form -> uiState(stored.environment, stored.lastUsername, form) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            uiState(config.defaultEnvironment, lastUsername = "", LoginForm()),
        )

    fun onUsernameChange(value: String) = form.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = form.update { it.copy(password = value, error = null) }

    fun onEnvironmentChange(environment: Environment) {
        form.update { it.copy(error = null) }
        viewModelScope.launch { sessionRepository.selectEnvironment(environment) }
    }

    fun onSubmit() {
        val current = state.value
        if (current.isLoading) return
        if (current.needsCredentials && (current.username.isBlank() || current.password.isEmpty())) {
            form.update { it.copy(error = LoginError.MissingFields) }
            return
        }

        form.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.logIn(current.environment, current.username, current.password)
            // On success the session changes and the root leaves this screen.
            val error = when (result) {
                LoginResult.Success -> null
                LoginResult.InvalidCredentials -> LoginError.InvalidCredentials
                is LoginResult.MoodleError -> LoginError.Moodle(result.message)
                is LoginResult.Failure -> LoginError.Api(result.error)
            }
            form.update {
                it.copy(
                    isLoading = false,
                    password = if (error == null) it.password else "",
                    error = error,
                )
            }
        }
    }

    private fun uiState(environment: Environment, lastUsername: String, form: LoginForm) = LoginUiState(
        environment = environment,
        canSwitchEnvironment = config.canSwitchEnvironment,
        username = form.username ?: lastUsername,
        password = form.password,
        isLoading = form.isLoading,
        error = form.error,
    )
}
