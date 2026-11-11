package com.moneyset.app.data.auth

import android.content.Context
import com.google.gson.Gson
import com.moneyset.app.data.source.remote.AuthApi
import com.moneyset.app.data.source.remote.dto.ChangePasswordRequest
import com.moneyset.app.data.source.remote.dto.ErrorResponse
import com.moneyset.app.data.source.remote.dto.ForgotPasswordRequest
import com.moneyset.app.data.source.remote.dto.LoginRequest
import com.moneyset.app.data.source.remote.dto.LoginResponse
import com.moneyset.app.data.source.remote.dto.RegisterRequest
import com.moneyset.app.data.source.remote.dto.ResetPasswordRequest
import com.moneyset.app.domain.model.AuthException
import com.moneyset.app.domain.model.PasswordResetTicket
import com.moneyset.app.domain.model.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

interface AuthDataSource {
    suspend fun login(email: String, password: String): UserSession
    suspend fun register(name: String, email: String, password: String): UserSession
    suspend fun requestPasswordReset(email: String): PasswordResetTicket
    suspend fun resetPassword(email: String, code: String, newPassword: String)
    /** [email] chỉ dùng cho bản mock; bản API xác định user qua Bearer token. */
    suspend fun changePassword(email: String, currentPassword: String, newPassword: String)
}

private const val WRONG_CREDENTIALS = "Sai email hoặc mật khẩu"

/**
 * Tài khoản giả lưu trên máy (SharedPreferences, mật khẩu đã băm SHA-256) để đăng ký
 * xong tắt app mở lại vẫn đăng nhập được. Có sẵn tài khoản demo.
 * Mã quên mật khẩu không gửi email mà trả về [PasswordResetTicket.devCode] để hiện trên màn hình.
 */
@Singleton
class FakeAuthDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : AuthDataSource {

    private class ResetCode(val code: String, val expiresAt: Long, var attemptsLeft: Int)

    private val prefs = context.getSharedPreferences("mock_accounts", Context.MODE_PRIVATE)
    private val resetCodes = mutableMapOf<String, ResetCode>()
    private val mutex = Mutex()

    init {
        val demo = account(DEMO_EMAIL)
        if (demo == null) {
            saveAccount(DEMO_EMAIL, DEMO_NAME, DEMO_PASSWORD)
        } else if (demo.first != DEMO_NAME) {
            // Bản cũ đặt tên demo là "Phúc": đổi tên, giữ nguyên mật khẩu
            prefs.edit().putString(key(DEMO_EMAIL), "$DEMO_NAME\n${demo.second}").apply()
        }
    }

    private fun normalize(email: String) = email.trim().lowercase()
    private fun key(email: String) = "acc:${normalize(email)}"

    private fun hash(email: String, password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${normalize(email)}:$password".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun saveAccount(email: String, name: String, password: String) {
        prefs.edit().putString(key(email), "$name\n${hash(email, password)}").apply()
    }

    /** (tên, hash mật khẩu) */
    private fun account(email: String): Pair<String, String>? =
        prefs.getString(key(email), null)
            ?.split('\n', limit = 2)
            ?.takeIf { it.size == 2 }
            ?.let { it[0] to it[1] }

    private fun sessionFor(email: String, name: String) =
        UserSession(token = "mock:${normalize(email)}", name = name, email = normalize(email))

    override suspend fun login(email: String, password: String): UserSession = mutex.withLock {
        delay(700)
        val (name, passwordHash) = account(email) ?: throw AuthException(WRONG_CREDENTIALS)
        if (passwordHash != hash(email, password)) throw AuthException(WRONG_CREDENTIALS)
        sessionFor(email, name)
    }

    override suspend fun register(name: String, email: String, password: String): UserSession = mutex.withLock {
        delay(900)
        if (account(email) != null) throw AuthException("Email này đã được đăng ký")
        saveAccount(email, name, password)
        sessionFor(email, name)
    }

