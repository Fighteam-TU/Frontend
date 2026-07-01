package com.fighteam.wannawear.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** 로그인/회원가입/토큰갱신 요청을 제외한 모든 요청에 Authorization: Bearer {accessToken} 헤더를 붙인다. */
class AuthInterceptor : Interceptor {

    private val noAuthPaths = listOf(
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/token/refresh"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (noAuthPaths.any { path.endsWith(it) }) {
            return chain.proceed(original)
        }
        val token = TokenManager.accessToken
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
