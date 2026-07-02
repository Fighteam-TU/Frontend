package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.ui.theme.*

@Composable
fun ProfileScreen(onNavigateToAddress: () -> Unit = {}) {
    val me = AppState.myProfile
    // #10: 하드코딩 제거 → AppState 실제 데이터 사용
    val completedCount by remember {
        derivedStateOf { AppState.matches.count { it.status == ExchangeStatus.COMPLETE } }
    }
    val myItemCount by remember {
        derivedStateOf { AppState.myCloset.size }
    }
    // 매칭률 = 매칭 성사 수 / 내가 좋아요 보낸 수 (0이면 0%)
    val matchRate by remember {
        derivedStateOf {
            val liked   = AppState.sentLikes.size + AppState.matches.size
            val matched = AppState.matches.size
            if (liked == 0) 0 else (matched * 100 / liked)
        }
    }
    // 에코: 교환 완료 건당 탄소 0.35kg 절감 (더미 계산)
    val carbonSaved = (completedCount * 0.35f)
    // 닉네임 밑 위치 표시 — 하드코딩("서울 마포구") 대신 실제 등록된 배송지 사용
    val addressLabel by remember {
        derivedStateOf {
            val addr = AppState.addresses.firstOrNull { it.isDefault } ?: AppState.addresses.firstOrNull()
            addr?.address1?.takeIf { it.isNotBlank() } ?: "배송지 미등록"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // 프로필 헤더
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model              = me?.avatar,
                    contentDescription = null,
                    modifier           = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale       = ContentScale.Crop
                )
                Box(
                    Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(AccentYellow, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📷", fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(me?.name ?: "...", color = TextPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.Black)
                Text(addressLabel, color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                // ✅ 교환 완료 0건이면 불 0개, 완료 수에 비례해 자연스럽게 증가
                val filledDots = when {
                    completedCount == 0 -> 0
                    completedCount < 3  -> 1
                    completedCount < 6  -> 2
                    completedCount < 10 -> 3
                    completedCount < 15 -> 4
                    else                -> 5
                }
                    repeat(5) { i ->
                        Box(
                            Modifier.size(6.dp).background(
                                if (i < filledDots) AccentYellow else TextTertiary, CircleShape
                            )
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("매너 온도 ${String.format("%.1f", (4.0f + completedCount * 0.05f).coerceAtMost(5.0f))}",
                        color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        // 통계 카드 — 실제 데이터 반영
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(BgCard, RoundedCornerShape(16.dp))
        ) {
            listOf(
                Triple("$completedCount", "교환 완료",    false),
                Triple("$myItemCount",    "등록 아이템",  true),
                Triple("$matchRate%",     "매칭률",       true)
            ).forEach { (value, label, showDivider) ->
                if (showDivider) {
                    Box(
                        Modifier.width(1.dp).height(60.dp)
                            .background(BorderSubtle)
                            .align(Alignment.CenterVertically)
                    )
                }
                Column(
                    Modifier.weight(1f).padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(label, color = TextSecondary, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 에코 스트립 — 교환 완료 건수에 따라 동적
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(Color(0x194ADE80), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌱", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("탄소 ${String.format("%.1f", carbonSaved)}kg 절감",
                    color = EcoGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (completedCount == 0) "첫 교환으로 지구를 지켜보세요!"
                    else "${completedCount}번의 교환으로 지구를 아꼈어요",
                    color = TextSecondary, fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 메뉴
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "프로필 수정" to {},
                "배송지 관리" to onNavigateToAddress,
                "교환 내역" to {},
                "설정" to {}
            ).forEach { (label, onClick) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BgCard, RoundedCornerShape(12.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(label, color = TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                    Text("›", color = TextTertiary, fontSize = 20.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
