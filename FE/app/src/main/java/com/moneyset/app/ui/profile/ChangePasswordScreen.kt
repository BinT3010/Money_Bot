package com.moneyset.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.domain.model.AuthValidator
import com.moneyset.app.ui.auth.AuthBanner
import com.moneyset.app.ui.auth.BannerKind
import com.moneyset.app.ui.auth.PasswordField
import com.moneyset.app.ui.auth.PasswordStrengthMeter
import com.moneyset.app.ui.auth.authMessage
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.components.ScreenTopBar
import com.moneyset.app.ui.components.WhiteCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val current: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val currentError: String? = null,
    val newPasswordError: String? = null,
    val confirmError: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun onCurrentChange(v: String) = _state.update { it.copy(current = v, currentError = null, error = null) }
    fun onNewChange(v: String) = _state.update { it.copy(newPassword = v, newPasswordError = null, error = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v, confirmError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.isLoading || s.done) return
        val validated = s.copy(
            currentError = if (s.current.isEmpty()) "Vui lòng nhập mật khẩu hiện tại" else null,
            newPasswordError = AuthValidator.newPasswordError(s.newPassword)
                ?: if (s.newPassword == s.current) "Mật khẩu mới phải khác mật khẩu hiện tại" else null,
            confirmError = AuthValidator.confirmPasswordError(s.newPassword, s.confirm),
        )
        if (validated.currentError != null || validated.newPasswordError != null || validated.confirmError != null) {
            _state.value = validated
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.changePassword(s.current, s.newPassword)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.authMessage()) } }
        }
    }
}

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.submit()
    }
    // Hiện thông báo thành công một chút rồi quay lại
    LaunchedEffect(state.done) {
        if (state.done) {
            delay(1_500)
            onBack()
        }
    }
    val enabled = !state.isLoading && !state.done

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        ScreenTopBar(overline = "Bảo mật", title = "Đổi mật khẩu", onBack = onBack)
        WhiteCard(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PasswordField(state.current, viewModel::onCurrentChange, "Mật khẩu hiện tại", state.currentError, enabled)
            Column {
                PasswordField(state.newPassword, viewModel::onNewChange, "Mật khẩu mới", state.newPasswordError, enabled)
                PasswordStrengthMeter(state.newPassword)
            }
            PasswordField(
                state.confirm, viewModel::onConfirmChange, "Nhập lại mật khẩu mới", state.confirmError, enabled,
                imeAction = ImeAction.Done,
                onDone = submit,
            )
            state.error?.let { AuthBanner(it, BannerKind.ERROR) }
            if (state.done) AuthBanner("Đổi mật khẩu thành công!", BannerKind.SUCCESS)
            GradientButton(
                text = "Cập nhật mật khẩu",
                onClick = submit,
                icon = Icons.Rounded.Key,
                loading = state.isLoading,
                enabled = !state.done,
            )
        }
    }
}
