package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.NotificationItem
import com.fighteam.wannawear.data.model.NotificationType
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenExchange: (Int) -> Unit
) {
    val notifications = AppState.notifications
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { AppState.loadNotifications() }

    // 무한스크롤: 리스트 끝에 가까워지면 다음 페이지 로드
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                if (lastVisible >= notifications.size - 3 && notifications.isNotEmpty()) {
                    AppState.loadMoreNotifications()
                }
            }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 앱바
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
            }
            Text(
                "알림", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            if (notifications.any { !it.isRead }) {
                TextButton(onClick = { AppState.markAllNotificationsRead() }) {
                    Text("모두 읽음", color = AccentYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { AppState.loadNotifications() }
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔔", fontSize = 32.sp)
                        Text("아직 알림이 없어요", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { noti ->
                        NotificationRow(
                            notification = noti,
                            onClick = {
                                AppState.markNotificationRead(noti.id)
                                noti.exchangeId?.let { onOpenExchange(it) }
                            }
                        )
                    }
                    if (AppState.hasMoreNotifications) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AccentYellow, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(type: NotificationType): String = when (type) {
    NotificationType.EXCHANGE_MATCHED   -> "💛"
    NotificationType.NEW_MESSAGE        -> "💬"
    NotificationType.ADDRESS_CONFIRMED  -> "📮"
    NotificationType.ITEM_SHIPPED       -> "📦"
    NotificationType.SHIPPING_STARTED   -> "🚚"
    NotificationType.ITEM_RECEIVED      -> "✅"
    NotificationType.EXCHANGE_COMPLETED -> "🎉"
    NotificationType.EXCHANGE_MODIFICATION_REQUESTED -> "🔄" // 2026-07-04 추가 (match-room-spec.md §9)
    NotificationType.UNKNOWN            -> "🔔"
}

@Composable
private fun NotificationRow(notification: NotificationItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (notification.isRead) BgCard else BgCardDark, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).background(BgCard, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(iconFor(notification.type), fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                notification.title, color = TextPrimary, fontSize = 13.sp,
                fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(notification.body, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(notification.createdAt, color = TextTertiary, fontSize = 10.sp)
        }
        if (!notification.isRead) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).background(AccentYellow, CircleShape))
        }
    }
}
