package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.fighteam.wannawear.data.model.DummyData
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.data.model.MatchItem
import com.fighteam.wannawear.ui.theme.*

@Composable
fun MatchesScreen() {
    val matches = DummyData.matches

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp)) {
            Text("교환 내역", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("${matches.size}건", color = TextSecondary, fontSize = 10.sp)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(matches) { match -> MatchCard(match) }
        }
    }
}

@Composable
fun MatchCard(match: MatchItem) {
    val statusColor = when (match.status) {
        ExchangeStatus.COMPLETE -> StatusComplete
        ExchangeStatus.SHIPPING -> StatusShipping
        ExchangeStatus.PENDING  -> StatusPending
    }

    Column(
        Modifier.fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                match.partner.avatar, null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(match.partner.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(match.date, color = TextSecondary, fontSize = 10.sp)
            }
            Text(
                match.status.label, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(BgCardDark, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth().height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                match.myItem.image, null,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            // 아이콘 대신 텍스트 사용
            Text("⇄", color = TextTertiary, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
            AsyncImage(
                match.theirItem.image, null,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(match.myItem.name, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(match.theirItem.name, color = TextSecondary, fontSize = 10.sp)
        }

        if (match.status == ExchangeStatus.PENDING) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText)
            ) {
                Text("교환 시작하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}
