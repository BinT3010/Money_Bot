package com.moneyset.app.ui.auth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.domain.model.AuthValidator
import com.moneyset.app.domain.model.PasswordResetTicket
import com.moneyset.app.ui.components.GradientButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    /** != null -> đã gửi mã, chuyển sang màn nhập mã */
    val sent: PasswordResetTicket? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState(email = savedStateHandle.get<String>(ARG_EMAIL).orEmpty()))
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = null, error = null) }

    fun sendCode() {
        val s = _state.value
        if (s.isLoading) return
        AuthValidator.emailError(s.email)?.let { e ->
            _state.update { it.copy(emailError = e) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.requestPasswordReset(s.email)
                .onSuccess { t -> _state.update { it.copy(isLoading = false, sent = t) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.authMessage()) } }
        }
    }

    fun onNavigated() = _state.update { it.copy(sent = null) }

    companion object {
        const val ARG_EMAIL = "email"
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onCodeSent: (PasswordResetTicket) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.sendCode()
    }
    LaunchedEffect(state.sent) {
        state.sent?.let {
            onCodeSent(it)
            viewModel.onNavigated()
        }
    }

    AuthScaffold(
        title = "Quên mật khẩu",
        subtitle = "Nhập email đã đăng ký, chúng tôi sẽ gửi mã xác nhận gồm 6 chữ số.",
        onBack = onBack,
        mascotSize = 90.dp,
    ) {
        AuthTextField(
            state.email, viewModel::onEmailChange, "Email", Icons.Rounded.Email, state.emailError, !state.isLoading,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        )
        state.error?.let { AuthBanner(it, BannerKind.ERROR) }
        GradientButton(
            text = "Gửi mã xác nhận",
            onClick = submit,
            icon = Icons.Rounded.MarkEmailRead,
            loading = state.isLoading,
        )
    }
}
