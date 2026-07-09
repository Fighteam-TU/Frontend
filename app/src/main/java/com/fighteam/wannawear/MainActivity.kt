package com.fighteam.wannawear

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.fighteam.wannawear.ui.navigation.WannaWearNavGraph
import com.fighteam.wannawear.ui.theme.WannaWearTheme

class MainActivity : ComponentActivity() {

    // 거부해도 앱은 정상 동작함 — 인앱 알림 목록(GET /api/notifications)은 권한과 무관하게 계속 보임.
    // 시스템 푸시(FCM 알림바 표시)만 못 받게 되는 정도라 별도 안내 UI 없이 조용히 요청만 해둠.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⚠️ 2026-07-09 라이트 테마 전환 — enableEdgeToEdge() 기본 스타일은 기기의 시스템
        // 다크모드 설정을 따라 상태바/내비게이션바 아이콘 색을 정하는데, 이 앱은 시스템 설정과
        // 무관하게 항상 크림 배경(라이트) 테마를 쓰므로 아이콘이 시스템이 다크모드일 때 밝은색으로
        // 나와 배경과 거의 안 보이는 문제가 생길 수 있음 — 항상 "밝은 배경 위 어두운 아이콘"
        // 스타일로 고정.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNotificationPermissionIfNeeded()
        setContent {
            WannaWearTheme {
                WannaWearNavGraph()
            }
        }
    }

    /** Android 13(API 33)+ 는 알림 표시에 런타임 권한이 필요함(스펙 0-1절 5번) */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
