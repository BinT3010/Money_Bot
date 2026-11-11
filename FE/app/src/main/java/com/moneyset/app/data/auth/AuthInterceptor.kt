package com.moneyset.app.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Gắn "Authorization: Bearer <token>" vào mọi request.
 * Máy chủ trả 401 (token hết hạn/sai) -> xóa phiên, app tự quay về màn đăng nhập.
 * OkHttp gọi interceptor trên luồng nền nên runBlocking ở đây không chặn UI.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionStore.session.first()?.token }
        val original = chain.request()
        val request = if (token != null && original.header("Authorization") == null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        val response = chain.proceed(request)
        // 401 ở các API tài khoản (vd. đổi mật khẩu) là lỗi nghiệp vụ, không phải token hết hạn
        val isAuthEndpoint = request.url.encodedPath.contains("/auth/")
        if (response.code == 401 && token != null && !isAuthEndpoint) {
            runBlocking { sessionStore.clear() }
        }
        return response
    }
}
