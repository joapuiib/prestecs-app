package com.fpmislata.prestecs.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fpmislata.prestecs.core.session.SessionRepository
import com.fpmislata.prestecs.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionRepository.state

    fun logOut() {
        viewModelScope.launch { sessionRepository.logOut() }
    }
}
