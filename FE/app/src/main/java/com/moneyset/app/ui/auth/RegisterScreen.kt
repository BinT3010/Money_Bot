package com.moneyset.app.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.domain.model.AuthValidator
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.RoseRed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val acceptedTerms: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val termsError: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = null, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v, confirmError = null, error = null) }
    fun onTermsChange(v: Boolean) = _state.update { it.copy(acceptedTerms = v, termsError = null) }

    fun register() {
        val s = _state.value
        if (s.isLoading) return
        val validated = s.copy(
            nameError = AuthValidator.nameError(s.name),
            emailError = AuthValidator.emailError(s.email),
            passwordError = AuthValidator.newPasswordError(s.password),
            confirmError = AuthValidator.confirmPasswordError(s.password, s.confirm),
            termsError = if (s.acceptedTerms) null else "Bạn cần đồng ý với điều khoản để tiếp tục",
        )
        val hasError = listOf(
            validated.nameError, validated.emailError, validated.passwordError, validated.confirmError, validated.termsError,
        ).any { it != null }
        if (hasError) {
            _state.value = validated
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.register(s.name, s.email, s.password)
                // Thành công: đã đăng nhập luôn, AppRoot tự chuyển vào app chính
                .onSuccess { _state.update { it.copy(isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.authMessage()) } }
        }
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, viewModel: RegisterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.register()
    }
    val enabled = !state.isLoading

    AuthScaffold(
        title = "Tạo tài khoản",
        subtitle = "Chỉ mất 30 giây để bắt đầu quản lý chi tiêu ✨",
        onBack = onBack,
        mascotSize = 90.dp,
        footer = { AuthFooterLink("Đã có tài khoản?", "Đăng nhập", onBack) },
    ) {
        AuthTextField(state.name, viewModel::onNameChange, "Họ và tên", Icons.Rounded.Person, state.nameError, enabled)
        AuthTextField(
            state.email, viewModel::onEmailChange, "Email", Icons.Rounded.Email, state.emailError, enabled,
            keyboardType = KeyboardType.Email,
        )
        Column {
            PasswordField(state.password, viewModel::onPasswordChange, "Mật khẩu", state.passwordError, enabled)
            PasswordStrengthMeter(state.password)
        }
        PasswordField(
            state.confirm, viewModel::onConfirmChange, "Nhập lại mật khẩu", state.confirmError, enabled,
            imeAction = ImeAction.Done,
            onDone = submit,
        )
        Column {
            Row(
                Modifier.clickable(enabled = enabled) { viewModel.onTermsChange(!state.acceptedTerms) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.acceptedTerms, onCheckedChange = viewModel::onTermsChange, enabled = enabled)
                Text(
                    buildAnnotatedString {
                        append("Tôi đồng ý với ")
                        withStyle(SpanStyle(color = Indigo, fontWeight = FontWeight.SemiBold)) { append("Điều khoản sử dụng") }
                        append(" và ")
                        withStyle(SpanStyle(color = Indigo, fontWeight = FontWeight.SemiBold)) { append("Chính sách bảo mật") }
                    },
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.termsError?.let {
                Text(it, color = RoseRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
            }
        }
        state.error?.let { AuthBanner(it, BannerKind.ERROR) }
        GradientButton(
            text = "Tạo tài khoản",
            onClick = submit,
            icon = Icons.Rounded.PersonAdd,
            loading = state.isLoading,
        )
    }
}
