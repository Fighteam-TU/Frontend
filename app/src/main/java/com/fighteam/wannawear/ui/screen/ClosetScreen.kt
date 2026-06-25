package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.data.model.DummyData
import com.fighteam.wannawear.ui.theme.*

@Composable
fun ClosetScreen() {
    val items = DummyData.myCloset

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 헤더
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("내 옷장", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("${items.size}개 등록", color = TextSecondary, fontSize = 10.sp)
            }
            Button(
                onClick = {},
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("추가하기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item -> ClosetItemCard(item) }

            // 추가 버튼 카드
            item {
                Box(
                    Modifier.height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .background(BgPrimary.copy(alpha = 0.02f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(40.dp).background(BgCardDark, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        Text("옷 추가하기", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun ClosetItemCard(item: ClothingItem) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(BgCard)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(152.dp)) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isListed) {
                    Text(
                        "교환중", color = AccentYellowText, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(10.dp).background(AccentYellow, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp).align(Alignment.TopStart)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(item.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${item.brand} · ${item.size}", color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}
