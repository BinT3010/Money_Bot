package com.moneyset.app.domain.model

data class UserSession(
    val token: String,
    val name: String,
    val email: String,
)

/** Kết quả yêu cầu quên mật khẩu. [devCode] chỉ có ở chế độ mock (thay cho email thật). */
data class PasswordResetTicket(
    val email: String,
    val resendAfterSeconds: Int = 60,
    val devCode: String? = null,
)

/** Lỗi tài khoản có thông điệp hiển thị được cho người dùng (sai mật khẩu, email đã tồn tại...). */
class AuthException(message: String) : Exception(message)

enum class PasswordStrength(val label: String, val level: Int) {
    EMPTY("", 0),
    WEAK("Yếu", 1),
    MEDIUM("Trung bình", 2),
    STRONG("Mạnh", 3),
}

object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 8
    const val OTP_LENGTH = 6

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Vui lòng nhập họ tên"
        name.trim().length < 2 -> "Họ tên quá ngắn"
        else -> null
    }

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Vui lòng nhập email"
        !EMAIL_REGEX.matches(email.trim()) -> "Email không hợp lệ"
        else -> null
    }

    /** Lúc đăng nhập chỉ cần có nhập (quy tắc độ mạnh có thể thay đổi theo thời gian). */
    fun loginPasswordError(password: String): String? =
        if (password.isEmpty()) "Vui lòng nhập mật khẩu" else null

    /** Quy tắc cho mật khẩu mới (đăng ký, đặt lại, đổi mật khẩu). */
    fun newPasswordError(password: String): String? = when {
        password.isEmpty() -> "Vui lòng nhập mật khẩu"
        password.length < MIN_PASSWORD_LENGTH -> "Mật khẩu tối thiểu $MIN_PASSWORD_LENGTH ký tự"
        !password.any(Char::isLetter) || !password.any(Char::isDigit) -> "Mật khẩu cần có cả chữ và số"
        else -> null
    }

    fun confirmPasswordError(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Vui lòng nhập lại mật khẩu"
        confirm != password -> "Mật khẩu nhập lại không khớp"
        else -> null
    }

    fun otpError(code: String): String? =
        if (code.length != OTP_LENGTH || !code.all(Char::isDigit)) "Mã xác nhận gồm $OTP_LENGTH chữ số" else null

    fun strength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any(Char::isLetter) && password.any(Char::isDigit)) score++
        if (password.any(Char::isUpperCase) && password.any(Char::isLowerCase)) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            score <= 1 -> PasswordStrength.WEAK
            score <= 3 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }
}
