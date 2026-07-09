package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.fighteam.wannawear.data.model.ChatMessage
import com.fighteam.wannawear.data.model.ChatMessageType
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.data.model.MatchRoomStatus
import com.fighteam.wannawear.data.remote.ChatSocketManager
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch


/**
 * ⚠️ 2026-07-04 수정: 기존 Exchange(MatchItem) 채팅뿐 아니라 신규 MatchRoom 채팅도 같은
 * 화면에서 처리하도록 확장 (match-room-spec.md 기준, roomId가 기존 exchangeId를 계승한다는
 * 마이그레이션 전제하에 동일 채팅 엔드포인트/소켓을 재사용). MatchRoom이 아직 백엔드 미배포라
 * 실제로는 항상 MatchItem 경로만 타게 됨.
 */
@Composable
fun ChatScreen(
    matchId: Int,
    onBack: () -> Unit,
    onNavigateToSelection: (Int) -> Unit = {}
) {
    val match = AppState.matches.firstOrNull { it.id == matchId }
    val room  = if (match == null) AppState.matchRooms.firstOrNull { it.id == matchId } else null
    if (match == null && room == null) { onBack(); return }

    val messages: List<ChatMessage> = match?.messages ?: room!!.messages
    val partnerAvatar = match?.partner?.avatar ?: room!!.partner.avatar
    val partnerName   = match?.partner?.name ?: room!!.partner.name
    val statusText = when {
        match != null -> when (match.status) {
            ExchangeStatus.MATCHED   -> "교환 대기 중"
            ExchangeStatus.CONFIRMED -> "배송 준비 중"
            ExchangeStatus.SHIPPING  -> "배송 중"
            ExchangeStatus.COMPLETE  -> "교환 완료"
            else                     -> ""
        }
        else -> room!!.status.label
    }
    val chatOpen = match?.let { AppState.isChatOpen(it.status) }
        ?: (room!!.status != MatchRoomStatus.COMPLETE && room.status != MatchRoomStatus.CANCELLED)

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 채팅방 진입 시: REST로 히스토리 로드 + 읽음 처리, WebSocket 연결(실시간 수신용, API_SPEC 12절 메인 경로)
    DisposableEffect(matchId) {
        if (match != null) AppState.loadMessages(matchId) else AppState.loadMatchRoomMessages(matchId)
        ChatSocketManager.connect(matchId)
        onDispose { ChatSocketManager.disconnect() }
    }

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
                partnerAvatar, null,
                modifier      = Modifier.size(36.dp).clip(CircleShape),
                contentScale  = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(partnerName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(statusText, color = TextSecondary, fontSize = 10.sp)
            }
            // 교환 아이템 미니 프리뷰 (기존 1:1 Exchange만 — MatchRoom은 N개라 생략)
            if (match != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(match.myItem.image, null,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    Text("⇄", color = TextTertiary, fontSize = 12.sp)
                    AsyncImage(match.theirItem.image, null,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(8.dp))
            }
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
                val isMe = msg.senderId == AppState.myUserId
                if (msg.type == ChatMessageType.EXCHANGE_MODIFICATION_REQUEST) {
                    ModificationRequestBubble(
                        message = msg,
                        isMe    = isMe,
                        onClickCta = { roomId -> onNavigateToSelection(roomId) }
                    )
                } else {
                    ChatBubble(
                        text      = msg.text,
                        timestamp = msg.timestamp,
                        isMe      = isMe,
                        avatar    = if (!isMe) partnerAvatar else null
                    )
                }
            }

            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💬", fontSize = 32.sp)
                            Text("${partnerName}님과 대화를 시작해보세요", color = TextSecondary, fontSize = 13.sp)
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
                            ChatSocketManager.sendMessage(matchId, inputText)
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
                            ChatSocketManager.sendMessage(matchId, inputText)
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

/** ⚠️ 2026-07-04 추가, v0.1 설계 제안(match-room-spec.md §7) — 교환 수정 요청 커스텀 말풍선.
 *  일반 텍스트 대신 카드형 UI로 렌더링하고, CTA 버튼 클릭 시 재선택 화면으로 이동. */
@Composable
private fun ModificationRequestBubble(
    message: ChatMessage,
    isMe: Boolean,
    onClickCta: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 260.dp)
                .background(BgCard, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("교환 수정 요청", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                message.text.ifBlank { "다시 선택해주세요" },
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { message.modificationRoomId?.let(onClickCta) },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(message.modificationCtaLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
