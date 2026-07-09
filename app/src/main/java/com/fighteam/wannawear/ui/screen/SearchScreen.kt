package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.MatchResult
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.data.model.ExchangeStatus
import com.fighteam.wannawear.data.model.MatchItem
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var detailItem by remember { mutableStateOf<ClothingItem?>(null) }
    var matchedResult by remember { mutableStateOf<MatchItem?>(null) }
    val results = AppState.searchResults
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // ⚠️ 스펙 3절 권장: 타이핑마다 바로 호출하면 서버가 매번 전체 조회라 부하가 큼 → 300ms 디바운스
    LaunchedEffect(query) {
        delay(300)
        AppState.searchItems(query)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    matchedResult?.let { match ->
        RealMatchPopup(match = match, onClose = { matchedResult = null })
        return
    }

    detailItem?.let { item ->
        ItemDetailSheet(
            item           = item,
            showLikeButton = item.user.id != AppState.myUserId,
            onLike = {
                AppState.likeItem(item) { result ->
                    if (result is MatchResult.Matched) matchedResult = result.match
                    detailItem = null
                }
            },
            onDismiss = { detailItem = null }
        )
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 검색바
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("브랜드, 이름, 카테고리로 검색", color = TextTertiary, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "지우기", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = AccentYellow, unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentYellow
                )
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 32.sp)
                        Text("찾고 있는 옷을 검색해보세요", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                AppState.isSearching && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentYellow)
                }
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("😕", fontSize = 32.sp)
                        Text("\"$query\"에 대한 검색 결과가 없어요", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { it.id }) { item ->
                        val alreadyLiked by remember { derivedStateOf { AppState.sentLikes.any { it.item.id == item.id } } }
                        val inExchange by remember {
                            derivedStateOf { AppState.isTheirItemInExchange(item.id) }
                        }
                        InteractiveItemCard(
                            item         = item,
                            alreadyLiked = alreadyLiked,
                            inExchange   = inExchange,
                            onClick      = { detailItem = item },
                            onLike       = {
                                AppState.likeItem(item) { result ->
                                    if (result is MatchResult.Matched) matchedResult = result.match
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
