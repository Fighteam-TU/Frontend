package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.MatchResult
import com.fighteam.wannawear.data.RemoveResult
import com.fighteam.wannawear.data.model.*
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────
// 탭 정의
// ─────────────────────────────────────────────────────────────────────

private enum class ClosetTab(val label: String, val emoji: String) {
    MY_CLOSET("내 옷장", "🗂"),
    RECEIVED_LIKES("받은 관심", "💛"),
    MY_LIKES("보낸 관심", "🤍")
}

// ─────────────────────────────────────────────────────────────────────
// ClosetScreen
// ─────────────────────────────────────────────────────────────────────

@Composable
fun ClosetScreen(onNavigateToAdd: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(ClosetTab.MY_CLOSET) }
    var viewingGroup by remember { mutableStateOf<Pair<ClothingItem, List<User>>?>(null) }
    var detailItem by remember { mutableStateOf<ClothingItem?>(null) }
    var matchedResult by remember { mutableStateOf<MatchItem?>(null) }
    var deleteConfirmItem by remember { mutableStateOf<ClothingItem?>(null) }
    var deleteBlockedMessage by remember { mutableStateOf<String?>(null) }

    // ⚠️ 이 앱엔 옷장/매칭 관련 실시간 소켓·푸시가 없어서, "탭을 바꿀 때마다 조용히 새로고침"이
    //    최소한의 동기화 수단이다. 당겨서 새로고침(pull-to-refresh)은 각 탭 내부에도 별도로 있음.
    //    매칭(matches)을 먼저 최신화해야 "완료된 옷" 판정이 낡은 데이터로 되는 걸 막을 수 있어서
    //    순서대로(매칭 → 해당 탭 데이터) 불러온다.
    LaunchedEffect(selectedTab) {
        runCatching { AppState.loadExchanges() }
        when (selectedTab) {
            ClosetTab.MY_CLOSET      -> runCatching { AppState.loadMyCloset() }
            ClosetTab.RECEIVED_LIKES -> runCatching { AppState.loadReceivedLikes() }
            ClosetTab.MY_LIKES       -> runCatching { AppState.loadSentLikes() }
        }
    }

    // 매치 팝업 (최우선)
    matchedResult?.let { match ->
        RealMatchPopup(match = match, onClose = {
            matchedResult = null
            viewingGroup = null
        })
        return
    }

    // "받은 관심" 그룹(같은 내 옷에 관심 보낸 사람들) 옷장 모아보기 다이얼로그
    viewingGroup?.let { (myItem, fromUsers) ->
        CombinedInterestedClosetDialog(
            myItem       = myItem,
            fromUsers    = fromUsers,
            onDismiss    = { viewingGroup = null },
            onMatched    = { matchedResult = it },
            onShowDetail = { detailItem = it }
        )
    }

    // 삭제 확인 다이얼로그
    deleteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmItem = null },
            title = { Text("이 옷을 내릴까요?", fontWeight = FontWeight.Bold) },
            text  = { Text("\"${item.name}\"이(가) 옷장과 발견 탭에서 사라져요. 이 작업은 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    AppState.removeMyItem(item.id) { result ->
                        when (result) {
                            is RemoveResult.BlockedInExchange ->
                                deleteBlockedMessage = "이미 교환이 진행 중인 옷은 내릴 수 없어요. 매칭 탭에서 먼저 교환을 완료해주세요."
                            else -> detailItem = null
                        }
                    }
                    deleteConfirmItem = null
                }) { Text("내리기", color = PassColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmItem = null }) { Text("취소") }
            }
        )
    }

    // 삭제 불가 안내 다이얼로그
    deleteBlockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteBlockedMessage = null },
            title   = { Text("내릴 수 없어요", fontWeight = FontWeight.Bold) },
            text    = { Text(message) },
            confirmButton = {
                TextButton(onClick = { deleteBlockedMessage = null }) { Text("확인") }
            }
        )
    }

    // 상세보기 바텀시트
    detailItem?.let { item ->
        ItemDetailSheet(
            item             = item,
            showLikeButton   = item.user.id != AppState.myUserId,
            showDeleteButton = item.user.id == AppState.myUserId,
            onLike           = {
                AppState.likeItem(item) { result ->
                    if (result is MatchResult.Matched) matchedResult = result.match
                    detailItem = null
                }
            },
            onDelete  = { deleteConfirmItem = item },
            onDismiss = { detailItem = null }
        )
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {

        // 헤더
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("옷장", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Button(
                onClick = onNavigateToAdd,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("옷 추가", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // 탭 바
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(BgCard, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            ClosetTab.values().forEach { tab ->
                val selected = selectedTab == tab
                val badgeCount = when (tab) {
                    ClosetTab.RECEIVED_LIKES -> AppState.receivedLikes
                        .count { !AppState.isItemCompleted(it.myItem.id) }
                    ClosetTab.MY_LIKES       -> AppState.sentLikes.size
                    else                     -> 0
                }
                Button(
                    onClick = { selectedTab = tab },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AccentYellow else Color.Transparent,
                        contentColor   = if (selected) AccentYellowText else TextSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        "${tab.emoji} ${tab.label}",
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    )
                    if (badgeCount > 0 && !selected) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier.size(16.dp).background(AccentYellow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$badgeCount", color = AccentYellowText, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            ClosetTab.MY_CLOSET      -> MyClosetTab(onNavigateToAdd, onShowDetail = { detailItem = it })
            ClosetTab.RECEIVED_LIKES -> ReceivedLikesTab(
                onOpenGroup  = { myItem, fromUsers -> viewingGroup = myItem to fromUsers },
                onShowDetail = { detailItem = it }
            )
            ClosetTab.MY_LIKES       -> MyLikesTab(
                onShowDetail = { detailItem = it },
                onLike       = { item ->
                    AppState.likeItem(item) { result ->
                        if (result is MatchResult.Matched) matchedResult = result.match
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 탭1: 내 옷장
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyClosetTab(onNavigateToAdd: () -> Unit, onShowDetail: (ClothingItem) -> Unit) {
    val items = AppState.myCloset
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                runCatching { AppState.loadMyCloset() }
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (AppState.isLoading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentYellow)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val inExchange by remember { derivedStateOf { AppState.isItemInExchange(item.id) } }
                    ClosetItemCard(item = item, inExchange = inExchange, onClick = { onShowDetail(item) })
                }
                item {
                    Box(
                        Modifier.height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .clickable { onNavigateToAdd() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(40.dp).background(BgCardDark, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                            Text("옷 추가하기", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 탭2: 받은 관심
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceivedLikesTab(
    onOpenGroup: (myItem: ClothingItem, fromUsers: List<User>) -> Unit,
    onShowDetail: (ClothingItem) -> Unit
) {
    // 교환완료된 내 아이템 관련 기록은 숨김
    val likes = AppState.receivedLikes.filter { !AppState.isItemCompleted(it.myItem.id) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ✅ 같은 내 옷(myItem)에 여러 명이 관심 보내면 하나의 그룹으로 묶는다.
    //    카드 수가 사람 수만큼 늘어나서 헷갈리던 문제 → "옷 1개 = 카드 1개"로 정리.
    val groups = remember(likes) {
        val order = LinkedHashMap<Int, ClothingItem>()
        val usersByItem = LinkedHashMap<Int, MutableList<User>>()
        likes.forEach { like ->
            order.putIfAbsent(like.myItem.id, like.myItem)
            val bucket = usersByItem.getOrPut(like.myItem.id) { mutableListOf() }
            if (bucket.none { it.id == like.fromUser.id }) bucket.add(like.fromUser)
        }
        order.values.map { item -> item to (usersByItem[item.id] ?: emptyList()) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                runCatching { AppState.loadReceivedLikes() }
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (groups.isEmpty()) {
            EmptyState("💛", "아직 받은 관심이 없어요", "옷을 더 등록해보세요!")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groups, key = { it.first.id }) { (myItem, fromUsers) ->
                    val inExchange by remember { derivedStateOf { AppState.isItemInExchange(myItem.id) } }
                    GroupedReceivedLikeCard(
                        myItem       = myItem,
                        fromUsers    = fromUsers,
                        inExchange   = inExchange,
                        onOpenGroup  = { onOpenGroup(myItem, fromUsers) },
                        onShowDetail = { onShowDetail(myItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupedReceivedLikeCard(
    myItem: ClothingItem,
    fromUsers: List<User>,
    inExchange: Boolean,
    onOpenGroup: () -> Unit,
    onShowDetail: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 내 옷 썸네일 + 교환중 태그
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)).clickable { onShowDetail() }) {
            AsyncImage(myItem.image, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (inExchange) {
                Text(
                    "교환중", color = AccentYellowText, fontSize = 8.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .background(AccentYellow, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            // 관심 보낸 사람들 아바타를 겹쳐서 스택으로 보여줌
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    fromUsers.take(4).forEach { u ->
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(BgCard)
                                .border(1.5.dp, BgCard, CircleShape)
                        ) {
                            AsyncImage(u.avatar, null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        }
                    }
                    if (fromUsers.size > 4) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(BgCardDark).border(1.5.dp, BgCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${fromUsers.size - 4}", color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text("${fromUsers.size}명이 관심 🤍", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(myItem.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            Text("${myItem.size}  ${myItem.heightFit}", color = TextTertiary, fontSize = 10.sp)
        }

        Button(
            onClick = onOpenGroup,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("옷장 모아보기", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 탭3: 보낸 관심
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyLikesTab(
    onShowDetail: (ClothingItem) -> Unit,
    onLike: (ClothingItem) -> Unit
) {
    // 교환완료된 상대 아이템은 숨김
    val likes = AppState.sentLikes.filter { !AppState.isTheirItemCompleted(it.item.id) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                runCatching { AppState.loadSentLikes() }
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (likes.isEmpty()) {
            EmptyState("🤍", "아직 관심 표시한 옷이 없어요", "발견 탭에서 마음에 드는 옷을 찾아보세요")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp)
            ) {
                items(likes, key = { it.item.id }) { like ->
                    val item = like.item
                    val alreadyLiked by remember { derivedStateOf { AppState.sentLikes.any { it.item.id == item.id } } }
                    val inExchange by remember {
                        derivedStateOf {
                            AppState.matches.any { m ->
                                m.theirItem.id == item.id && m.status != ExchangeStatus.COMPLETE
                            }
                        }
                    }
                    InteractiveItemCard(
                        item         = item,
                        alreadyLiked = alreadyLiked,
                        inExchange   = inExchange,
                        onClick      = { onShowDetail(item) },
                        onLike       = { onLike(item) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 공통 카드
// ─────────────────────────────────────────────────────────────────────

@Composable
fun ClosetItemCard(
    item: ClothingItem,
    inExchange: Boolean = false,
    onClick: () -> Unit = {}
) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(BgCard).clickable { onClick() }) {
        Column {
            Box(Modifier.fillMaxWidth().height(152.dp)) {
                AsyncImage(item.image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                // ⚠️ item.isListed는 서버 ItemResponse에 없는 필드라 클라이언트에서 항상 true로
                //    채워져 있음 — 뱃지 판정에 쓰면 안 됨. 실제 교환 진행 여부(matches 기준)만 본다.
                if (inExchange) {
                    Text(
                        "교환중", color = AccentYellowText, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(8.dp)
                            .background(AccentYellow, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .align(Alignment.TopStart)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(item.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${item.brand} · ${item.size}", color = TextSecondary, fontSize = 10.sp)
                if (item.heightFit.isNotEmpty()) Text(item.heightFit, color = TextTertiary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun InteractiveItemCard(
    item: ClothingItem,
    alreadyLiked: Boolean,
    inExchange: Boolean = false,
    onClick: () -> Unit,
    onLike: () -> Unit,
    showOwnerBadge: Boolean = false
) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(BgCardDark).clickable { onClick() }) {
        Column {
            Box(Modifier.fillMaxWidth().height(152.dp)) {
                AsyncImage(item.image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

                // 교환중 태그 (좌상단)
                if (inExchange) {
                    Text(
                        "교환중", color = AccentYellowText, fontSize = 8.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                            .background(AccentYellow, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // 하트 버튼 (우상단)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp)
                        .background(if (alreadyLiked) AccentYellow else Color(0xAA000000), CircleShape)
                        .clickable { onLike() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (alreadyLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "좋아요",
                        tint = if (alreadyLiked) AccentYellowText else AccentYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // ✅ 여러 명 옷장을 모아서 볼 때, 이 아이템이 누구 옷장 건지 구분하기 위한 작은 프로필 뱃지 (좌하단)
                if (showOwnerBadge) {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            item.user.avatar, null,
                            modifier = Modifier.size(14.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(item.user.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(item.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${item.brand} · ${item.size}", color = TextSecondary, fontSize = 10.sp)
                if (item.heightFit.isNotEmpty()) Text(item.heightFit, color = TextTertiary, fontSize = 9.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 상세보기 바텀시트
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: ClothingItem,
    showLikeButton: Boolean,
    onLike: () -> Unit,
    onDismiss: () -> Unit,
    showDeleteButton: Boolean = false,
    onDelete: () -> Unit = {}
) {
    val alreadyLiked by remember { derivedStateOf { AppState.sentLikes.any { it.item.id == item.id } } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).background(TextTertiary, RoundedCornerShape(50)))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {

            // 대표 이미지
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(item.image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(80.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .size(32.dp).background(Color(0x88000000), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // 실착 샷
            if (item.wearingImage.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    AsyncImage(item.wearingImage, "실착 샷", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text("실착 샷", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                            .background(Color(0x88000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            // 기본 정보
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(item.brand.uppercase(), color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(item.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))

                if (item.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.forEach { tag ->
                            Text("#$tag", color = TextSecondary, fontSize = 11.sp,
                                modifier = Modifier.background(BgCardDark, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // 인포 박스
                Row(Modifier.fillMaxWidth().background(BgCardDark, RoundedCornerShape(12.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround) {
                    InfoCell("사이즈", item.size)
                    Box(Modifier.width(1.dp).height(36.dp).background(BorderSubtle).align(Alignment.CenterVertically))
                    InfoCell("권장 키", item.heightFit.ifEmpty { "-" })
                    Box(Modifier.width(1.dp).height(36.dp).background(BorderSubtle).align(Alignment.CenterVertically))
                    InfoCell("상태", item.condition)
                    Box(Modifier.width(1.dp).height(36.dp).background(BorderSubtle).align(Alignment.CenterVertically))
                    InfoCell("카테고리", item.category.label)
                }

                if (item.description.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("설명", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(item.description, color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().background(BgCardDark, RoundedCornerShape(10.dp)).padding(14.dp))
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(item.user.avatar, null, modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(item.user.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (item.distance.isNotEmpty()) Text(item.distance, color = TextTertiary, fontSize = 10.sp)
                    }
                }

                if (showLikeButton) {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onLike() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alreadyLiked) BgCardDark else AccentYellow,
                            contentColor   = if (alreadyLiked) TextSecondary else AccentYellowText
                        )
                    ) {
                        Icon(if (alreadyLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (alreadyLiked) "관심 취소하기" else "이 옷에 관심 보내기",
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }

                if (showDeleteButton) {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { onDelete() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PassColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PassColor)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("이 옷 내리기", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────
// 받은 관심 그룹 — 관심 보낸 사람들의 옷장을 하나로 모아보는 다이얼로그
// ─────────────────────────────────────────────────────────────────────

@Composable
fun CombinedInterestedClosetDialog(
    myItem: ClothingItem,
    fromUsers: List<User>,
    onDismiss: () -> Unit,
    onMatched: (MatchItem) -> Unit,
    onShowDetail: (ClothingItem) -> Unit
) {
    // 유저별로 옷장을 각각 불러와서 합친다. 키는 유저 id — 아직 안 불러온 사람은 map에 없음(로딩 판정용).
    // 값이 null = 그 사람 옷장 불러오기 실패, emptyList() = 정말로 빈 옷장 (구분해서 보여줘야
    // "옷장이 비어있다"고 잘못 뜨는 문제가 안 생김).
    var itemsByUser by remember(fromUsers) { mutableStateOf<Map<Int, List<ClothingItem>?>>(emptyMap()) }
    var filterUserId by remember(fromUsers) { mutableStateOf<Int?>(null) } // null = 전체 보기
    var retryTick by remember(fromUsers) { mutableStateOf(0) }

    LaunchedEffect(fromUsers, retryTick) {
        itemsByUser = emptyMap()
        fromUsers.forEach { user ->
            AppState.loadUserCloset(user.id) { items ->
                itemsByUser = itemsByUser + (user.id to items)
            }
        }
    }

    val stillLoading = itemsByUser.size < fromUsers.size
    val failedUsers = fromUsers.filter { itemsByUser.containsKey(it.id) && itemsByUser[it.id] == null }
    val combinedItems = remember(itemsByUser, filterUserId) {
        itemsByUser.values.filterNotNull().flatten()
            // ⚠️ 이미 나와 교환 완료된 아이템은 다시 좋아요 보낼 수 있는 것처럼 보이면 안 됨
            .filterNot { AppState.isTheirItemCompleted(it.id) }
            .filter { filterUserId == null || it.user.id == filterUserId }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(20.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    myItem.image, null,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("\"${myItem.name}\" 관심 ${fromUsers.size}명", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("옷장을 모아봤어요 · 카드 클릭 = 상세보기", color = TextSecondary, fontSize = 10.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 필터 칩 — "전체" + 관심 보낸 사람별로 눌러서 그 사람 옷만 보기
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterUserId == null,
                    onClick  = { filterUserId = null },
                    label    = { Text("전체", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentYellow, selectedLabelColor = AccentYellowText,
                        containerColor = BgCardDark, labelColor = TextSecondary
                    ),
                    border = null
                )
                fromUsers.forEach { u ->
                    FilterChip(
                        selected = filterUserId == u.id,
                        onClick  = { filterUserId = u.id },
                        leadingIcon = {
                            AsyncImage(u.avatar, null, modifier = Modifier.size(16.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        },
                        label = { Text(u.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentYellow, selectedLabelColor = AccentYellowText,
                            containerColor = BgCardDark, labelColor = TextSecondary
                        ),
                        border = null
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (failedUsers.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(PassColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${failedUsers.size}명의 옷장을 불러오지 못했어요",
                        color = PassColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { retryTick++ }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("재시도", color = PassColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (stillLoading && combinedItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentYellow)
                }
                return@Column
            }

            if (combinedItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (failedUsers.isNotEmpty()) "불러오기에 실패해서 표시할 옷이 없어요"
                        else "옷장이 비어있어요",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(380.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(combinedItems, key = { "${it.user.id}-${it.id}" }) { item ->
                    val alreadyLiked by remember { derivedStateOf { AppState.sentLikes.any { it.item.id == item.id } } }
                    val inExchange by remember {
                        derivedStateOf {
                            AppState.matches.any { m ->
                                m.theirItem.id == item.id && m.status != ExchangeStatus.COMPLETE
                            }
                        }
                    }
                    InteractiveItemCard(
                        item           = item,
                        alreadyLiked   = alreadyLiked,
                        inExchange     = inExchange,
                        showOwnerBadge = fromUsers.size > 1 && filterUserId == null,
                        onClick        = { onShowDetail(item) },
                        onLike         = {
                            AppState.likeItem(item) { result ->
                                if (result is MatchResult.Matched) onMatched(result.match)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 빈 상태
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(emoji: String, title: String, sub: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 36.sp)
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(sub, color = TextSecondary, fontSize = 13.sp)
        }
    }
}
