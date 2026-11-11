package com.moneyset.app.data.auth

import com.moneyset.app.core.suspendRunCatching
import com.moneyset.app.data.repository.FinanceRepository
import com.moneyset.app.domain.model.AuthException
import com.moneyset.app.domain.model.PasswordResetTicket
import com.moneyset.app.domain.model.UserSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class AuthRepository @Inject constructor(
    private val dataSource: AuthDataSource,
    private val sessionStore: SessionStore,
    private val financeRepository: FinanceRepository,
) {
    /** null = chưa đăng nhập. */
    val session: Flow<UserSession?> = sessionStore.session

    suspend fun login(email: String, password: String): Result<UserSession> = suspendRunCatching {
        startSession(dataSource.login(email.trim(), password))
    }

    /** Đăng ký xong đăng nhập luôn. */
    suspend fun register(name: String, email: String, password: String): Result<UserSession> = suspendRunCatching {
        startSession(dataSource.register(name.trim(), email.trim(), password))
    }

    suspend fun requestPasswordReset(email: String): Result<PasswordResetTicket> = suspendRunCatching {
        dataSource.requestPasswordReset(email.trim())
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Unit> = suspendRunCatching {
        dataSource.resetPassword(email.trim(), code, newPassword)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = suspendRunCatching {
        val email = sessionStore.session.first()?.email ?: throw AuthException("Phiên đăng nhập đã hết hạn")
        dataSource.changePassword(email, currentPassword, newPassword)
    }

    suspend fun logout() {
        sessionStore.clear()
        financeRepository.clear()
    }

    private suspend fun startSession(session: UserSession): UserSession {
        // Xóa cache dữ liệu của phiên trước (nếu có) để tải lại cho user mới
        financeRepository.clear()
        sessionStore.save(session)
        return session
    }
}
