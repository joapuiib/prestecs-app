package com.fpmislata.prestecs.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fpmislata.prestecs.ui.loan.NewLoanScreen
import com.fpmislata.prestecs.ui.loans.LoansScreen
import com.fpmislata.prestecs.ui.returns.NewReturnScreen
import kotlinx.serialization.Serializable

@Serializable
object LoansRoute

@Serializable
object NewLoanRoute

@Serializable
object NewReturnRoute

@Composable
fun PrestecsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoansRoute) {
        composable<LoansRoute> {
            LoansScreen(
                onNewLoan = { navController.navigate(NewLoanRoute) },
                onNewReturn = { navController.navigate(NewReturnRoute) },
            )
        }
        composable<NewLoanRoute> {
            NewLoanScreen(onBack = { navController.popBackStack() })
        }
        composable<NewReturnRoute> {
            NewReturnScreen(onBack = { navController.popBackStack() })
        }
    }
}
