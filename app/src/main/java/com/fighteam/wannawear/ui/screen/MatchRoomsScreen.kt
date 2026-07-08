package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.fighteam.wannawear.data.ConfirmResult
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.data.model.MatchRoom
import com.fighteam.wannawear.data.model.MatchRoomStatus
import com.fighteam.wannawear.data.remote.dto.ReviewStatusResponse
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch

/**
 * MatchRoom(N:M 다중 교환) 목록/액션 화면 — 2026-07-05부터 "매칭" 탭 자체로 승격됨
 * (별도 "매칭룸(베타)" 메뉴는 없앰). v1.0 확정 스펙, 백엔드 배포/E2E검증 완료.
 */
private enum class RoomFilter(val label: String) {
    ALL("전체"), BEFORE_SHIP("배송 전"), SHIPPING("배송중"), COMPLETE("완료"), CANCELLED("취소")
}
private fun MatchRoom.matchesFilter(filter: RoomFilter): Boolean = when (filter) {
    RoomFilter.ALL         -> true
    RoomFilter.BEFORE_SHIP -> status == MatchRoomStatus.SELECTING || status == MatchRoomStatus.MATCHED || status == MatchRoomStatus.CONFIRMED
    RoomFilter.SHIPPING    -> status == MatchRoomStatus.SHIPPING
    RoomFilter.COMPLETE    -> status == MatchRoomStatus.COMPLETE
    RoomFilter.CANCELLED   -> status == MatchRoomStatus.CANCELLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchRoomsScreen(
    onOpenChat: (Int) -> Unit,
    onSelectItems: (Int) -> Unit
) {
    val rooms = AppState.matchRooms
    var isRefreshing by remember { mutableStateOf(false) }
    var addressPromptRoomId by remember { mutableStateOf<Int?>(null) }
    var cancelConfirmRoomId by remember { mutableStateOf<Int?>(null) }
    var modificationRoomId by remember { mutableStateOf<Int?>(null) }
    // ⚠️ 2026-07-07 추가 — 교환 개수(내가 받을 옷 개수 vs 상대가 받을 옷 개수)가 다른 채로 잠그면
    // 한쪽이 더 많이/적게 받는 불공정한 교환이 될 수 있어서, 잠그기 직전에 한 번 더 확인시킨다.
    var lockConfirmRoomId by remember { mutableStateOf<Int?>(null) }
    // ✅ 탭/화면 이동 후 돌아와도 보고 있던 필터 유지 (remember는 백스택 복귀 시 초기화됨)
    var selectedFilter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AppState.refreshMatchRooms() }

    val filteredRooms = rooms.filter { it.matchesFilter(selectedFilter) }

    addressPromptRoomId?.let { roomId ->
        AddressPromptDialog(
            onDismiss = { addressPromptRoomId = null },
            onSubmit  = { address1, recipient ->
                AppState.addAddress(address1 = address1, recipient = recipient.ifBlank { null }) { success ->
                    addressPromptRoomId = null
                    if (success) AppState.confirmMatchRoom(roomId)
                }
            }
        )
    }

    cancelConfirmRoomId?.let { roomId ->
        AlertDialog(
            onDismissRequest = { cancelConfirmRoomId = null },
            title = { Text("교환을 취소할까요?", fontWeight = FontWeight.Bold) },
            text  = {
                // ⚠️ 라이브 검증(2026-07-08): 방을 취소하면 상대가 이 옷들에 눌렀던 좋아요 자체가
                // 서버에서 삭제되는 것으로 보임(백엔드 버그, 문서 기록함) — 취소 전에 미리 알려준다.
                Text("이 방의 교환이 즉시 취소돼요. 되돌릴 수 없고, 상대가 눌렀던 좋아요도 함께 사라질 수 있어요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    AppState.cancelMatchRoom(roomId)
                    cancelConfirmRoomId = null
                }) { Text("취소하기", color = PassColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { cancelConfirmRoomId = null }) { Text("닫기") } }
        )
    }

    modificationRoomId?.let { roomId ->
        val room = rooms.firstOrNull { it.id == roomId }
        if (room != null) {
            ModificationRequestDialog(
                room = room,
                onDismiss = { modificationRoomId = null },
                onSubmit = { itemIds ->
                    AppState.requestMatchRoomModification(roomId, itemIds)
                    modificationRoomId = null
                }
            )
        }
    }

    lockConfirmRoomId?.let { roomId ->
        val room = rooms.firstOrNull { it.id == roomId }
        if (room != null) {
            val myCount = room.myWantList.size
            val theirCount = room.theirWantList.size
            AlertDialog(
                onDismissRequest = { lockConfirmRoomId = null },
                title = { Text("교환 개수가 서로 달라요", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        if (myCount > theirCount)
                            "당신이 상대보다 많은(${myCount}개) 옷을 선택했어요. ${room.partner.name}님은 ${theirCount}개를 선택했어요. 이대로 선택을 잠글까요?"
                        else
                            "${room.partner.name}님이 당신보다 많은(${theirCount}개) 옷을 선택했어요. 당신은 ${myCount}개를 선택했어요. 이대로 선택을 잠글까요?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        AppState.lockMatchRoomSelection(roomId)
                        lockConfirmRoomId = null
                    }) { Text("그대로 진행", color = AccentYellow, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { lockConfirmRoomId = null }) { Text("다시 선택하기", color = TextTertiary) }
                }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text("매칭", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("총 ${rooms.size}건", color = TextSecondary, fontSize = 10.sp)
        }

        // 상태별 필터 탭
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(BgCard, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            RoomFilter.values().forEach { filter ->
                val selected = selectedFilter == filter
                Button(
                    onClick = { selectedFilter = filter },
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AccentYellow else Color.Transparent,
                        contentColor   = if (selected) AccentYellowText else TextSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    Text(filter.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { AppState.loadMatchRooms() }
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredRooms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📭", fontSize = 32.sp)
                        Text(
                            if (selectedFilter == RoomFilter.ALL) "아직 매칭이 없어요" else "해당하는 매칭이 없어요",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRooms, key = { it.id }) { room ->
                        MatchRoomCard(
                            room = room,
                            onOpenChat        = { onOpenChat(room.id) },
                            onSelectItems     = { onSelectItems(room.id) },
                            onLockSelection   = {
                                // 이미 상대가 뭔가 골라둔 상태에서 개수가 다르면 잠그기 전에 한 번 더 확인
                                if (room.theirWantList.isNotEmpty() && room.myWantList.size != room.theirWantList.size) {
                                    lockConfirmRoomId = room.id
                                } else {
                                    AppState.lockMatchRoomSelection(room.id)
                                }
                            },
                            onConfirm         = {
                                AppState.confirmMatchRoom(room.id) { result ->
                                    if (result is ConfirmResult.NeedsAddress) addressPromptRoomId = room.id
                                }
                            },
                            onShip            = { AppState.shipMatchRoom(room.id) },
                            onComplete        = { AppState.completeMatchRoom(room.id) },
                            onRequestCancel   = { cancelConfirmRoomId = room.id },
                            onRequestModification = { modificationRoomId = room.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRoomCard(
    room: MatchRoom,
    onOpenChat: () -> Unit,
    onSelectItems: () -> Unit,
    onLockSelection: () -> Unit,
    onConfirm: () -> Unit,
    onShip: () -> Unit,
    onComplete: () -> Unit,
    onRequestCancel: () -> Unit,
    onRequestModification: () -> Unit
) {
    val (statusColor, statusBg) = when (room.status) {
        MatchRoomStatus.SELECTING -> Pair(StatusPending, StatusPending.copy(alpha = 0.12f))
        MatchRoomStatus.MATCHED   -> Pair(AccentYellow, AccentYellow.copy(alpha = 0.12f))
        MatchRoomStatus.CONFIRMED, MatchRoomStatus.SHIPPING -> Pair(StatusShipping, StatusShipping.copy(alpha = 0.12f))
        MatchRoomStatus.COMPLETE  -> Pair(StatusComplete, StatusComplete.copy(alpha = 0.12f))
        MatchRoomStatus.CANCELLED -> Pair(TextTertiary, TextTertiary.copy(alpha = 0.12f))
    }
    // ⚠️ 라이브 검증(2026-07-08): 첫 SELECTING(아직 한 번도 안 잠근 방)에서 request-modification을
    // 호출하면 서버가 MODIFICATION_NOT_ALLOWED로 거절함. 하지만 상대가 수정요청을 보내서 방이
    // MATCHED/CONFIRMED에서 SELECTING으로 되돌아온 경우(modificationRequestedByThem=true)엔,
    // 나도 다른 구성으로 맞받아 제안할 수 있어야 하니 이 경우엔 버튼을 계속 보여준다.
    val canRequestModification = room.status == MatchRoomStatus.MATCHED || room.status == MatchRoomStatus.CONFIRMED ||
        (room.status == MatchRoomStatus.SELECTING && room.modificationRequestedByThem)
    val canCancel = room.status != MatchRoomStatus.COMPLETE && room.status != MatchRoomStatus.CANCELLED

    Column(
        Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(16.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(room.partner.avatar, null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(room.partner.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    // ✅ 상대 평점(매너온도)을 카드에서 바로 볼 수 있게 (서버 mannerScore가 있을 때만)
                    room.partner.mannerScore?.let { score ->
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(String.format("%.1f", score), color = AccentYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(room.date, color = TextSecondary, fontSize = 10.sp)
            }
            Text(
                room.status.label, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(statusBg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(room.status.description, color = TextSecondary, fontSize = 11.sp)

        if (room.modificationRequestedByThem) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = PassColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("${room.partner.name}님이 교환 수정을 요청했어요", color = PassColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (room.modificationRequestedByMe) {
            Spacer(Modifier.height(6.dp))
            Text("수정 요청을 보냈어요 · 상대방 응답을 기다리는 중", color = StatusPending, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))

        // 내가 받을 아이템들 / 상대가 받을 아이템들 — N개일 수 있어서 가로 스크롤 행으로
        WantListRow(title = "내가 받을 옷", items = room.myWantList)
        Spacer(Modifier.height(8.dp))
        WantListRow(title = "상대가 받을 옷", items = room.theirWantList)

        // 개수가 다르면 잠그기 전부터 눈에 띄게 알려준다 (요청사항: 더 많이/적게 받는 쪽에 안내)
        if (room.status == MatchRoomStatus.SELECTING && room.theirWantList.isNotEmpty() &&
            room.myWantList.size != room.theirWantList.size
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().background(StatusPending.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = StatusPending, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (room.myWantList.size > room.theirWantList.size)
                        "내가 상대보다 옷을 더 많이(${room.myWantList.size}개) 골랐어요"
                    else
                        "상대가 나보다 옷을 더 많이(${room.theirWantList.size}개) 골랐어요",
                    color = StatusPending, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        when (room.status) {
            MatchRoomStatus.SELECTING -> {
                Button(
                    onClick = onSelectItems,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText)
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("아이템 선택하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onLockSelection,
                    enabled = !room.myLockedSelection && room.myWantList.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow)
                ) {
                    Text(
                        if (room.myLockedSelection) "선택 잠금 완료 · 상대방 대기 중" else "선택 완료(잠금)",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            MatchRoomStatus.MATCHED -> {
                Button(
                    onClick = onConfirm,
                    enabled = !room.myConfirmed,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow, contentColor = AccentYellowText,
                        disabledContainerColor = BgCardDark, disabledContentColor = TextTertiary
                    )
                ) {
                    Text(if (room.myConfirmed) "상대방 확인 대기 중" else "배송지 확인하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
            MatchRoomStatus.CONFIRMED -> {
                Button(
                    onClick = onShip,
                    enabled = !room.myShipped,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusShipping.copy(alpha = 0.15f), contentColor = StatusShipping,
                        disabledContainerColor = BgCardDark, disabledContentColor = TextTertiary
                    )
                ) {
                    Text(if (room.myShipped) "상대방 발송 대기 중" else "발송 완료(한 박스로)", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
            MatchRoomStatus.SHIPPING -> {
                Button(
                    onClick = onComplete,
                    enabled = !room.myReceived,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusComplete.copy(alpha = 0.15f), contentColor = StatusComplete,
                        disabledContainerColor = BgCardDark, disabledContentColor = TextTertiary
                    )
                ) {
                    Text(if (room.myReceived) "상대방 수령 대기 중" else "수령 확인", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                if (room.myReceived) {
                    Spacer(Modifier.height(8.dp))
                    RoomReviewPrompt(room)
                }
            }
            MatchRoomStatus.COMPLETE -> {
                Row(
                    Modifier.fillMaxWidth().background(StatusComplete.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusComplete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("교환이 완료됐어요 🎉", color = StatusComplete, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                RoomReviewPrompt(room)
            }
            MatchRoomStatus.CANCELLED -> {
                Row(
                    Modifier.fillMaxWidth().background(TextTertiary.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("취소된 매칭룸이에요", color = TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (room.status != MatchRoomStatus.CANCELLED) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("채팅하기", fontSize = 12.sp)
                }
                if (canRequestModification) {
                    OutlinedButton(
                        onClick = onRequestModification,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusShipping)
                    ) {
                        Text("교환 수정 요청", fontSize = 12.sp)
                    }
                }
                if (canCancel) {
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PassColor)
                    ) {
                        Text("취소하기", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WantListRow(title: String, items: List<ClothingItem>) {
    Column {
        Text("$title (${items.size})", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (items.isEmpty()) {
            Text("아직 고른 옷이 없어요", color = TextTertiary, fontSize = 11.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.id }) { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            item.image, null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(item.name, color = TextSecondary, fontSize = 8.sp, maxLines = 1,
                            modifier = Modifier.width(56.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 평점(리뷰) — 완료된 방에만 노출, 수령확인만 끝났으면 조기 선택 가능 (코멘트 없이 별점 1~5)
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun RoomReviewPrompt(room: MatchRoom) {
    val roomId = room.id
    val partnerName = room.partner.name
    val isComplete = room.status == MatchRoomStatus.COMPLETE
    val pendingScore = AppState.pendingReviewScores[roomId]

    var status by remember(roomId, isComplete) { mutableStateOf<ReviewStatusResponse?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(roomId, isComplete) {
        if (isComplete) status = AppState.getReviewStatus(roomId)
    }

    if (isComplete) {
        val s = status ?: return
        if (s.myReviewSubmitted) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("내가 남긴 평점: ", color = TextTertiary, fontSize = 11.sp)
                repeat(5) { i ->
                    Icon(
                        if (i < (s.myScoreGiven ?: 0)) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null, tint = AccentYellow, modifier = Modifier.size(13.dp)
                    )
                }
            }
            return
        }
        if (!s.canReview) return

        if (showDialog) {
            RoomReviewDialog(
                partnerName = partnerName,
                isSubmitting = isSubmitting,
                onDismiss = { showDialog = false },
                onSubmit = { score ->
                    isSubmitting = true
                    AppState.submitReview(roomId, score) { success ->
                        isSubmitting = false
                        if (success) {
                            showDialog = false
                            status = s.copy(myReviewSubmitted = true, myScoreGiven = score)
                        }
                    }
                }
            )
        }

        OutlinedButton(
            onClick  = { showDialog = true },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow)
        ) {
            Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("${partnerName}님 평점 남기기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    // ── 아직 COMPLETE 전(SHIPPING + 내 수령확인만 끝남) — 미리 선택해두기 ──────────
    if (pendingScore != null) {
        Row(
            Modifier.fillMaxWidth()
                .background(AccentYellow.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { i ->
                Icon(
                    if (i < pendingScore) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null, tint = AccentYellow, modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("미리 선택해뒀어요 · 상대방 수령확인되면 자동 반영", color = TextSecondary, fontSize = 10.sp, maxLines = 1)
        }
        return
    }

    if (showDialog) {
        RoomReviewDialog(
            partnerName = partnerName,
            isSubmitting = false,
            onDismiss = { showDialog = false },
            onSubmit = { score ->
                AppState.stageOrSubmitReview(roomId, score)
                showDialog = false
            }
        )
    }

    OutlinedButton(
        onClick  = { showDialog = true },
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow)
    ) {
        Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("${partnerName}님 평점 미리 남기기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoomReviewDialog(
    partnerName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var selected by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("${partnerName}님과의 교환은 어떠셨나요?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    (1..5).forEach { i ->
                        IconButton(onClick = { selected = i }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                if (i <= selected) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "${i}점",
                                tint = AccentYellow,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Text(
                    when (selected) {
                        0 -> "별을 눌러 평점을 매겨주세요"
                        1 -> "아쉬웠어요"
                        2 -> "그저 그랬어요"
                        3 -> "무난했어요"
                        4 -> "좋았어요"
                        else -> "최고였어요!"
                    },
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selected > 0) onSubmit(selected) },
                enabled = selected > 0 && !isSubmitting
            ) {
                Text(if (isSubmitting) "제출 중..." else "제출하기", color = AccentYellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("나중에", color = TextTertiary) }
        }
    )
}

@Composable
private fun ModificationRequestDialog(
    room: MatchRoom,
    onDismiss: () -> Unit,
    onSubmit: (List<Int>) -> Unit
) {
    // ✅ 2026-07-08 의미 수정: "교환 수정 요청"은 **상대에게 넘길 내 옷 구성**을 바꿔달라는 요청이다
    //    (예: "내 옷은 이 2개로 교환하고 싶어요"). 예전엔 반대로 "내가 받고 싶은 상대 옷"을 고르게
    //    돼있었음 — 그래서 후보도 상대 아이템(myCandidates)이 아니라 내 아이템(theirCandidates:
    //    상대가 좋아요한 내 옷들)을 보여주고, 현재 상대가 받기로 돼있는 목록(theirWantList)을
    //    기본 체크로 시작한다. 상대는 이 제안을 받으면 재선택 화면에서 제안된 옷들이 미리 체크된
    //    상태로 보게 된다(MatchRoomSelectionScreen 참고).
    var myItemCandidates by remember { mutableStateOf<List<ClothingItem>?>(null) }
    var selectedIds by remember { mutableStateOf(room.theirWantList.map { it.id }.toSet()) }

    LaunchedEffect(room.id) {
        AppState.loadMatchRoomCandidates(room.id) { _, their -> myItemCandidates = their }
    }

    // ⚠️ 서버 candidates는 "새로 고를 수 있는 후보"만 주고, 이미 이 방에서 상대가 받기로 한
    // 내 아이템(room.theirWantList)은 빠져있을 수 있음 — 합쳐서 전부 체크/해제 가능하게 한다.
    val mergedCandidates = myItemCandidates?.let { base -> (room.theirWantList + base).distinctBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교환 수정 요청", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    "${room.partner.name}님에게 넘길 내 옷 구성을 다시 골라서 제안할 수 있어요. 상대방이 확인하면 제안한 옷들이 미리 선택된 상태로 다시 고르게 돼요.",
                    color = TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                val candidates = mergedCandidates
                if (candidates == null) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentYellow, modifier = Modifier.size(20.dp))
                    }
                } else if (candidates.isEmpty()) {
                    Text(
                        "상대가 좋아요한 내 옷이 아직 없어서 제안할 수 있는 옷이 없어요.",
                        color = TextTertiary, fontSize = 12.sp
                    )
                } else {
                    Column(Modifier.heightIn(max = 280.dp)) {
                        candidates.forEach { item ->
                            val checked = item.id in selectedIds
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (checked) selectedIds - item.id else selectedIds + item.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { selectedIds = if (it) selectedIds + item.id else selectedIds - item.id },
                                    colors = CheckboxDefaults.colors(checkedColor = AccentYellow)
                                )
                                AsyncImage(item.image, null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(8.dp))
                                Text(item.name, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selectedIds.toList()) }) {
                Text("수정 요청 보내기", color = AccentYellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = TextTertiary) } }
    )
}
