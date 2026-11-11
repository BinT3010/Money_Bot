package com.moneyset.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.domain.model.AuthValidator
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.RoseRed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirm: String = "",
    val codeError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val error: String? = null,
    val info: String? = null,
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    /** Số giây còn lại trước khi được gửi lại mã. */
    val resendIn: Int = 0,
    /** Chỉ có ở chế độ mock: mã hiển thị thay cho email. */
    val devCode: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ResetPasswordUiState(
            email = savedStateHandle.get<String>(ARG_EMAIL).orEmpty(),
            devCode = savedStateHandle.get<String>(ARG_DEV_CODE)?.takeIf { it.isNotBlank() },
        ),
    )
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()
    private var countdown: Job? = null

    init {
        startCountdown(savedStateHandle.get<Int>(ARG_WAIT) ?: 60)
    }

    fun onCodeChange(v: String) = _state.update { it.copy(code = v, codeError = null, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v, confirmError = null, error = null) }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (s in seconds downTo 0) {
                _state.update { it.copy(resendIn = s) }
                if (s > 0) delay(1_000)
            }
        }
    }

    fun resend() {
        val s = _state.value
        if (s.resendIn > 0 || s.isResending) return
        _state.update { it.copy(isResending = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.requestPasswordReset(s.email)
                .onSuccess { t ->
                    _state.update {
                        it.copy(isResending = false, code = "", devCode = t.devCode, info = "Đã gửi lại mã mới tới ${t.email}")
                    }
                    startCountdown(t.resendAfterSeconds)
                }
                .onFailure { e -> _state.update { it.copy(isResending = false, error = e.authMessage()) } }
        }
    }

    fun submit() {
        val s = _state.value
        if (s.isLoading) return
        val validated = s.copy(
            codeError = AuthValidator.otpError(s.code),
            passwordError = AuthValidator.newPasswordError(s.password),
            confirmError = AuthValidator.confirmPasswordError(s.password, s.confirm),
        )
        if (validated.codeError != null || validated.passwordError != null || validated.confirmError != null) {
            _state.value = validated
            return
        }
        _state.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.resetPassword(s.email, s.code, s.password)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.authMessage()) } }
        }
    }

    companion object {
        const val ARG_EMAIL = "email"
        const val ARG_DEV_CODE = "devCode"
        const val ARG_WAIT = "wait"
    }
}

@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    onDone: (email: String) -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.submit()
    }
    LaunchedEffect(state.done) { if (state.done) onDone(state.email) }
    val enabled = !state.isLoading

    AuthScaffold(
        title = "Đặt lại mật khẩu",
        subtitle = "Nhập mã 6 số đã gửi tới ${state.email} và tạo mật khẩu mới.",
        onBack = onBack,
        mascotSize = 90.dp,
    ) {
        state.devCode?.let { AuthBanner("Chế độ demo — mã xác nhận của bạn: $it", BannerKind.INFO) }
        state.info?.let { AuthBanner(it, BannerKind.SUCCESS) }
        Column {
            OtpField(state.code, viewModel::onCodeChange, isError = state.codeError != null, enabled = enabled)
            state.codeError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = RoseRed, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Không nhận được mã? ", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                if (state.resendIn > 0) {
                    Text("Gửi lại sau ${state.resendIn}s", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                } else {
                    TextLink(
                        if (state.isResending) "Đang gửi..." else "Gửi lại mã",
                        onClick = viewModel::resend,
                        enabled = !state.isResending && enabled,
                    )
                }
            }
        }
        Column {
            PasswordField(state.password, viewModel::onPasswordChange, "Mật khẩu mới", state.passwordError, enabled)
            PasswordStrengthMeter(state.password)
        }
        PasswordField(
            state.confirm, viewModel::onConfirmChange, "Nhập lại mật khẩu mới", state.confirmError, enabled,
            imeAction = ImeAction.Done,
            onDone = submit,
        )
        state.error?.let { AuthBanner(it, BannerKind.ERROR) }
        GradientButton(
            text = "Đặt lại mật khẩu",
            onClick = submit,
            icon = Icons.Rounded.LockReset,
            loading = state.isLoading,
        )
    }
}
