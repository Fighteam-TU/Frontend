package com.fighteam.wannawear.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fighteam.wannawear.MainActivity
import com.fighteam.wannawear.data.AppState
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 서비스. AndroidManifest.xml에 <service>로 등록돼 있어야 실제로 동작한다.
 *
 * - onNewToken: 토큰이 (재)발급될 때마다 호출됨. 로그인 전에 먼저 호출될 수도 있어서
 *   AppState.registerDeviceToken()이 일단 로컬(TokenManager)에 저장해두고, 로그인 전이면
 *   서버 호출은 건너뛴다 — 로그인 성공 시점(AppState.loadInitialData)에 다시 시도함.
 * - onMessageReceived: 앱이 포그라운드일 때는 FCM이 시스템 알림을 자동으로 안 띄워주므로 직접 띄운다.
 *   (백그라운드/종료 상태에선 OS가 notification payload를 보고 알아서 띄워줌 — 이 콜백 자체가 안 옴)
 *
 * ⚠️ TODO: setSmallIcon을 시스템 기본 아이콘으로 임시 처리해뒀음. 알림바에서 예쁘게 보이려면
 *          단색/투명 배경의 전용 벡터 아이콘(drawable)을 만들어서 교체하는 걸 권장.
 * ⚠️ TODO: exchangeId로 채팅/교환상세 화면까지 딥링크하는 건 아직 안 붙어있음 — 알림 목록/상세
 *          화면 네비게이션이 만들어지면 MainActivity에서 intent extra를 읽어 라우팅해야 함.
 */
class WannaWearMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppState.registerDeviceToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "WannaWear"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val exchangeId = message.data["exchangeId"]?.toIntOrNull()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (exchangeId != null) putExtra("exchangeId", exchangeId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, exchangeId ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (canPost) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    companion object {
        // ⚠️ WannaWearApplication.createNotificationChannel()과 AndroidManifest.xml의
        //    default_notification_channel_id 메타데이터, 이 3곳의 채널 id가 전부 같아야 함.
        private const val CHANNEL_ID = "wannawear_default_channel"
    }
}
