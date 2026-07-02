package com.fighteam.wannawear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * 앱 전역 Context 보관용.
 * TokenManager(토큰 저장소)가 Activity 없이도 SharedPreferences에 접근할 수 있도록
 * Application Context를 정적으로 노출한다.
 */
class WannaWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        createNotificationChannel()
    }

    // ⚠️ AndroidManifest.xml의 default_notification_channel_id("wannawear_default_channel")와
    // 반드시 같은 id여야 FCM 포그라운드/백그라운드 알림이 정상적으로 채널에 표시된다.
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "wannawear_default_channel",
                "WannaWear 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "매칭, 채팅, 교환 진행 상황 알림"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var appContext: Application
            private set
    }
}

