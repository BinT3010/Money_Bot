package com.moneyset.app.data.source.remote

import com.moneyset.app.data.source.remote.dto.ChangePasswordRequest
import com.moneyset.app.data.source.remote.dto.ForgotPasswordRequest
import com.moneyset.app.data.source.remote.dto.ForgotPasswordResponse
import com.moneyset.app.data.source.remote.dto.LoginRequest
import com.moneyset.app.data.source.remote.dto.LoginResponse
import com.moneyset.app.data.source.remote.dto.RegisterRequest
import com.moneyset.app.data.source.remote.dto.ResetPasswordRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): LoginResponse

    /** Gửi mã 6 số tới email. */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ForgotPasswordResponse

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest)

    /** Cần Bearer token. Sai mật khẩu hiện tại nên trả 400 (không phải 401). */
    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)
}
