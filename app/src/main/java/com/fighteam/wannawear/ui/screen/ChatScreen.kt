package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.DummyData
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch


@Composable
fun ChatScreen(matchId: Int, onBack: () -> Unit) {
    val match = AppState.matches.firstOrNull { it.id == matchId }
        ?: run { onBack(); return }

    val messages = match.messages
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chatOpen  = AppState.isChatOpen(match.status)

    // ✅ messages.size 로 항상 마지막 인덱스 정확히 지정
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty())
            listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(BgPrimary).imePadding()) {

        // ── 앱바 ───────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .background(BgCard)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            AsyncImage(
                match.partner.avatar, null,
                modifier      = Modifier.size(36.dp).clip(CircleShape),
                contentScale  = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(match.partner.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (match.status) {
                        ExchangeStatus.MATCHED   -> "교환 대기 중"
                        ExchangeStatus.CONFIRMED -> "배송 준비 중"
                        ExchangeStatus.SHIPPING  -> "배송 중"
                        ExchangeStatus.COMPLETE  -> "교환 완료"
                        else                     -> ""
                    },
                    color = TextSecondary, fontSize = 10.sp
                )
            }
            // 교환 아이템 미니 프리뷰
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(match.myItem.image, null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                Text("⇄", color = TextTertiary, fontSize = 12.sp)
                AsyncImage(match.theirItem.image, null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(8.dp))
        }

        // ── 교환완료 안내 배너 ─────────────────────────────────────
        if (!chatOpen) {
            Row(
                Modifier.fillMaxWidth()
                    .background(BgCardDark)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("교환이 완료되어 채팅이 종료됐어요", color = TextTertiary, fontSize = 12.sp)
            }
        }

        // ── 메시지 목록 ────────────────────────────────────────────
        LazyColumn(
            state          = listState,
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 날짜 구분선 (더미 - 오늘)
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f), color = BorderSubtle)
                    Text("  오늘  ", color = TextTertiary, fontSize = 10.sp)
                    Divider(Modifier.weight(1f), color = BorderSubtle)
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == DummyData.me.id
                ChatBubble(
                    text      = msg.text,
                    timestamp = msg.timestamp,
                    isMe      = isMe,
                    avatar    = if (!isMe) match.partner.avatar else null
                )
            }

            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💬", fontSize = 32.sp)
                            Text("${match.partner.name}님과 대화를 시작해보세요", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ── 입력창 ─────────────────────────────────────────────────
        if (chatOpen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    placeholder   = { Text("메시지를 입력하세요...", color = TextSecondary, fontSize = 13.sp) },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    singleLine    = false,
                    maxLines      = 4,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor    = TextPrimary,
                        unfocusedTextColor  = TextPrimary,
                        focusedContainerColor   = BgCardDark,
                        unfocusedContainerColor = BgCardDark,
                        focusedBorderColor   = AccentYellow,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            AppState.sendMessage(matchId, inputText)
                            inputText = ""
                            // LaunchedEffect(messages.size) 가 자동 스크롤 처리
                        }
                    })
                )
                Spacer(Modifier.width(8.dp))
                // 전송 버튼
                Box(
                    Modifier
                        .size(44.dp)
                        .background(
                            if (inputText.isNotBlank()) AccentYellow else BgCardDark,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            AppState.sendMessage(matchId, inputText)
                            inputText = ""
                            // LaunchedEffect(messages.size) 가 자동 스크롤 처리
                        }
                    }) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "전송",
                            tint     = if (inputText.isNotBlank()) AccentYellowText else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── 말풍선 ────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(
    text: String,
    timestamp: String,
    isMe: Boolean,
    avatar: String?
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        // 상대방 아바타
        if (!isMe && avatar != null) {
            AsyncImage(avatar, null,
                modifier     = Modifier.size(28.dp).clip(CircleShape),
                contentScale = ContentScale.Crop)
            Spacer(Modifier.width(6.dp))
        }

        if (isMe) {
            // 내 메시지: 타임스탬프 왼쪽
            Text(timestamp, color = TextTertiary, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.Bottom).padding(end = 4.dp, bottom = 2.dp))
        }

        // 말풍선
        Text(
            text,
            color    = if (isMe) AccentYellowText else TextPrimary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(
                    if (isMe) AccentYellow else BgCard,
                    RoundedCornerShape(
                        topStart    = 16.dp,
                        topEnd      = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd   = if (isMe) 4.dp  else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )

        if (!isMe) {
            // 상대 메시지: 타임스탬프 오른쪽
            Text(timestamp, color = TextTertiary, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.Bottom).padding(start = 4.dp, bottom = 2.dp))
        }
    }
}