    override suspend fun requestPasswordReset(email: String): PasswordResetTicket = mutex.withLock {
        delay(800)
        account(email) ?: throw AuthException("Email chưa được đăng ký")
        val code = (100_000..999_999).random().toString()
        resetCodes[normalize(email)] = ResetCode(code, System.currentTimeMillis() + CODE_TTL_MS, MAX_ATTEMPTS)
        PasswordResetTicket(email = normalize(email), resendAfterSeconds = 60, devCode = code)
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) = mutex.withLock {
        delay(800)
        val key = normalize(email)
        val reset = resetCodes[key] ?: throw AuthException("Mã xác nhận đã hết hạn, vui lòng gửi lại mã")
        if (System.currentTimeMillis() > reset.expiresAt) {
            resetCodes.remove(key)
            throw AuthException("Mã xác nhận đã hết hạn, vui lòng gửi lại mã")
        }
        if (reset.code != code) {
            reset.attemptsLeft--
            if (reset.attemptsLeft <= 0) {
                resetCodes.remove(key)
                throw AuthException("Nhập sai quá nhiều lần, vui lòng gửi lại mã")
            }
            throw AuthException("Mã xác nhận không đúng (còn ${reset.attemptsLeft} lần thử)")
        }
        val (name, _) = account(email) ?: throw AuthException("Email chưa được đăng ký")
        saveAccount(email, name, newPassword)
        resetCodes.remove(key)
        Unit
    }

    override suspend fun changePassword(email: String, currentPassword: String, newPassword: String) = mutex.withLock {
        delay(800)
        val (name, passwordHash) = account(email) ?: throw AuthException("Không tìm thấy tài khoản")
        if (passwordHash != hash(email, currentPassword)) throw AuthException("Mật khẩu hiện tại không đúng")
        saveAccount(email, name, newPassword)
    }

    companion object {
        const val DEMO_EMAIL = "demo@moneyset.vn"
        const val DEMO_PASSWORD = "Demo1234"
        private const val DEMO_NAME = "Demo"
        private const val CODE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ATTEMPTS = 5
    }
}

class RemoteAuthDataSource @Inject constructor(
    private val api: AuthApi,
) : AuthDataSource {

    private val gson = Gson()

    override suspend fun login(email: String, password: String): UserSession =
        call(mapOf(400 to WRONG_CREDENTIALS, 401 to WRONG_CREDENTIALS)) {
            api.login(LoginRequest(email, password))
        }.toSession(email)

    override suspend fun register(name: String, email: String, password: String): UserSession =
        call(mapOf(409 to "Email này đã được đăng ký")) {
            api.register(RegisterRequest(name, email, password))
        }.toSession(email, name)

    override suspend fun requestPasswordReset(email: String): PasswordResetTicket =
        call(mapOf(404 to "Email chưa được đăng ký")) {
            api.forgotPassword(ForgotPasswordRequest(email))
        }.let { PasswordResetTicket(email = email, resendAfterSeconds = it.resendAfterSeconds ?: 60) }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        call(mapOf(400 to "Mã xác nhận không đúng hoặc đã hết hạn")) {
            api.resetPassword(ResetPasswordRequest(email, code, newPassword))
        }

    override suspend fun changePassword(email: String, currentPassword: String, newPassword: String) =
        call(mapOf(400 to "Mật khẩu hiện tại không đúng")) {
            api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
        }

    /** Đổi lỗi HTTP thành [AuthException]: ưu tiên "message" từ server, sau đó thông điệp mặc định theo mã lỗi. */
    private suspend fun <T> call(fallback: Map<Int, String> = emptyMap(), block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val serverMessage = e.response()?.errorBody()?.string()?.let { body ->
            runCatching { gson.fromJson(body, ErrorResponse::class.java)?.message }.getOrNull()
        }
        val message = serverMessage?.takeIf { it.isNotBlank() }
            ?: fallback[e.code()]
            ?: if (e.code() == 429) "Bạn thao tác quá nhiều lần, vui lòng thử lại sau" else throw e
        throw AuthException(message)
    }

    private fun LoginResponse.toSession(email: String, name: String? = null): UserSession {
        val token = token ?: throw AuthException("Máy chủ trả về phản hồi không hợp lệ")
        return UserSession(
            token = token,
            name = user?.name ?: name ?: email.substringBefore('@'),
            email = user?.email ?: email,
        )
    }
}
