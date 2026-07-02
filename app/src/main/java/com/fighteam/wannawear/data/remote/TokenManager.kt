package com.fighteam.wannawear.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.fighteam.wannawear.WannaWearApplication

/**
 * accessToken / refreshToken 저장소.
 * TODO: 배포 전엔 EncryptedSharedPreferences(androidx.security:security-crypto)로 교체 권장.
 *       지금은 백엔드 연동 우선순위상 평문 SharedPreferences로 빠르게 붙임.
 */
object TokenManager {
    private const val PREFS_NAME = "wannawear_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_DEVICE_TOKEN = "fcm_device_token"

    private val prefs: SharedPreferences by lazy {
        WannaWearApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply() }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply() }

    // ⚠️ 로그아웃 시 DELETE /device-tokens?token= 호출에 필요해서 별도로 들고 있음.
    //    FCM SDK에서 다시 getToken()으로 받아올 수도 있지만, 네트워크 왕복 없이 즉시 쓰기 위해 저장.
    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply() }

    val isLoggedIn: Boolean
        get() = !refreshToken.isNullOrBlank()

    fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    fun clear() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply()
    }
}
