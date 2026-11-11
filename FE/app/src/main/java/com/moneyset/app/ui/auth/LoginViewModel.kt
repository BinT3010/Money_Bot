package com.moneyset.app.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.domain.model.AuthValidator
import com.moneyset.app.domain.model.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: UserSession) : SessionState
}

/** Quyết định hiển thị luồng tài khoản hay app chính. */
@HiltViewModel
class SessionViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val state: StateFlow<SessionState> = authRepository.session
        .map { if (it == null) SessionState.LoggedOut else SessionState.LoggedIn(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    val info: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // Màn đặt lại mật khẩu trả kết quả về đây qua SavedStateHandle của back stack entry
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_RESET_EMAIL, null).filterNotNull().collect { email ->
                _state.update {
                    LoginUiState(email = email, info = "Đặt lại mật khẩu thành công! Hãy đăng nhập bằng mật khẩu mới.")
                }
                savedStateHandle[KEY_RESET_EMAIL] = null
            }
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun login() {
        val s = _state.value
        if (s.isLoading) return
        val emailError = AuthValidator.emailError(s.email)
        val passwordError = AuthValidator.loginPasswordError(s.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.login(s.email, s.password)
                // Thành công: SessionViewModel nhận phiên mới và tự chuyển sang app chính
                .onSuccess { _state.update { it.copy(isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.authMessage()) } }
        }
    }

    companion object {
        const val KEY_RESET_EMAIL = "reset_email"
    }
}
