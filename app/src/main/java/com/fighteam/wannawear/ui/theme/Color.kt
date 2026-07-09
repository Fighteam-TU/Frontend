package com.fighteam.wannawear.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────
// ⚠️ 2026-07-09 라이트 테마 전환 — 앱 아이콘(크림 배경 + 코랄/피치 하트-옷걸이)의 톤을
// 앱 전체에 반영. 화면들이 MaterialTheme.colorScheme이 아니라 이 파일의 top-level 상수를
// 직접 참조하는 구조라, 값만 바꿔도 전체 화면에 일괄 적용됨(사용처 코드 변경 불필요).
// 변수명(AccentYellow 등)은 하위 호환을 위해 그대로 두되 실제 색상 의미는 "포인트 컬러"로
// 바뀌었음 — 새로 이 파일을 보는 사람은 이름 대신 정의된 실제 색상 값을 기준으로 판단할 것.
//
// ⚠️ 2026-07-09 2차 수정 — 1차 시도(배경/카드 모두 거의 순백)가 "너무 하얘서 밋밋하다"는
// 피드백을 받아 톤 다운. 배경은 좀 더 깊은 매트한 크림으로, 카드는 배경보다 살짝만 밝은
// 계열로 낮춰서 카드-배경 경계가 색 대비가 아니라 은은한 명암 차 + 그림자로 드러나게 함
// (Depop/에어비앤비류 라이트 테마들이 순백보다 이런 톤-온-톤 방식을 씀). 포인트 코랄도
// 조금 더 채도/깊이를 줘서 고급스러운 느낌으로.
// ─────────────────────────────────────────────────────────────────────

val BgPrimary        = Color(0xFFF2E9DC) // 매트한 웜 크림 (기존보다 한 톤 깊게)
val BgCard           = Color(0xFFFFFCF7) // 배경보다 살짝만 밝은 카드 — 순백 대신 톤온톤
val BgCardDark       = Color(0xFFE6D9C7) // 비활성/보조 카드 배경(진한 베이지)
val TextPrimary      = Color(0xFF261F19) // 아이콘 로고 텍스트와 같은 계열의 진한 웜 차콜
val TextSecondary    = Color(0xFF6E6259)
val TextTertiary     = Color(0xFFA0917F)
val AccentYellow     = Color(0xFFD9694F) // (구)포인트 옐로우 → 아이콘 하트/옷걸이의 딥 코랄
val AccentYellowText = Color(0xFFFFFBF7) // 코랄 배경 위 텍스트는 완전 화이트 대신 웜 화이트
val StatusComplete   = Color(0xFF2F9C63)
val StatusShipping   = Color(0xFFE0813F)
val StatusPending    = Color(0xFFC98A2E)
val LikeColor        = Color(0xFFED4E68) // 하트/좋아요는 코랄과 구분되는 진한 로즈 톤
val PassColor        = Color(0xFFD8433F)
val EcoGreen         = Color(0xFF2F9C63)
val KakaoBg          = Color(0xFFFEE500)
val KakaoText        = Color(0xFF191919)
val BorderSubtle     = Color(0x1F1A1410) // 라이트 배경용 저알파 웜 다크 보더 (카드 경계 살짝 강조)
val NavBgColor       = Color(0xFFFFFCF7)
// ✅ 카드 그림자용 — 순백 배경에선 그림자가 아예 안 보여서 카드가 "떠 보이는" 느낌이 없었음.
//    Modifier.shadow()에 사용해서 카드에 은은한 입체감을 준다.
val CardShadow       = Color(0x1F1A1410)
