package com.fighteam.wannawear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ⚠️ 2026-07-09: darkColorScheme() → lightColorScheme()으로 변경. 화면들은 대부분 위 Color.kt의
// 상수를 직접 참조해서 그리지만, OutlinedTextField 테두리/Divider 기본색 등 우리가 명시적으로
// 안 채운 나머지 Material3 role들은 이 베이스 함수의 기본값을 따르므로, 라이트 배경에서 다크
// 스킴 기본값(밝은 회색 계열)을 쓰면 거의 안 보이는 문제가 생김 — 라이트 베이스로 맞춤.
private val LightColors = lightColorScheme(
    primary = AccentYellow,
    onPrimary = AccentYellowText,
    background = BgPrimary,
    surface = BgCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun WannaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
