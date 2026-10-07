package com.fpmislata.prestecs.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpmislata.prestecs.core.session.SessionState
import com.fpmislata.prestecs.ui.login.LoginScreen
import com.fpmislata.prestecs.ui.navigation.PrestecsNavHost

/**
 * Shows the login screen or the app depending on the session. Logging out
 * (or a 401 from the API) clears the session and lands back on login with a
 * fresh back stack.
 */
@Composable
fun PrestecsRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            SessionState.Loading -> Unit
            is SessionState.LoggedOut -> LoginScreen()
            is SessionState.LoggedIn -> PrestecsNavHost(
                environment = s.session.environment,
                onLogOut = viewModel::logOut,
            )
        }
    }
}
