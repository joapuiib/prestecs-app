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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    config: AppConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            environment = config.defaultEnvironment,
            canSwitchEnvironment = config.canSwitchEnvironment,
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = sessionRepository.state.first { it !is SessionState.Loading }
            if (stored is SessionState.LoggedOut) {
                _state.update {
                    it.copy(
                        environment = stored.environment,
                        username = it.username.ifEmpty { stored.lastUsername },
                    )
                }
            }
        }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onEnvironmentChange(environment: Environment) {
        _state.update { it.copy(environment = environment, error = null) }
        viewModelScope.launch { sessionRepository.selectEnvironment(environment) }
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isLoading) return
        if (current.needsCredentials && (current.username.isBlank() || current.password.isEmpty())) {
            _state.update { it.copy(error = LoginError.MissingFields) }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.logIn(current.environment, current.username, current.password)
            // On success the session changes and the root leaves this screen.
            _state.update {
                it.copy(
                    isLoading = false,
                    password = if (result == LoginResult.Success) it.password else "",
                    error = when (result) {
                        LoginResult.Success -> null
                        LoginResult.InvalidCredentials -> LoginError.InvalidCredentials
                        is LoginResult.MoodleError -> LoginError.Moodle(result.message)
                        is LoginResult.Failure -> LoginError.Api(result.error)
                    },
                )
            }
        }
    }
}
