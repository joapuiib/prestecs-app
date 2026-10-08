package com.fpmislata.prestecs.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.ui.history.HistoryScreen
import com.fpmislata.prestecs.ui.loan.NewLoanScreen
import com.fpmislata.prestecs.ui.loans.LoansScreen
import com.fpmislata.prestecs.ui.returns.NewReturnScreen
import kotlinx.serialization.Serializable

@Serializable
object LoansRoute

@Serializable
object HistoryRoute

@Serializable
object NewLoanRoute

@Serializable
object NewReturnRoute

private const val SAVED_MESSAGE_KEY = "savedMessage"

/** Back to the loans list, which shows [message] in a snackbar. */
private fun NavController.backWithMessage(message: String) {
    previousBackStackEntry?.savedStateHandle?.set(SAVED_MESSAGE_KEY, message)
    popBackStack()
}

@Composable
fun PrestecsNavHost(environment: Environment, onLogOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoansRoute) {
        composable<LoansRoute> { entry ->
            // Set by a new loan or return that saved everything.
            val savedMessage by entry.savedStateHandle.getStateFlow<String?>(SAVED_MESSAGE_KEY, null)
                .collectAsStateWithLifecycle()
            LoansScreen(
                environment = environment,
                snackbarMessage = savedMessage,
                onSnackbarShown = { entry.savedStateHandle[SAVED_MESSAGE_KEY] = null },
                onLogOut = onLogOut,
                onNewLoan = { navController.navigate(NewLoanRoute) },
                onNewReturn = { navController.navigate(NewReturnRoute) },
                onHistory = { navController.navigate(HistoryRoute) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable<NewLoanRoute> {
            NewLoanScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.backWithMessage(it) },
            )
        }
        composable<NewReturnRoute> {
            NewReturnScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.backWithMessage(it) },
            )
        }
    }
}
