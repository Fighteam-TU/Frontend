package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.ConfirmResult
import com.fighteam.wannawear.data.MatchResult
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.data.model.MatchItem
import com.fighteam.wannawear.data.model.User
import com.fighteam.wannawear.data.remote.dto.ReviewStatusResponse
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch

// 교환신청 전(매칭됨) / 진행중(배송) / 완료 / 취소 느낌으로 묶어서 볼 수 있게 하는 필터
private enum class MatchFilter(val label: String) {
    ALL("전체"),
    MATCHED("교환신청 전"),
    IN_PROGRESS("배송중"),
    COMPLETE("교환 완료"),
    CANCELLED("취소")
}

private fun MatchItem.matchesFilter(filter: MatchFilter): Boolean = when (filter) {
    MatchFilter.ALL         -> true
    MatchFilter.MATCHED     -> status == ExchangeStatus.MATCHED
    MatchFilter.IN_PROGRESS -> status == ExchangeStatus.CONFIRMED || status == ExchangeStatus.SHIPPING
    MatchFilter.COMPLETE    -> status == ExchangeStatus.COMPLETE
    MatchFilter.CANCELLED   -> status == ExchangeStatus.CANCELLED
}

// "받은 관심"처럼 같은 내 옷으로 매칭이 여러 건이면 카드 하나로 묶어서 보여주기 위한 타입
private sealed class MatchListEntry {
    data class Single(val match: MatchItem) : MatchListEntry()
    data class Duplicate(
        val myItem: ClothingItem,
        val conflicting: List<MatchItem>,
        val alreadyCompletedElsewhere: Boolean
    ) : MatchListEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onOpenChat:          (Int) -> Unit = {},
    onOpenShippingGuide: (Int) -> Unit = {}
) {
    val matches = AppState.matches
    // ⚠️ confirm 하려면 본인 기본 배송주소가 있어야 함(API_SPEC 6절) — 없으면 이 다이얼로그로 유도
    var addressPromptMatchId by remember { mutableStateOf<Int?>(null) }
    var cancelConfirmMatchId by remember { mutableStateOf<Int?>(null) }
    var selectedFilter by remember { mutableStateOf(MatchFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }
    var matchedResult by remember { mutableStateOf<MatchItem?>(null) }
    val scope = rememberCoroutineScope()

    // ⚠️ 매칭/교환 상태는 실시간 소켓이 없어서, 이 탭에 들어올 때마다 조용히 한 번 새로고침한다
    // (당겨서 새로고침은 아래 PullToRefreshBox로 별도 제공).
    LaunchedEffect(Unit) { AppState.refreshExchanges() }

    // ⚠️ 버그 수정: matches는 SnapshotStateList라 항목이 바뀌어도 "참조" 자체는 안 바뀐다.
    //    remember(matches, ...)로 감싸면 키가 안 바뀐 걸로 판단해서 확정/취소 후에도
    //    재계산이 안 되고, 필터 탭을 건드리는 등 "우연히" recompose 될 때까지 화면이 안 바뀌는
    //    버그가 있었음. remember 없이 매번 계산 — 목록 크기가 작아서 성능 문제 없음.
    val filteredMatches = matches.filter { it.matchesFilter(selectedFilter) }

    // ⚠️ 백엔드 버그 방어: 같은 내 옷(myItem)으로 매칭이 동시에 여러 건 성사될 수 있는 버그가
    //    서버에 있음(실측 확인 — 상대 둘이 먼저 내 옷에 좋아요 걸어두면, 내가 둘 다에게 좋아요
    //    보낼 때 둘 다 매칭이 생겨버림). 서버가 하나를 진행해도 나머지를 자동으로 안 막아주기
    //    때문에, 클라이언트에서 감지해서 "받은 관심"처럼 카드 하나로 묶어 보여주고 하나만
    //    골라 유지, 나머지는 자동 취소하도록 유도한다.
    //    ⚠️ 추가 케이스(실사용 스크린샷으로 확인됨): 이미 COMPLETE로 끝난 교환이 있는데도
    //    같은 아이템으로 걸린 다른 MATCHED/CONFIRMED 매칭이 안 사라지는 경우도 있음 — 이때는
    //    "여러 개 중 하나 고르기"가 아니라 "이미 끝난 옷이니 남은 매칭 전부 정리"로 다뤄야 한다.
    val activeStatuses = setOf(ExchangeStatus.MATCHED, ExchangeStatus.CONFIRMED, ExchangeStatus.SHIPPING)
    val completedItemIds = matches.filter { it.status == ExchangeStatus.COMPLETE }.map { it.myItem.id }.toSet()
    val duplicateGroups = matches
        .filter { it.status in activeStatuses }
        .groupBy { it.myItem.id }
        .filter { (itemId, list) -> list.size > 1 || itemId in completedItemIds }
        .map { (itemId, list) -> Triple(list.first().myItem, list, itemId in completedItemIds) }
    val duplicateMatchIds = duplicateGroups.flatMap { it.second }.map { it.id }.toSet()
    var resolvingGroupItemId by remember { mutableStateOf<Int?>(null) }

    // "전체" 필터에서만 중복 매칭을 그룹 카드로 합쳐 보여줌 — 특정 상태 필터에서는 개별 카드 그대로.
    val displayEntries: List<MatchListEntry> = if (selectedFilter == MatchFilter.ALL) {
        val dupEntries = duplicateGroups.map { (item, list, completedElsewhere) ->
            MatchListEntry.Duplicate(item, list, completedElsewhere)
        }
        val singleEntries = filteredMatches.filter { it.id !in duplicateMatchIds }.map { MatchListEntry.Single(it) }
        dupEntries + singleEntries
    } else {
        filteredMatches.map { MatchListEntry.Single(it) }
    }

    // ⚠️ 취소 후 "같은 상대의 다른 좋아요한 옷으로 다시 시도할래요?" 제안 팝업에 쓰는 상태
    var retrySuggestion by remember { mutableStateOf<Pair<User, List<ClothingItem>>?>(null) }

    matchedResult?.let { match ->
        RealMatchPopup(match = match, onClose = { matchedResult = null })
        return
    }

    addressPromptMatchId?.let { matchId ->
        AddressPromptDialog(
            onDismiss = { addressPromptMatchId = null },
            onSubmit  = { address1, recipient ->
                AppState.addAddress(address1 = address1, recipient = recipient.ifBlank { null }) { success ->
                    addressPromptMatchId = null
                    if (success) AppState.confirmExchange(matchId)
                }
            }
        )
    }

    cancelConfirmMatchId?.let { matchId ->
        val cancellingMatch = matches.firstOrNull { it.id == matchId }
        AlertDialog(
            onDismissRequest = { cancelConfirmMatchId = null },
            title = { Text("교환을 취소할까요?", fontWeight = FontWeight.Bold) },
            text  = { Text("매칭이 즉시 취소되고 상대방 동의는 필요 없어요. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    val partner = cancellingMatch?.partner
                    val cancelledItemId = cancellingMatch?.theirItem?.id
                    AppState.cancelExchange(matchId) { success ->
                        // 취소한 상대의 다른 옷 중에 이미 좋아요 눌러둔 게 있으면(그리고 그게
                        // 아직 다른 교환에 안 걸려있으면) 다시 시도할지 물어본다.
                        if (success && partner != null) {
                            val alternatives = AppState.sentLikes
                                .map { it.item }
                                .filter { it.user.id == partner.id && it.id != cancelledItemId }
                                .filterNot { AppState.isTheirItemInExchange(it.id) || AppState.isTheirItemCompleted(it.id) }
                            if (alternatives.isNotEmpty()) {
                                retrySuggestion = partner to alternatives
                            }
                        }
                    }
                    cancelConfirmMatchId = null
                }) { Text("취소하기", color = PassColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { cancelConfirmMatchId = null }) { Text("닫기") }
            }
        )
    }

    retrySuggestion?.let { (partner, items) ->
        RetryExchangeDialog(
            partnerName = partner.name,
            items       = items,
            onDismiss   = { retrySuggestion = null },
            onSelect    = { item ->
                retrySuggestion = null
                AppState.likeItem(item) { result ->
                    if (result is MatchResult.Matched) matchedResult = result.match
                }
            }
        )
    }

    resolvingGroupItemId?.let { itemId ->
        val group = duplicateGroups.firstOrNull { it.first.id == itemId }
        if (group != null) {
            DuplicateResolveDialog(
                myItem                    = group.first,
                conflicting               = group.second,
                alreadyCompletedElsewhere = group.third,
                onDismiss                 = { resolvingGroupItemId = null }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text("교환 내역", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("총 ${matches.size}건", color = TextSecondary, fontSize = 10.sp)
        }

        // 상태별 필터 탭
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(BgCard, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            MatchFilter.values().forEach { filter ->
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
                    Text(
                        filter.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { AppState.loadExchanges() }
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📭", fontSize = 32.sp)
                        Text(
                            if (selectedFilter == MatchFilter.ALL) "아직 교환 내역이 없어요" else "해당하는 교환 내역이 없어요",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        displayEntries,
                        key = { entry ->
                            when (entry) {
                                is MatchListEntry.Single    -> "s-${entry.match.id}"
                                is MatchListEntry.Duplicate -> "d-${entry.myItem.id}"
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is MatchListEntry.Single -> MatchCard(
                                match               = entry.match,
                                onOpenChat          = { onOpenChat(entry.match.id) },
                                onOpenShippingGuide = { onOpenShippingGuide(entry.match.id) },
                                onNeedsAddress      = { addressPromptMatchId = entry.match.id },
                                onRequestCancel     = { cancelConfirmMatchId = entry.match.id }
                            )
                            is MatchListEntry.Duplicate -> GroupedDuplicateMatchCard(
                                myItem                    = entry.myItem,
                                conflicting               = entry.conflicting,
                                alreadyCompletedElsewhere = entry.alreadyCompletedElsewhere,
                                onOpenResolve             = { resolvingGroupItemId = entry.myItem.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressPromptDialog(
    onDismiss: () -> Unit,
    onSubmit: (address1: String, recipient: String) -> Unit
) {
    var address1 by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("배송 주소가 필요해요", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("교환을 확정하려면 기본 배송지가 1개 있어야 해요. 상대방이 confirm 하면 이 주소가 상대방에게 보여요.",
                    color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = address1,
                    onValueChange = { address1 = it },
                    placeholder = { Text("주소 *  ex) 서울특별시 중구 세종대로 110") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    placeholder = { Text("받는 사람 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (address1.isNotBlank()) onSubmit(address1, recipient) },
                enabled = address1.isNotBlank()
            ) { Text("저장하고 확정", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────
// 중복 매칭 정리 — 같은 내 옷으로 매칭이 여러 건 성사된 경우(백엔드 버그) 방어 UI.
// "받은 관심" 카드와 같은 톤: 아이템 하나 + 관심 보낸(매칭된) 사람들 아바타 스택.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun GroupedDuplicateMatchCard(
    myItem: ClothingItem,
    conflicting: List<MatchItem>,
    alreadyCompletedElsewhere: Boolean,
    onOpenResolve: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .clickable { onOpenResolve() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(10.dp))) {
            AsyncImage(myItem.image, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                if (alreadyCompletedElsewhere) "완료됨" else "중복",
                color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .background(PassColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    conflicting.take(4).forEach { m ->
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(BgCard)
                                .border(1.5.dp, BgCard, CircleShape)
                        ) {
                            AsyncImage(m.partner.avatar, null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        }
                    }
                    if (conflicting.size > 4) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(BgCardDark).border(1.5.dp, BgCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${conflicting.size - 4}", color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text("${conflicting.size}건 매칭 ⚠️", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(myItem.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            Text(
                if (alreadyCompletedElsewhere) "이미 다른 곳으로 교환 완료됐어요 · 정리해주세요"
                else "한 곳에만 보낼 수 있어요 · 하나만 골라주세요",
                color = PassColor, fontSize = 10.sp
            )
        }

        Button(
            onClick = onOpenResolve,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(if (alreadyCompletedElsewhere) "정리하기" else "고르기", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DuplicateResolveDialog(
    myItem: ClothingItem,
    conflicting: List<MatchItem>,
    alreadyCompletedElsewhere: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .background(BgCard, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(myItem.image, null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("\"${myItem.name}\" 매칭 ${conflicting.size}건", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (alreadyCompletedElsewhere) "이미 다른 곳으로 교환 완료된 옷이에요 — 남은 매칭을 정리해주세요"
                        else "유지할 교환을 고르면 나머지는 자동으로 취소돼요",
                        color = if (alreadyCompletedElsewhere) PassColor else TextSecondary, fontSize = 10.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))

            if (alreadyCompletedElsewhere) {
                Button(
                    onClick = {
                        conflicting.forEach { AppState.cancelExchange(it.id) }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PassColor, contentColor = Color.White)
                ) {
                    Text("전부 취소하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conflicting.forEach { match ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(BgCardDark, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(match.theirItem.image, null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(match.partner.avatar, null, modifier = Modifier.size(16.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(4.dp))
                                Text(match.partner.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(match.theirItem.name, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
                            Text(match.status.label, color = TextTertiary, fontSize = 9.sp)
                        }
                        if (alreadyCompletedElsewhere) {
                            Button(
                                onClick = { AppState.cancelExchange(match.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PassColor.copy(alpha = 0.15f), contentColor = PassColor),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("취소하기", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    conflicting.filter { it.id != match.id }.forEach { other -> AppState.cancelExchange(other.id) }
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("이걸로 진행", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 교환 취소 후 재시도 제안 — 취소한 상대의 다른 옷 중에 이미 좋아요 눌러둔 게 있으면
// "그 중에서 다시 교환을 시도해보시겠어요?" 팝업으로 골라서 재시도할 수 있게 해준다.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun RetryExchangeDialog(
    partnerName: String,
    items: List<ClothingItem>,
    onDismiss: () -> Unit,
    onSelect: (ClothingItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${partnerName}님과의 교환이 취소됐어요", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    "${partnerName}님의 다른 옷 중에 좋아요 하신 게 있어요. 그 중에서 교환을 다시 시도해보시겠어요?",
                    color = TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(item.image, null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${item.size} · ${item.brand}", color = TextTertiary, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("나중에", color = TextTertiary) }
        }
    )
}

@Composable
fun MatchCard(
    match:               MatchItem,
    onOpenChat:          () -> Unit = {},
    onOpenShippingGuide: () -> Unit = {},
    onNeedsAddress:      () -> Unit = {},
    onRequestCancel:     () -> Unit = {}
) {
    val (statusColor, statusBg) = when (match.status) {
        ExchangeStatus.WAITING   -> Pair(StatusPending,  StatusPending.copy(alpha = 0.12f))
        ExchangeStatus.MATCHED   -> Pair(AccentYellow,   AccentYellow.copy(alpha = 0.12f))
        ExchangeStatus.CONFIRMED -> Pair(StatusShipping, StatusShipping.copy(alpha = 0.12f))
        ExchangeStatus.SHIPPING  -> Pair(StatusShipping, StatusShipping.copy(alpha = 0.12f))
        ExchangeStatus.COMPLETE  -> Pair(StatusComplete, StatusComplete.copy(alpha = 0.12f))
        ExchangeStatus.CANCELLED -> Pair(TextTertiary,   TextTertiary.copy(alpha = 0.12f))
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

        // confirm/ship/complete는 양쪽이 각자 호출해야 넘어가는 방식 — 내가 이미 했는데
        // 상대가 아직이면 대기 안내를 보여준다
        val waitingOnPartner = when (match.status) {
            ExchangeStatus.MATCHED   -> match.myConfirmed && !match.theirConfirmed
            ExchangeStatus.CONFIRMED -> match.myShipped && !match.theirShipped
            ExchangeStatus.SHIPPING  -> match.myReceived && !match.theirReceived
            else -> false
        }
        if (waitingOnPartner) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HourglassEmpty, contentDescription = null,
                    tint = StatusPending, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("상대방 확인을 기다리는 중이에요", color = StatusPending, fontSize = 11.sp)
            }
        }
        if (match.status == ExchangeStatus.CANCELLED) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (match.cancelledByMe == true) "내가 취소한 매칭이에요" else "상대방이 취소한 매칭이에요",
                color = TextTertiary, fontSize = 11.sp
            )
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
                    if (lastMsg.senderId == AppState.myUserId) "나: ${lastMsg.text}" else "${match.partner.name}: ${lastMsg.text}",
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
                    onClick  = {
                        if (!match.myConfirmed) {
                            AppState.confirmExchange(match.id) { result ->
                                if (result is ConfirmResult.NeedsAddress) onNeedsAddress()
                            }
                        }
                    },
                    enabled  = !match.myConfirmed,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow, contentColor = AccentYellowText,
                        disabledContainerColor = BgCardDark, disabledContentColor = TextTertiary
                    )
                ) {
                    Text(
                        if (match.myConfirmed) "상대방 확인 대기 중" else "교환 확정하기",
                        fontWeight = FontWeight.Black, fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick  = onOpenChat,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("채팅하기", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick  = onRequestCancel,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PassColor)
                    ) {
                        Text("취소하기", fontSize = 13.sp)
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick  = onOpenChat,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("채팅하기", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick  = onRequestCancel,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PassColor)
                    ) {
                        Text("취소하기", fontSize = 13.sp)
                    }
                }
            }

            ExchangeStatus.SHIPPING -> {
                Button(
                    onClick  = { if (!match.myReceived) AppState.completeExchange(match.id) },
                    enabled  = !match.myReceived,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = StatusComplete.copy(alpha = 0.15f),
                        contentColor   = StatusComplete,
                        disabledContainerColor = BgCardDark, disabledContentColor = TextTertiary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (match.myReceived) "상대방 수령 대기 중" else "교환 완료(도착 확인)",
                        fontWeight = FontWeight.Black, fontSize = 14.sp
                    )
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
                // ⚠️ 서버는 status==COMPLETE(양쪽 다 수령확인)일 때만 리뷰 제출을 받아주지만,
                //    나는 이미 수령확인을 눌렀으니(myReceived) 미리 별점을 골라둘 수 있게 해준다.
                //    실제 반영/제출은 상대도 수령확인 눌러서 COMPLETE가 되는 순간 자동으로 됨.
                if (match.myReceived) {
                    Spacer(Modifier.height(8.dp))
                    ReviewPrompt(match)
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
                Spacer(Modifier.height(8.dp))
                ReviewPrompt(match)
            }

            ExchangeStatus.CANCELLED -> {
                Row(
                    Modifier.fillMaxWidth()
                        .background(TextTertiary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, contentDescription = null,
                        tint = TextTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("취소된 매칭이에요", color = TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            else -> {}
        }

        // ⚠️ 표준 신고 남용 방지: 매칭이 실제로 성사된 상대에게만, 교환 1건당 1회만 가능.
        //    작고 눈에 안 띄는 텍스트로 둬서 오남용(가벼운 클릭)을 줄임 — 필요할 때만 찾아 누르게.
        if (match.status == ExchangeStatus.MATCHED || match.status == ExchangeStatus.CONFIRMED ||
            match.status == ExchangeStatus.SHIPPING || match.status == ExchangeStatus.COMPLETE) {
            var showReportDialog by remember { mutableStateOf(false) }
            Spacer(Modifier.height(10.dp))
            Text(
                if (AppState.canReport(match.id)) "이 거래에 문제가 있었나요? · 신고하기" else "신고가 접수됐어요",
                color = TextTertiary, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
                    .then(
                        if (AppState.canReport(match.id))
                            Modifier.clickable { showReportDialog = true }
                        else Modifier
                    ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (showReportDialog) {
                ReportDialog(
                    partnerName = match.partner.name,
                    onDismiss = { showReportDialog = false },
                    onSubmit = { reason, detail ->
                        AppState.reportExchange(match.id, reason, detail)
                        showReportDialog = false
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 평점(리뷰) — 완료된 교환에만 노출. 코멘트 없이 별점 1~5만 (스펙 2절 확정)
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewPrompt(match: MatchItem) {
    val matchId = match.id
    val partnerName = match.partner.name
    val isComplete = match.status == ExchangeStatus.COMPLETE
    val pendingScore = AppState.pendingReviewScores[matchId]

    var status by remember(matchId, isComplete) { mutableStateOf<ReviewStatusResponse?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // ⚠️ 서버 리뷰 상태 조회는 COMPLETE일 때만 의미가 있음(그 전엔 어차피 canReview=false로 옴)
    LaunchedEffect(matchId, isComplete) {
        if (isComplete) status = AppState.getReviewStatus(matchId)
    }

    if (isComplete) {
        val s = status ?: return // 조회 실패/로딩 중이면 조용히 아무것도 안 보여줌

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
            ReviewDialog(
                partnerName = partnerName,
                isSubmitting = isSubmitting,
                onDismiss = { showDialog = false },
                onSubmit = { score ->
                    isSubmitting = true
                    AppState.submitReview(matchId, score) { success ->
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
            Text(
                "미리 선택해뒀어요 · 상대방 수령확인되면 자동 반영",
                color = TextSecondary, fontSize = 10.sp, maxLines = 1
            )
        }
        return
    }

    if (showDialog) {
        ReviewDialog(
            partnerName = partnerName,
            isSubmitting = false,
            onDismiss = { showDialog = false },
            onSubmit = { score ->
                AppState.stageOrSubmitReview(matchId, score)
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
private fun ReviewDialog(
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

// ─────────────────────────────────────────────────────────────────────
// 신고 — 표준적인 남용 방지책: 정해진 사유 중 선택 필수 + 최종 확인 단계
// ─────────────────────────────────────────────────────────────────────

private enum class ReportReason(val code: String, val label: String) {
    NO_SHIP("NO_SHIP", "발송을 안 하거나 연락이 끊겼어요"),
    FAKE_OR_DAMAGED("FAKE_OR_DAMAGED", "설명과 다르거나 파손된 상품을 보냈어요"),
    RUDE_BEHAVIOR("RUDE_BEHAVIOR", "욕설·비매너 행동을 했어요"),
    SCAM_SUSPECTED("SCAM_SUSPECTED", "사기가 의심돼요"),
    OTHER("OTHER", "기타")
}

@Composable
private fun ReportDialog(
    partnerName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String?) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: 사유 선택, 2: 최종 확인
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var detail by remember { mutableStateOf("") }

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${partnerName}님을 신고할까요?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("사유를 선택해주세요", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    ReportReason.values().forEach { reason ->
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
