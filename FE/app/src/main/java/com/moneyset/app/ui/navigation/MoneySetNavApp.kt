package com.moneyset.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneyset.app.ui.bills.BillsScreen
import com.moneyset.app.ui.budget.BudgetScreen
import com.moneyset.app.ui.chat.ChatScreen
import com.moneyset.app.ui.chat.ChatViewModel
import com.moneyset.app.ui.home.HomeScreen
import com.moneyset.app.ui.profile.ChangePasswordScreen
import com.moneyset.app.ui.profile.ProfileScreen
import com.moneyset.app.ui.stats.StatsScreen
import com.moneyset.app.ui.theme.AppBackground
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.IndigoSoft
import com.moneyset.app.ui.theme.InkSoft

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val BUDGET = "budget"
    const val PROFILE = "profile"
    const val BILLS = "bills"
    const val CHANGE_PASSWORD = "change-password"
    const val CHAT = "chat?${ChatViewModel.ARG_PREFILL}={${ChatViewModel.ARG_PREFILL}}&${ChatViewModel.ARG_SCAN}={${ChatViewModel.ARG_SCAN}}"

    fun chat(prefill: String?, scan: Boolean) =
        "chat?${ChatViewModel.ARG_PREFILL}=${Uri.encode(prefill.orEmpty())}&${ChatViewModel.ARG_SCAN}=$scan"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Trang chủ", Icons.Rounded.Home),
    STATS(Routes.STATS, "Thống kê", Icons.Rounded.BarChart),
    BUDGET(Routes.BUDGET, "Ngân sách", Icons.Rounded.Savings),
    PROFILE(Routes.PROFILE, "Cá nhân", Icons.Rounded.Person),
}

/** Màn nào hiện bottom bar và thuộc tab nào (Hóa đơn nằm trong tab Ngân sách). */
private fun tabFor(route: String?): Tab? = when (route) {
    Routes.HOME -> Tab.HOME
    Routes.STATS -> Tab.STATS
    Routes.BUDGET, Routes.BILLS -> Tab.BUDGET
    Routes.PROFILE -> Tab.PROFILE
    else -> null
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MoneySetNavApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = tabFor(backStackEntry?.destination?.route)

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Indigo,
                                selectedTextColor = Indigo,
                                indicatorColor = IndigoSoft,
                                unselectedIconColor = InkSoft,
                                unselectedTextColor = InkSoft,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenChat = { prefill, scan -> navController.navigate(Routes.chat(prefill, scan)) },
                    onOpenBudget = { navController.navigateToTab(Routes.BUDGET) },
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.BUDGET) {
                BudgetScreen(
                    onBack = { navController.navigateToTab(Routes.HOME) },
                    onOpenBills = { navController.navigate(Routes.BILLS) },
                )
            }
            composable(Routes.BILLS) { BillsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PROFILE) {
                ProfileScreen(onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) })
            }
            composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument(ChatViewModel.ARG_PREFILL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(ChatViewModel.ARG_SCAN) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) {
                ChatScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
