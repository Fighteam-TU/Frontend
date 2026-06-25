package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.ui.theme.*

@Composable
fun ProfileScreen() {
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
                    model = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=120&h=120&fit=crop",
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
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
                Text("김지은", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("서울 마포구", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(5) { i ->
                        Box(Modifier.size(6.dp).background(
                            if (i < 4) AccentYellow else TextTertiary, CircleShape
                        ))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("매너 온도 4.2", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        // 통계
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(BgCard, RoundedCornerShape(16.dp))
        ) {
            listOf(
                Triple("12", "교환 완료", false),
                Triple("4",  "등록 아이템", true),
                Triple("78%","매칭률", true)
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
                    Text(label, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 에코 스트립
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
                Text("탄소 4.2kg 절감", color = EcoGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("12번의 교환으로 지구를 아꼈어요", color = TextSecondary, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 메뉴 리스트 (아이콘 대신 텍스트 화살표)
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("프로필 수정", "교환 내역", "설정").forEach { label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BgCard, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("›", color = TextTertiary, fontSize = 20.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
