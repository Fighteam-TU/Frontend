package com.fighteam.wannawear.data.remote

import com.fighteam.wannawear.data.remote.dto.TokenRefreshRequest
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Route

/**
 * accessToken 만료로 401을 받으면 refreshToken으로 재발급을 시도하고, 성공하면 원래 요청을
 * 새 accessToken으로 재시도한다. refreshToken까지 만료/무효면 로그아웃 처리하고
 * SessionEvents로 UI에 알려서 로그인 화면으로 돌려보낸다.
 *
 * AuthInterceptor를 타지 않는 순수 OkHttpClient로 직접 refresh를 호출한다
 * (Authenticator는 동기 컨텍스트라 suspend Retrofit 함수를 못 쓰고,
 *  같은 인터셉터 체인을 다시 타면 재귀 위험이 있어 별도 클라이언트를 둔다).
 */
class TokenAuthenticator : Authenticator {

    private val gson = Gson()
    private val rawClient = OkHttpClient.Builder().build()

    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        // 이미 한 번 재시도했는데 또 401이면 무한루프 방지를 위해 포기
        if (responseCount(response) >= 2) return null

        val refreshToken = TokenManager.refreshToken ?: run {
            SessionEvents.markExpired()
            return null
        }

        synchronized(this) {
            // 다른 요청이 먼저 갱신해뒀을 수 있으니 한 번 더 확인
            val currentAccess = TokenManager.accessToken
            val requestAccess = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentAccess != null && currentAccess != requestAccess) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }

            return try {
                val bodyJson = gson.toJson(TokenRefreshRequest(refreshToken))
                val request = Request.Builder()
                    .url(RetrofitClient.BASE_URL + "/api/auth/token/refresh")
                    .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                rawClient.newCall(request).execute().use { httpResponse ->
                    if (!httpResponse.isSuccessful) {
                        TokenManager.clear()
                        SessionEvents.markExpired()
                        return null
                    }
                    val raw = httpResponse.body?.string()
                    val parsed = gson.fromJson(raw, RefreshEnvelope::class.java)
                    val newAccessToken = parsed?.data?.accessToken
                    if (newAccessToken.isNullOrBlank()) {
                        TokenManager.clear()
                        SessionEvents.markExpired()
                        return null
                    }
                    TokenManager.accessToken = newAccessToken
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                }
            } catch (e: Exception) {
                TokenManager.clear()
                SessionEvents.markExpired()
                null
            }
        }
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private data class RefreshEnvelope(val data: RefreshData?)
    private data class RefreshData(val accessToken: String?)
}
