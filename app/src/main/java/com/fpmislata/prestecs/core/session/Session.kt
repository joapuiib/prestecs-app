package com.fpmislata.prestecs.core.session

import com.fpmislata.prestecs.core.config.Environment

/** A signed-in user: the Moodle token and the backend it is valid for. */
data class Session(val environment: Environment, val token: String)

sealed interface SessionState {
    /** Stored session not read yet (app start). */
    data object Loading : SessionState

    data class LoggedOut(val environment: Environment, val lastUsername: String) : SessionState

    data class LoggedIn(val session: Session) : SessionState
}
