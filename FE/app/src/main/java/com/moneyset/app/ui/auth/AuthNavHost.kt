package com.moneyset.app.ui.auth

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object AuthRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot?email={email}"
    const val RESET = "reset?email={email}&devCode={devCode}&wait={wait}"

    fun forgot(email: String) = "forgot?email=${Uri.encode(email)}"
    fun reset(email: String, devCode: String?, wait: Int) =
        "reset?email=${Uri.encode(email)}&devCode=${Uri.encode(devCode.orEmpty())}&wait=$wait"
}

/** Luồng tài khoản khi chưa đăng nhập: Đăng nhập ↔ Đăng ký, Quên mật khẩu → Đặt lại mật khẩu. */
@Composable
fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = AuthRoutes.LOGIN) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onRegister = { nav.navigate(AuthRoutes.REGISTER) },
                onForgotPassword = { email -> nav.navigate(AuthRoutes.forgot(email)) },
            )
        }
        composable(AuthRoutes.REGISTER) {
            RegisterScreen(onBack = { nav.popBackStack() })
        }
        composable(
            AuthRoutes.FORGOT,
            arguments = listOf(navArgument(ForgotPasswordViewModel.ARG_EMAIL) { type = NavType.StringType; defaultValue = "" }),
        ) {
            ForgotPasswordScreen(
                onBack = { nav.popBackStack() },
                onCodeSent = { ticket -> nav.navigate(AuthRoutes.reset(ticket.email, ticket.devCode, ticket.resendAfterSeconds)) },
            )
        }
        composable(
            AuthRoutes.RESET,
            arguments = listOf(
                navArgument(ResetPasswordViewModel.ARG_EMAIL) { type = NavType.StringType; defaultValue = "" },
                navArgument(ResetPasswordViewModel.ARG_DEV_CODE) { type = NavType.StringType; defaultValue = "" },
                navArgument(ResetPasswordViewModel.ARG_WAIT) { type = NavType.IntType; defaultValue = 60 },
            ),
        ) {
            ResetPasswordScreen(
                onBack = { nav.popBackStack() },
                onDone = { email ->
                    // Báo kết quả cho màn Đăng nhập rồi quay về đó (bỏ qua màn Quên mật khẩu)
                    nav.getBackStackEntry(AuthRoutes.LOGIN).savedStateHandle[LoginViewModel.KEY_RESET_EMAIL] = email
                    nav.popBackStack(AuthRoutes.LOGIN, inclusive = false)
                },
            )
        }
    }
}
