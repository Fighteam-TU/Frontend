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

    private val prefs: SharedPreferences by lazy {
        WannaWearApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply() }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply() }

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
