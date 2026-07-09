package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.draw.shadow
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
    // ✅ 요청사항: "전체" 탭은 문자 그대로 전부가 아니라 "완료/취소를 뺀 진행중 전체"를 뜻하게
    //    바꿈 — 완료되거나 취소된 건은 각자 전용 탭에서만 보고, 기본 화면은 신경 써야 할 것만
    //    보이게 한다.
    RoomFilter.ALL         -> status != MatchRoomStatus.COMPLETE && status != MatchRoomStatus.CANCELLED
    RoomFilter.BEFORE_SHIP -> status == MatchRoomStatus.SELECTING || status == MatchRoomStatus.MATCHED || status == MatchRoomStatus.CONFIRMED
    RoomFilter.SHIPPING    -> status == MatchRoomStatus.SHIPPING
    RoomFilter.COMPLETE    -> status == MatchRoomStatus.COMPLETE
    RoomFilter.CANCELLED   -> status == MatchRoomStatus.CANCELLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchRoomsScreen(
    onOpenChat: (Int) -> Unit,
    onSelectItems: (Int) -> Unit,
    onNavigateToShipping: (Int) -> Unit = {}
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
                // ✅ 2026-07-08: 취소해도 서로 좋아요가 남아있으면 자동으로 매칭을 다시 만들어주니
                // (아래 로직) 예전처럼 "좋아요가 사라질 수 있다"고 겁줄 필요는 없어졌음. 다만 방
                // 자체(선택해둔 내역, 채팅 등)는 취소하면 되돌릴 수 없다는 점만 안내.
                Text("이 방의 교환이 즉시 취소돼요. 되돌릴 수 없어요. (서로 좋아요가 남아있는 옷이 있다면 자동으로 새 매칭이 만들어져요)")
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
                onSubmit = { wantItems, offerItems ->
                    if (room.status == MatchRoomStatus.SELECTING) {
                        // ⚠️ 2026-07-09 추가 — 요청사항: 아이템 선택을 확정(잠금)하기 전에도 제안을
                        // 보낼 수 있게 해달라는 요청. 그런데 서버는 SELECTING 상태에서
                        // request-modification 자체를 막아뒀음(라이브 검증 완료 — "이미 선택을
                        // 진행 중이에요"로 거절). 정식 API가 안 열려있는 단계라 채팅 메시지로
                        // 같은 내용(받고 싶은 옷 + 제시하는 옷)을 전달하는 방식으로 우회한다.
                        // 백엔드에 SELECTING 단계도 정식으로 허용해달라고 요청해뒀음.
                        val wantNames = wantItems.joinToString(", ") { it.name }
                        val offerNames = offerItems.joinToString(", ") { it.name }
                        val text = buildString {
                            append("교환 제안을 보내요 🙂")
                            if (wantNames.isNotEmpty()) append("\n받고 싶은 옷: $wantNames")
                            if (offerNames.isNotEmpty()) append("\n제가 드리고 싶은 옷: $offerNames")
                        }
                        AppState.sendMatchRoomMessage(roomId, text)
                    } else {
                        AppState.requestMatchRoomModification(
                            roomId, wantItems.map { it.id }, offerItems.map { it.id }
                        )
                    }
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
        // ⚠️ 2026-07-09 — "총 N건"을 타이틀 아래 별도 줄로 두니 상단이 불필요하게 넓어져서
        // 카드가 한 화면에 다 안 보인다는 피드백. 타이틀과 한 줄에 나란히 배치하고 상단
        // 패딩도 줄여서 아래 컴포넌트들이 전체적으로 위로 올라오게 함.
        Row(
            Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "매칭", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.width(8.dp))
            Text("총 ${rooms.size}건", color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 1.dp))
        }

        // 상태별 필터 탭 — ⚠️ 2026-07-09 디자인 업그레이드: 선택된 탭에도 카드와 같은 계열의
        // 은은한 그림자를 줘서 "떠 있는 세그먼트"처럼 보이게 다듬음(Linear/Stripe 대시보드류
        // 세그먼트 컨트롤 참고). 트랙 자체는 배경과 톤온톤으로 낮춰서 과하지 않게.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(BgCardDark.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            RoomFilter.values().forEach { filter ->
                val selected = selectedFilter == filter
                Button(
                    onClick = { selectedFilter = filter },
                    modifier = Modifier.weight(1f).height(36.dp)
                        .then(
                            if (selected) Modifier.shadow(elevation = 2.dp, shape = RoundedCornerShape(10.dp), ambientColor = CardShadow, spotColor = CardShadow)
                            else Modifier
                        ),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AccentYellow else Color.Transparent,
                        contentColor   = if (selected) AccentYellowText else TextSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    Text(filter.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp, maxLines = 1)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            onShip            = {
                                // ⚠️ 2026-07-09 버그 수정: 여기서 바로 shipMatchRoom을 호출해서
                                // 예전에 있던 주소 확인/배송 안내 화면(ShippingGuideScreen)을 아예
                                // 안 거치고 바로 발송 처리됐음 — 화면으로 이동하도록 되돌림. 실제
                                // 발송 액션은 그 화면의 "배송 완료" 버튼에서 함.
                                onNavigateToShipping(room.id)
                            },
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
    val statusColor = when (room.status) {
        MatchRoomStatus.SELECTING -> StatusPending
        MatchRoomStatus.MATCHED   -> AccentYellow
        MatchRoomStatus.CONFIRMED, MatchRoomStatus.SHIPPING -> StatusShipping
        MatchRoomStatus.COMPLETE  -> StatusComplete
        MatchRoomStatus.CANCELLED -> TextTertiary
    }
    // ⚠️ 2026-07-09 — 요청사항: 아이템 선택 확정 전(SELECTING)에도 교환 제안을 보낼 수 있게
    // 해달라는 요청 반영. 서버가 SELECTING 단계에서 request-modification API 자체를 막아뒀지만
    // (라이브 검증 완료 — "이미 선택을 진행 중이에요"), 채팅으로 같은 내용을 전달하는 방식으로
    // 우회 지원한다(ModificationRequestDialog의 onSubmit에서 분기). SHIPPING 이후는 여전히 불가.
    val canRequestModification = room.status == MatchRoomStatus.SELECTING ||
        room.status == MatchRoomStatus.MATCHED || room.status == MatchRoomStatus.CONFIRMED
    val modificationButtonLabel = if (room.status == MatchRoomStatus.SELECTING) "교환 제안 보내기" else "교환 수정 요청"
    val canCancel = room.status != MatchRoomStatus.COMPLETE && room.status != MatchRoomStatus.CANCELLED

    Column(
        Modifier.fillMaxWidth()
            // ⚠️ 2026-07-09 — 순백에 가까운 배경에서는 그림자가 없으면 카드가 배경과 거의 안
            // 구분돼서 밋밋해 보였음. 은은한 그림자로 입체감을 살림.
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(18.dp), ambientColor = CardShadow, spotColor = CardShadow)
            .background(BgCard, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ⚠️ 2026-07-09 — 아바타/텍스트가 전반적으로 작아서 잘 안 보인다는 피드백 반영,
            // 카드 전체 요소 크기를 한 단계씩 키움.
            AsyncImage(room.partner.avatar, null, modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(room.partner.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    // ✅ 상대 평점(매너온도)을 카드에서 바로 볼 수 있게 (서버 mannerScore가 있을 때만)
                    room.partner.mannerScore?.let { score ->
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(String.format("%.1f", score), color = AccentYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(room.date, color = TextSecondary, fontSize = 11.sp)
            }
            // ⚠️ 2026-07-09 디자인 업그레이드: 진한 배경 배지 대신 점(dot) + 라벨로 절제된
            // 상태 표시(Linear류 상태 인디케이터 참고) — 색은 그대로 상태별 의미 유지, 톤만 정제.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(room.status.label, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(room.status.description, color = TextSecondary, fontSize = 13.sp)

        if (room.modificationRequestedByThem) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = PassColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${room.partner.name}님이 교환 수정을 요청했어요", color = PassColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else if (room.modificationRequestedByMe) {
            Spacer(Modifier.height(6.dp))
            Text("수정 요청을 보냈어요 · 상대방 응답을 기다리는 중", color = StatusPending, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))

        // 내가 받을 아이템들 / 상대가 받을 아이템들 — N개일 수 있어서 가로 스크롤 행으로
        WantListRow(title = "내가 받을 옷", items = room.myWantList)
        Spacer(Modifier.height(6.dp))
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
                Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = StatusPending, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (room.myWantList.size > room.theirWantList.size)
                        "내가 상대보다 옷을 더 많이(${room.myWantList.size}개) 골랐어요"
                    else
                        "상대가 나보다 옷을 더 많이(${room.theirWantList.size}개) 골랐어요",
                    color = StatusPending, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

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
                    // ⚠️ 2026-07-09 UX 수정: 이 버튼은 실제로 "매칭 확정" 액션(confirmMatchRoom)인데
                    // 라벨이 "배송지 확인하기"로 되어 있어서, 사용자가 확정 버튼이 사라지고 갑자기
                    // 배송 관련 버튼이 나온 것처럼 오해하는 문제가 있었음. 주소 등록은 필요할 때
                    // 자동으로 뜨는 팝업(addressPromptRoomId)에서 처리되니, 메인 버튼은 실제 액션의
                    // 의미(매칭 확정)를 분명히 드러내도록 라벨만 변경. 동작은 동일.
                    Text(if (room.myConfirmed) "상대방 확인 대기 중" else "매칭 확정하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                // ⚠️ 2026-07-09 버그 수정: 상대방이 먼저 배송지를 확정하면 서버에 partnerAddress가
                // 이미 준비되는데, 예전엔 나도 확정해서 status가 CONFIRMED가 되기 전까진 주소를 볼
                // 방법이 아예 없었음(실제로 발송하려면 주소를 알아야 하는데도). 상대방이 확정했으면
                // 내가 아직 확정 전이어도 배송 안내 화면(주소만 조회, 발송 액션은 그 화면에서 계속
                // 막힘)에 미리 들어갈 수 있게 한다.
                if (room.theirConfirmed) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onShip,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusShipping)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${room.partner.name}님이 배송지를 등록했어요 · 주소 확인하기", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MatchRoomStatus.CONFIRMED -> {
                Button(
                    onClick = onShip,
                    // ⚠️ 이제 이 버튼은 실제 발송 처리가 아니라 배송 안내/주소 확인 화면으로
                    // 이동하는 버튼이라, 이미 내가 발송해도(myShipped) 주소를 다시 보거나 안내를
                    // 다시 볼 수 있게 항상 눌리게 둔다 — 실제 발송 액션은 그 화면 안에 있음.
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusShipping.copy(alpha = 0.15f), contentColor = StatusShipping
                    )
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (room.myShipped) "배송 안내 다시 보기 (상대방 발송 대기 중)" else "배송 안내 보기 · 발송하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
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
            // ⚠️ 2026-07-09 UI 버그 수정: 버튼 3개가 weight(1f)로 좁게 나뉘는데 Material3 기본
            // contentPadding(좌우 24dp)이 그대로 적용돼서, 아이콘+텍스트가 있는 "채팅하기"가
            // 공간 부족으로 잘렸었음("채팅하 기") 다른 두 버튼도 여백이 과하게/불균등하게 보였음.
            // 좌우 패딩을 좁혀서 셋 다 여유 있게 들어가도록 수정.
            val actionButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = actionButtonPadding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("채팅하기", fontSize = 13.sp, maxLines = 1)
                }
                if (canRequestModification) {
                    OutlinedButton(
                        onClick = onRequestModification,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = actionButtonPadding,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusShipping)
                    ) {
                        Text(modificationButtonLabel, fontSize = 13.sp, maxLines = 1)
                    }
                }
                if (canCancel) {
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = actionButtonPadding,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PassColor)
                    ) {
                        Text("취소하기", fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }

        // ⚠️ 2026-07-09 복구 — "매칭" 탭이 2026-07-05에 MatchesScreen(1:1)에서 이 화면(MatchRoom
        // 기반)으로 승격될 때 신고 기능이 같이 안 옮겨져서 빠져 있었음. 매칭이 실제로 성사된
        // 이후(선택 중 제외)에만, 방 1개당 1회만 가능하도록 MatchesScreen과 동일한 로직으로 복구.
        if (room.status == MatchRoomStatus.MATCHED || room.status == MatchRoomStatus.CONFIRMED ||
            room.status == MatchRoomStatus.SHIPPING || room.status == MatchRoomStatus.COMPLETE
        ) {
            var showReportDialog by remember { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            Text(
                if (AppState.canReport(room.id)) "이 거래에 문제가 있었나요? · 신고하기" else "신고가 접수됐어요",
                color = TextTertiary, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
                    .then(
                        if (AppState.canReport(room.id))
                            Modifier.clickable { showReportDialog = true }
                        else Modifier
                    ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (showReportDialog) {
                RoomReportDialog(
                    partnerName = room.partner.name,
                    onDismiss = { showReportDialog = false },
                    onSubmit = { reason, detail ->
                        AppState.reportExchange(room.id, reason, detail)
                        showReportDialog = false
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 신고 — MatchesScreen(1:1)에 있던 것과 동일한 표준 남용 방지책(사유 선택 필수 + 최종 확인
// 단계)을 매칭룸에도 복구. AppState.reportExchange/canReport는 matchId(Int)만 받으므로
// roomId를 그대로 넘기면 동일하게 동작한다.
// ─────────────────────────────────────────────────────────────────────

private enum class RoomReportReason(val code: String, val label: String) {
    NO_SHIP("NO_SHIP", "발송을 안 하거나 연락이 끊겼어요"),
    FAKE_OR_DAMAGED("FAKE_OR_DAMAGED", "설명과 다르거나 파손된 상품을 보냈어요"),
    RUDE_BEHAVIOR("RUDE_BEHAVIOR", "욕설·비매너 행동을 했어요"),
    SCAM_SUSPECTED("SCAM_SUSPECTED", "사기가 의심돼요"),
    OTHER("OTHER", "기타")
}

@Composable
private fun RoomReportDialog(
    partnerName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String?) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: 사유 선택, 2: 최종 확인
    var selectedReason by remember { mutableStateOf<RoomReportReason?>(null) }
    var detail by remember { mutableStateOf("") }

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${partnerName}님을 신고할까요?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("사유를 선택해주세요", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    RoomReportReason.values().forEach { reason ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick  = { selectedReason = reason },
                                colors   = RadioButtonDefaults.colors(selectedColor = AccentYellow)
                            )
                            Text(reason.label, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { if (it.length <= 300) detail = it },
                        placeholder = { Text("상황을 자세히 알려주세요 (선택)", fontSize = 12.sp) },
                        minLines = 2, maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (selectedReason != null) step = 2 },
                    enabled = selectedReason != null
                ) { Text("다음", color = AccentYellow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("취소", color = TextTertiary) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("정말 신고할까요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "허위 신고는 이용 제재 대상이 될 수 있어요. 신고 후에는 취소할 수 없어요.",
                    color = TextSecondary, fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedReason?.let { onSubmit(it.code, detail) }
                }) { Text("신고하기", color = PassColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { step = 1 }) { Text("뒤로", color = TextTertiary) }
            }
        )
    }
}

@Composable
private fun WantListRow(title: String, items: List<ClothingItem>) {
    // ⚠️ 2026-07-09 — 옷 썸네일/이름이 너무 작아 잘 안 보인다는 피드백으로 56dp → 78dp,
    // 이름 폰트 8sp → 10sp로 확대.
    Column {
        Text("$title (${items.size})", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text("아직 고른 옷이 없어요", color = TextTertiary, fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            item.image, null,
                            modifier = Modifier.size(78.dp).clip(RoundedCornerShape(10.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(item.name, color = TextSecondary, fontSize = 10.sp, maxLines = 1,
                            modifier = Modifier.width(78.dp))
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
    onSubmit: (wantItems: List<ClothingItem>, offerItems: List<ClothingItem>) -> Unit
) {
    // ⚠️ 2026-07-08 백엔드 최종 스펙 확정(backend-report-response-2026-07-08.md §12):
    // proposedItemIds(내가 받고 싶은 상대 아이템)와 suggestedOfferItemIds(내가 제시하고 싶은
    // 내 아이템, 본인 소유만 검증)를 request-modification 요청 바디에 같이 보내는 정식 필드로
    // 지원하게 됨 — 예전엔 후자를 채팅 텍스트로 우회 전달했는데 이제 정식 API로 전송한다.
    // 상대는 GET /match-rooms/{id}의 modificationSuggestedOfferItemIds로 조회해서 재선택
    // 화면에서 "상대가 권유한 아이템" 배지로 보게 된다(MatchRoomSelectionScreen 참고).
    //
    // ⚠️ 2026-07-09 추가 — SELECTING 단계(아직 선택 잠그기 전)에서는 서버가 이 API 자체를
    // 막아뒀어서(라이브 검증됨), 이 화면은 그대로 두고 호출부(MatchRoomsScreen)에서 SELECTING인
    // 경우 정식 API 대신 채팅 메시지로 우회 전송한다 — 그래서 이름까지 알아야 해 List<ClothingItem>
    // 을 그대로 넘긴다.
    val isPreLock = room.status == MatchRoomStatus.SELECTING
    var myItemCandidates by remember { mutableStateOf<List<ClothingItem>?>(null) }
    var theirItemCandidates by remember { mutableStateOf<List<ClothingItem>?>(null) }
    var wantSelectedIds by remember { mutableStateOf(room.myWantList.map { it.id }.toSet()) }
    var offerSelectedIds by remember { mutableStateOf(room.theirWantList.map { it.id }.toSet()) }

    LaunchedEffect(room.id) {
        AppState.loadMatchRoomCandidates(room.id) { my, their ->
            myItemCandidates = my
            theirItemCandidates = their
        }
    }

    // ⚠️ candidates는 "새로 고를 수 있는 후보"만 주고, 이미 이 방에 선택된 아이템은 빠져있을 수
    // 있음 — 합쳐서 전부 체크/해제 가능하게 한다.
    val mergedWantCandidates = myItemCandidates?.let { base -> (room.myWantList + base).distinctBy { it.id } }
    val mergedOfferCandidates = theirItemCandidates?.let { base -> (room.theirWantList + base).distinctBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPreLock) "교환 제안 보내기" else "교환 수정 요청", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Text(
                    if (isPreLock)
                        "아직 선택을 잠그기 전이라, 원하는 구성을 채팅 메시지로 먼저 제안해볼 수 있어요. 실제 선택은 서로 \"아이템 선택하기\"에서 직접 하는 거예요."
                        else "받고 싶은 옷과 내가 제시할 옷을 둘 다 고를 수 있어요. 제시하는 쪽은 실제로 정해지는 건 아니고, ${room.partner.name}님이 다시 고를 때 참고할 제안이에요.",
                    color = TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                Text("${room.partner.name}님에게서 받고 싶은 옷", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                SelectableCandidateGrid(
                    candidates = mergedWantCandidates,
                    selectedIds = wantSelectedIds,
                    onToggle = { id, checked -> wantSelectedIds = if (checked) wantSelectedIds + id else wantSelectedIds - id },
                    emptyText = "아직 좋아요한 상대 옷이 없어서 고를 수 있는 옷이 없어요.",
                    maxHeight = 160.dp
                )

                Spacer(Modifier.height(16.dp))
                Text("내가 교환으로 제시하는 옷", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isPreLock)
                        "체크한 옷은 채팅 메시지에 같이 담겨서 전달돼요."
                        else "체크한 옷은 강제로 정해지는 게 아니라, ${room.partner.name}님이 다시 고를 때 \"상대방이 권유한 거래 옷\"으로 표시돼요.",
                    color = TextTertiary, fontSize = 10.sp
                )
                Spacer(Modifier.height(6.dp))
                SelectableCandidateGrid(
                    candidates = mergedOfferCandidates,
                    selectedIds = offerSelectedIds,
                    onToggle = { id, checked -> offerSelectedIds = if (checked) offerSelectedIds + id else offerSelectedIds - id },
                    emptyText = "상대가 좋아요한 내 옷이 아직 없어서 제안할 옷이 없어요.",
                    maxHeight = 160.dp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val availableWantIds = (mergedWantCandidates ?: emptyList()).map { it.id }.toSet()
                val wantItems = (mergedWantCandidates ?: emptyList()).filter { it.id in wantSelectedIds && it.id in availableWantIds }
                val offerItems = (mergedOfferCandidates ?: emptyList()).filter { it.id in offerSelectedIds }
                onSubmit(wantItems, offerItems)
            }) {
                Text(if (isPreLock) "제안 보내기" else "수정 요청 보내기", color = AccentYellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = TextTertiary) } }
    )
}

// ✅ 2026-07-08: 체크박스 대신 다른 서비스들(쇼핑앱 옵션 선택 등)에서 흔한 "카드 테두리 강조 +
// 체크 배지" 방식으로 통일 — MatchRoomSelectionScreen의 SelectableCandidateCard와 같은 톤.
@Composable
private fun SelectableCandidateGrid(
    candidates: List<ClothingItem>?,
    selectedIds: Set<Int>,
    onToggle: (Int, Boolean) -> Unit,
    emptyText: String,
    maxHeight: androidx.compose.ui.unit.Dp
) {
    when {
        candidates == null -> Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentYellow, modifier = Modifier.size(18.dp))
        }
        candidates.isEmpty() -> Text(emptyText, color = TextTertiary, fontSize = 12.sp)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = maxHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(candidates, key = { it.id }) { item ->
                val selected = item.id in selectedIds
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { onToggle(item.id, !selected) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .border(
                                if (selected) 2.5.dp else 0.dp,
                                if (selected) AccentYellow else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        AsyncImage(item.image, null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        if (selected) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp)
                                    .background(AccentYellow, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "선택됨", tint = AccentYellowText, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(item.name, color = TextPrimary, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}
