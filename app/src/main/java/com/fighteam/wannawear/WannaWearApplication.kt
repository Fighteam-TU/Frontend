package com.fighteam.wannawear

import android.app.Application

/**
 * 앱 전역 Context 보관용.
 * TokenManager(토큰 저장소)가 Activity 없이도 SharedPreferences에 접근할 수 있도록
 * Application Context를 정적으로 노출한다.
 */
class WannaWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: Application
            private set
    }
}
