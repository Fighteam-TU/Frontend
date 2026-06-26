package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.data.model.MatchItem
import com.fighteam.wannawear.ui.theme.*

@Composable
fun MatchesScreen(
    onOpenChat:          (Int) -> Unit = {},
    onOpenShippingGuide: (Int) -> Unit = {}
) {
    val matches = AppState.matches

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text("교환 내역", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("총 ${matches.size}건", color = TextSecondary, fontSize = 10.sp)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(matches, key = { it.id }) { match ->
                MatchCard(
                    match               = match,
                    onOpenChat          = { onOpenChat(match.id) },
                    onOpenShippingGuide = { onOpenShippingGuide(match.id) }
                )
            }
        }
    }
}

@Composable
fun MatchCard(
    match:               MatchItem,
    onOpenChat:          () -> Unit = {},
    onOpenShippingGuide: () -> Unit = {}
) {
    val (statusColor, statusBg) = when (match.status) {
        ExchangeStatus.WAITING   -> Pair(StatusPending,  StatusPending.copy(alpha = 0.12f))
        ExchangeStatus.MATCHED   -> Pair(AccentYellow,   AccentYellow.copy(alpha = 0.12f))
        ExchangeStatus.CONFIRMED -> Pair(StatusShipping, StatusShipping.copy(alpha = 0.12f))
        ExchangeStatus.SHIPPING  -> Pair(StatusShipping, StatusShipping.copy(alpha = 0.12f))
        ExchangeStatus.COMPLETE  -> Pair(StatusComplete, StatusComplete.copy(alpha = 0.12f))
    }

    Column(
        Modifier.fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // ── 헤더 ──────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(match.partner.avatar, null,
                modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(match.partner.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(match.date,         color = TextSecondary, fontSize = 10.sp)
            }
            Text(
                match.status.label,
                color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(statusBg, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (match.status == ExchangeStatus.MATCHED || match.status == ExchangeStatus.WAITING) {
            Spacer(Modifier.height(8.dp))
            Text(match.status.description, color = TextSecondary, fontSize = 11.sp)
        }

        Spacer(Modifier.height(14.dp))

        // ── 아이템 이미지 ─────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AsyncImage(match.myItem.image, null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(match.myItem.size, color = AccentYellowText, fontSize = 9.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(AccentYellow, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
            Text("⇄", color = TextTertiary, fontSize = 18.sp)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AsyncImage(match.theirItem.image, null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(match.theirItem.size, color = AccentYellowText, fontSize = 9.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(AccentYellow, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(match.myItem.name,    color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(match.theirItem.name, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
        }

        // 사이즈 비교
        if (match.status == ExchangeStatus.MATCHED || match.status == ExchangeStatus.CONFIRMED) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(BgCardDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("내 옷",   color = TextTertiary,  fontSize = 9.sp)
                    Text(match.myItem.size, color = AccentYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (match.myItem.heightFit.isNotEmpty())
                        Text(match.myItem.heightFit, color = TextTertiary, fontSize = 9.sp)
                }
                Box(Modifier.width(1.dp).height(32.dp).background(BorderSubtle).align(Alignment.CenterVertically))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("상대 옷", color = TextTertiary, fontSize = 9.sp)
                    Text(match.theirItem.size, color = AccentYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (match.theirItem.heightFit.isNotEmpty())
                        Text(match.theirItem.heightFit, color = TextTertiary, fontSize = 9.sp)
                }
            }
        }

        // 채팅 미리보기
        if (AppState.isChatOpen(match.status) && match.messages.isNotEmpty()) {
            val lastMsg = match.messages.last()
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(BgCardDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                    tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (lastMsg.senderId == 0) "나: ${lastMsg.text}" else "${match.partner.name}: ${lastMsg.text}",
                    color = TextSecondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f)
                )
                Text(lastMsg.timestamp, color = TextTertiary, fontSize = 9.sp)
            }
        }

        // ── 액션 버튼 ─────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        when (match.status) {

            ExchangeStatus.MATCHED -> {
                Button(
                    onClick  = { AppState.confirmExchange(match.id) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText)
                ) {
                    Text("교환 확정하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = onOpenChat,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("채팅하기", fontSize = 13.sp)
                }
            }

            ExchangeStatus.CONFIRMED -> {
                Button(
                    onClick  = onOpenShippingGuide,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = StatusShipping.copy(alpha = 0.15f),
                        contentColor   = StatusShipping
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("배송 안내 보기", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = onOpenChat,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("채팅하기", fontSize = 13.sp)
                }
            }

            ExchangeStatus.SHIPPING -> {
                Button(
                    onClick  = { AppState.completeExchange(match.id) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = StatusComplete.copy(alpha = 0.15f),
                        contentColor   = StatusComplete
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("교환 완료", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = onOpenChat,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("채팅하기", fontSize = 13.sp)
                }
            }

            ExchangeStatus.COMPLETE -> {
                Row(
                    Modifier.fillMaxWidth()
                        .background(StatusComplete.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = StatusComplete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("교환이 완료됐어요 🎉", color = StatusComplete, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            else -> {}
        }
    }
}
