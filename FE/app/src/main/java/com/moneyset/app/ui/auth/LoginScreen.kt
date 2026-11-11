package com.moneyset.app.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyset.app.BuildConfig
import com.moneyset.app.data.auth.FakeAuthDataSource
import com.moneyset.app.ui.components.GradientButton

@Composable
fun LoginScreen(
    onRegister: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.login()
    }

    AuthScaffold(
        title = "Đăng nhập",
        subtitle = "Chào mừng bạn quay lại! 👋",
        mascotSize = 130.dp,
        footer = {
            AuthFooterLink("Chưa có tài khoản?", "Đăng ký ngay", onRegister)
            if (BuildConfig.USE_MOCK_API) {
                AuthBanner(
                    "Chế độ demo: dùng ${FakeAuthDataSource.DEMO_EMAIL} / ${FakeAuthDataSource.DEMO_PASSWORD} hoặc tự đăng ký tài khoản mới.",
                    BannerKind.INFO,
                )
            }
        },
    ) {
        state.info?.let { AuthBanner(it, BannerKind.SUCCESS) }
        AuthTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            icon = Icons.Rounded.Email,
            error = state.emailError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Mật khẩu",
            error = state.passwordError,
            enabled = !state.isLoading,
            imeAction = ImeAction.Done,
            onDone = submit,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextLink("Quên mật khẩu?", onClick = { onForgotPassword(state.email) }, enabled = !state.isLoading)
        }
        state.error?.let { AuthBanner(it, BannerKind.ERROR) }
        GradientButton(
            text = "Đăng nhập",
            onClick = submit,
            icon = Icons.AutoMirrored.Rounded.Login,
            loading = state.isLoading,
        )
    }
}
