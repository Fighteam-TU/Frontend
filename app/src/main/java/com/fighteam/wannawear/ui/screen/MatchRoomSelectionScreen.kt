package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.ui.theme.*

/**
 * MatchRoom 다중 아이템 선택 화면.
 * ⚠️ 2026-07-04 추가, v0.1 설계 제안 단계(match-room-spec.md) — 백엔드 미배포.
 * 내가 받고 싶은 아이템(상대 소유, 내가 좋아요한 것)들을 여러 개 골라서 저장한다.
 * 실제 잠금(lock-selection)은 이 화면이 아니라 MatchRoomsScreen의 방 카드에서 별도로 함.
 */
@Composable
fun MatchRoomSelectionScreen(roomId: Int, onBack: () -> Unit) {
    val room = AppState.matchRooms.firstOrNull { it.id == roomId }
    var candidates by remember { mutableStateOf<List<ClothingItem>?>(null) }
    var selectedIds by remember(room?.id) { mutableStateOf(room?.myWantList?.map { it.id }?.toSet() ?: emptySet()) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) {
        AppState.loadMatchRoomCandidates(roomId) { my, _ -> candidates = my }
    }

    // ⚠️ 버그 수정: 서버가 주는 candidates는 "새로 고를 수 있는 후보"만 담고 있어서, 이미 이 방에
    // 잠겨있는 아이템(room.myWantList)은 여기 안 들어있을 수 있음(그 아이템은 status=in_exchange로
    // 취급돼서 후보 풀에서 빠짐 — 스펙 6절 참고). 그래서 이미 선택된 옷을 체크박스로 "빼는" 것 자체가
    // 안 됐음(화면에 아예 안 보이니까). candidates + 이미 선택된 목록을 합쳐서 전부 토글 가능하게 함.
    val mergedList = candidates?.let { base ->
        val alreadySelected = room?.myWantList ?: emptyList()
        (alreadySelected + base).distinctBy { it.id }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("받고 싶은 옷 고르기", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                room?.let {
                    Text("${it.partner.name}님의 옷 중 내가 좋아요한 것들이에요", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            val list = mergedList
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentYellow)
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🤔", fontSize = 32.sp)
                        Text("아직 좋아요한 옷이 없어요", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { item ->
                        val selected = item.id in selectedIds
                        SelectableCandidateCard(
                            item = item,
                            selected = selected,
                            onToggle = {
                                selectedIds = if (selected) selectedIds - item.id else selectedIds + item.id
                            }
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    isSaving = true
                    AppState.updateMatchRoomSelection(roomId, selectedIds.toList()) { success ->
                        isSaving = false
                        if (success) onBack()
                    }
                },
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText)
            ) {
                Text(
                    if (isSaving) "저장 중..." else "선택 저장하기 (${selectedIds.size}개)",
                    fontWeight = FontWeight.Black, fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SelectableCandidateCard(
    item: ClothingItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(BgCardDark).clickable { onToggle() }
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(152.dp)) {
                AsyncImage(item.image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                        .background(if (selected) AccentYellow else Color(0xAA000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = "선택됨", tint = AccentYellowText, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(item.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${item.brand} · ${item.size}", color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}
