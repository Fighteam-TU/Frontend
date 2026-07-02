package com.fighteam.wannawear.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.MatchResult
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.data.model.MatchItem
import com.fighteam.wannawear.ui.theme.*
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    val cards = AppState.discoverCards
    var matchedResult by remember { mutableStateOf<MatchItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 최초 진입 시 발견 덱이 비어있으면 한 번 더 로드 시도
    LaunchedEffect(Unit) {
        if (cards.isEmpty() && !AppState.isLoading) {
            AppState.refreshDiscoverFeed()
        }
    }

    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.fillMaxSize()) {

            // ── 헤더 ─────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("WannaWear", color = TextPrimary, fontSize = 28.sp,
                        fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    Text("서울 · ${cards.size}개 아이템", color = TextSecondary, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)
                        .background(BgCard, CircleShape)) {
                        Icon(Icons.Default.Search, contentDescription = null,
                            tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)
                        .background(BgCard, CircleShape)) {
                        Icon(Icons.Default.Settings, contentDescription = null,
                            tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── 카드 스택 ─────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            runCatching { AppState.loadDiscoverFeed() }
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (AppState.isLoading && cards.isEmpty()) {
                    CircularProgressIndicator(color = AccentYellow)
                } else if (cards.isEmpty()) {
                    // 빈 상태 — 새 아이템 보기 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🌿", fontSize = 36.sp)
                        Text("주변 아이템을 모두 봤어요", color = TextPrimary,
                            fontWeight = FontWeight.Bold)
                        Text("좋아요 안 한 다른 옷들을 보여드릴게요",
                            color = TextSecondary, fontSize = 13.sp)
                        Button(
                            onClick = {
                                scope.launch { AppState.refreshDiscoverFeed() }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentYellow,
                                contentColor   = AccentYellowText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("새 아이템 보기", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // ✅ 고정 3장 기준(2 - revIdx) 대신 실제 표시 개수 기준으로 stackIdx 계산.
                    //    덱이 3장 미만으로 줄어도(마지막 1~2장) top 카드가 정확히 지정되어 스와이프가 계속 먹는다.
                    val visible = cards.take(3)
                    visible.reversed().forEachIndexed { revIdx, item ->
                        val stackIdx = (visible.size - 1) - revIdx
                        val isTop    = stackIdx == 0
                        SwipeCard(
                            item       = item,
                            stackIndex = stackIdx,
                            isTop      = isTop,
                            onSwiped   = { isLike ->
                                val top = cards.firstOrNull() ?: return@SwipeCard
                                cards.removeAt(0)
                                if (isLike) {
                                    AppState.likeItem(top) { result ->
                                        if (result is MatchResult.Matched) matchedResult = result.match
                                    }
                                } else {
                                    AppState.passItem(top.id)
                                }
                            }
                        )
                    }
                }
                }
                }
            }

            // ── 액션 버튼 ─────────────────────────────────────────────
            if (cards.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // 패스
                    IconButton(
                        onClick = {
                            val top = cards.firstOrNull() ?: return@IconButton
                            cards.removeAt(0)
                            AppState.passItem(top.id)
                        },
                        modifier = Modifier.size(52.dp).background(BgCardDark, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "패스",
                            tint = TextSecondary, modifier = Modifier.size(22.dp))
                    }

                    Spacer(Modifier.width(20.dp))

                    // 새 아이템 보기
                    IconButton(onClick = {
                        scope.launch {
                            AppState.refreshDiscoverFeed()
                            snackbarHostState.showSnackbar(
                                message  = "새 아이템을 불러왔어요 ✨",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "새 아이템",
                            tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.width(20.dp))

                    // 좋아요
                    IconButton(
                        onClick = {
                            val top = cards.firstOrNull() ?: return@IconButton
                            cards.removeAt(0)
                            AppState.likeItem(top) { result ->
                                if (result is MatchResult.Matched) matchedResult = result.match
                            }
                        },
                        modifier = Modifier.size(52.dp).background(AccentYellow, CircleShape)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "좋아요",
                            tint = AccentYellowText, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // ── 스낵바 ────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        ) { data ->
            Snackbar(
                snackbarData   = data,
                containerColor = BgCard,
                contentColor   = TextPrimary,
                shape          = RoundedCornerShape(12.dp)
            )
        }

        // ── 매치 팝업 ─────────────────────────────────────────────────
        matchedResult?.let { match ->
            RealMatchPopup(
                match   = match,
                onClose = {
                    matchedResult = null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message  = "매칭 내역은 매칭 탭에서 확인하세요 💛",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// SwipeCard
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SwipeCard(
    item:       ClothingItem,
    stackIndex: Int,
    isTop:      Boolean,
    onSwiped:   (Boolean) -> Unit
) {
    var offsetX    by remember { mutableStateOf(0f) }
    var showDetail by remember { mutableStateOf(false) }
    val rotation   by animateFloatAsState(targetValue = offsetX / 25f, label = "rotate")
    val scale  = 1f - stackIndex * 0.04f
    val yOffset = (stackIndex * 13).dp

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationY = yOffset.toPx()
                translationX = if (isTop) offsetX else 0f
                rotationZ    = if (isTop) rotation else 0f
            }
            .clip(RoundedCornerShape(26.dp))
            .background(BgCard)
            .then(
                if (isTop) Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount },
                        onDragEnd = {
                            if (abs(offsetX) > 250) onSwiped(offsetX > 0)
                            else offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f }
                    )
                } else Modifier
            )
    ) {
        AsyncImage(
            model            = item.image,
            contentDescription = item.name,
            contentScale     = ContentScale.Crop,
            modifier         = Modifier.fillMaxSize()
        )

        // LIKE / NOPE 스탬프
        if (isTop && offsetX > 30f) {
            Text("LIKE", color = AccentYellow, fontSize = 52.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(20.dp).rotate(-14f)
                    .graphicsLayer { alpha = (offsetX / 200f).coerceIn(0f, 1f) })
        }
        if (isTop && offsetX < -30f) {
            Text("NOPE", color = PassColor, fontSize = 52.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).rotate(14f)
                    .graphicsLayer { alpha = (-offsetX / 200f).coerceIn(0f, 1f) })
        }

        // 유저 / 거리 칩
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.background(Color(0x80000000), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(item.user.avatar, null,
                    modifier = Modifier.size(20.dp).clip(CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(item.user.name, color = TextPrimary, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            if (item.distance.isNotEmpty()) {
                Text(item.distance, color = TextSecondary, fontSize = 11.sp,
                    modifier = Modifier.background(Color(0x80000000), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }

        // 하단 정보
        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB000000))))
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(item.brand.uppercase(), color = Color(0x73FFFFFF),
                            fontSize = 10.sp, letterSpacing = 1.5.sp)
                        Text(item.name, color = TextPrimary, fontSize = 20.sp,
                            fontWeight = FontWeight.Black)
                    }
                    Column(horizontalAlignment = Alignment.End,
                        verticalArrangement    = Arrangement.spacedBy(4.dp)) {
                        Text(item.condition, color = AccentYellow, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0x2EE4D94A), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp))
                        Text("${item.size}  ${item.heightFit}",
                            color = Color(0x80FFFFFF), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.forEach { tag ->
                        Text("#$tag", color = Color(0x8CFFFFFF), fontSize = 10.sp,
                            modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }

                // 자세히 보기 토글
                if (isTop && (item.wearingImage.isNotEmpty() || item.description.isNotEmpty())) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDetail = !showDetail },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (showDetail) "간단히 보기 ↑" else "자세히 보기 ↓",
                            color = AccentYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (showDetail && isTop) {
                    Spacer(Modifier.height(6.dp))
                    if (item.description.isNotEmpty()) {
                        Text("📝 ${item.description}", color = Color(0xCCFFFFFF), fontSize = 11.sp,
                            modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    if (item.wearingImage.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("실착 샷", color = TextSecondary, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model              = item.wearingImage,
                            contentDescription = "실착 샷",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxWidth().height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// RealMatchPopup
// ─────────────────────────────────────────────────────────────────────

@Composable
fun RealMatchPopup(match: MatchItem, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Spacer(Modifier.height(48.dp))
            Text("서로 좋아요를 눌렀어요! 🎉", color = AccentYellow, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("MATCH", color = TextPrimary, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("${match.partner.name}님과 교환을 시작할 수 있어요",
                color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(match.myItem.image, null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))) {
                        Column(Modifier.padding(12.dp).align(Alignment.BottomStart)) {
                            Text(match.myItem.name, color = TextPrimary, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold)
                            Text("내 옷", color = Color(0x66FFFFFF), fontSize = 10.sp)
                        }
                    }
                }
                Text("×", color = TextTertiary, fontSize = 24.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.CenterVertically))
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(match.theirItem.image, null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))) {
                        Column(Modifier.padding(12.dp).align(Alignment.BottomStart)) {
                            Text(match.theirItem.name, color = TextPrimary, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold)
                            Text(match.partner.name, color = Color(0x66FFFFFF), fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("내 옷 사이즈", color = TextSecondary, fontSize = 10.sp)
                    Text(match.myItem.size, color = AccentYellow, fontSize = 16.sp,
                        fontWeight = FontWeight.Black)
                    if (match.myItem.heightFit.isNotEmpty())
                        Text(match.myItem.heightFit, color = TextSecondary, fontSize = 9.sp)
                }
                Box(Modifier.width(1.dp).height(40.dp).background(BorderSubtle)
                    .align(Alignment.CenterVertically))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("상대 옷 사이즈", color = TextSecondary, fontSize = 10.sp)
                    Text(match.theirItem.size, color = AccentYellow, fontSize = 16.sp,
                        fontWeight = FontWeight.Black)
                    if (match.theirItem.heightFit.isNotEmpty())
                        Text(match.theirItem.heightFit, color = TextSecondary, fontSize = 9.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    AppState.confirmExchange(match.id)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow,
                    contentColor   = AccentYellowText
                )
            ) {
                Text("교환 확정하기", fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("나중에 결정하기", color = TextTertiary)
            }
        }
    }
}
