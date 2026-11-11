package com.moneyset.app.data.source.remote.dto

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(val name: String, val email: String, val password: String)

data class ForgotPasswordRequest(val email: String)

data class ForgotPasswordResponse(val resendAfterSeconds: Int? = null)

data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

data class UserDto(
    val name: String? = null,
    val email: String? = null,
)

data class LoginResponse(
    val token: String? = null,
    val user: UserDto? = null,
)

/** Body lỗi chuẩn từ backend: { "message": "..." } */
data class ErrorResponse(val message: String? = null)
