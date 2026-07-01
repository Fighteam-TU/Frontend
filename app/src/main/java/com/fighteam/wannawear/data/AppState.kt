package com.fighteam.wannawear.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.fighteam.wannawear.data.model.*
import com.fighteam.wannawear.data.remote.*
import com.fighteam.wannawear.data.remote.dto.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * 실제 WannaWear 백엔드(http://119.206.244.145:8080)와 통신하는 전역 상태.
 *
 * ⚠️ 일부 목록 API(/items/discover, /items/me, /exchanges, /exchanges/{id}/messages,
 * /likes/sent, /likes/received)는 스웨거상 정확한 응답 필드명이 없는 느슨한 타입으로
 * 노출돼 있어(Mappers.kt의 extractItems/extractExchanges/extractMessages 참고),
 * "가장 그럴듯한 키 이름"으로 추정해서 파싱한다. 실제로 붙여보고 필드명이 다르면
 * Mappers.kt의 후보 키 목록만 조정하면 된다.
 */
object AppState {
    private val api = RetrofitClient.api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()

    // ── 전역 상태 ─────────────────────────────────────────────────────
    var myProfile by mutableStateOf<User?>(null)
        private set

    val myUserId: Int get() = myProfile?.id ?: -1

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    val myCloset: SnapshotStateList<ClothingItem> = mutableStateListOf()
    val discoverCards: SnapshotStateList<ClothingItem> = mutableStateListOf()
    val matches: SnapshotStateList<MatchItem> = mutableStateListOf()
    val receivedLikes: SnapshotStateList<ReceivedLike> = mutableStateListOf()
    val sentLikes: SnapshotStateList<SentLike> = mutableStateListOf()
    val pendingMatchNotifications: SnapshotStateList<MatchItem> = mutableStateListOf()

    // 마지막으로 사용한 위경도 (발견 탭 새로고침 시 재사용)
    var lastLat: Double = 37.5665
        private set
    var lastLng: Double = 126.9780
        private set

    // ── 공통 API 호출 래퍼 ────────────────────────────────────────────
    private suspend fun <T> apiCall(block: suspend () -> ApiResponse<T>): T {
        val response = try {
            block()
        } catch (e: HttpException) {
            val raw = e.response()?.errorBody()?.string()
            val err = try {
                @Suppress("UNCHECKED_CAST")
                (gson.fromJson(raw, ApiResponse::class.java) as? ApiResponse<Any>)?.error
            } catch (_: Exception) { null }
            throw ApiException(err, e.code())
        } catch (e: IOException) {
            throw ApiException(ErrorBody(code = "NETWORK_ERROR", message = "네트워크 연결을 확인해주세요"))
        }
        if (!response.success || response.data == null) {
            throw ApiException(response.error)
        }
        return response.data
    }

    // ── 초기 데이터 로드 (로그인 직후 1회 호출) ───────────────────────
    fun loadInitialData(latitude: Double, longitude: Double) {
        lastLat = latitude
        lastLng = longitude
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                loadMyProfile()
            } catch (e: Exception) {
                errorMessage = e.message
            }
            coroutineScope {
                launch { runCatching { loadMyCloset() } }
                launch { runCatching { loadDiscoverFeed(latitude, longitude) } }
                launch { runCatching { loadExchanges() } }
                launch { runCatching { loadReceivedLikes() } }
                launch { runCatching { loadSentLikes() } }
            }
            isLoading = false
        }
    }

    suspend fun loadMyProfile() {
        val res = apiCall { api.getMyProfile() }
        myProfile = res.toUser()
    }

    suspend fun loadMyCloset() {
        val res = apiCall { api.getMyItems() }
        val items = extractItems(res).map { it.toClothingItem(fallbackUser = myProfile) }
        myCloset.clear()
        myCloset.addAll(items)
    }

    suspend fun loadDiscoverFeed(latitude: Double, longitude: Double) {
        val res = apiCall { api.discoverItems(latitude, longitude) }
        // ✅ isLikedByMe=true 인 아이템은 방어적으로 한 번 더 걸러낸다
        val items = extractItems(res)
            .filter { it.isLikedByMe != true }
            .map { it.toClothingItem() }
        discoverCards.clear()
        discoverCards.addAll(items)
    }

    /** "새 아이템 보기" 버튼: 같은 좌표로 다시 요청 (서버가 이미 본/좋아요한 아이템은 제외해줌) */
    fun refreshDiscoverFeed(latitude: Double = lastLat, longitude: Double = lastLng) {
        lastLat = latitude
        lastLng = longitude
        scope.launch {
            try {
                loadDiscoverFeed(latitude, longitude)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    suspend fun loadExchanges() {
        val res = apiCall { api.getExchanges() }
        val items = extractExchanges(res).map { it.toMatchItem() }
        matches.clear()
        matches.addAll(items)
    }

    suspend fun loadReceivedLikes() {
        val res = apiCall { api.getReceivedLikes() }
        @Suppress("UNCHECKED_CAST")
        val entries = (res as List<Map<String, Any?>>).toLikeEntries()
        receivedLikes.clear()
        receivedLikes.addAll(
            entries.mapNotNull { entry ->
                val item = entry.item?.toClothingItem() ?: return@mapNotNull null
                val fromUser = entry.fromUser?.toUser() ?: return@mapNotNull null
                ReceivedLike(fromUser = fromUser, myItem = item)
            }
        )
    }

    suspend fun loadSentLikes() {
        val res = apiCall { api.getSentLikes() }
        @Suppress("UNCHECKED_CAST")
        val entries = (res as List<Map<String, Any?>>).toLikeEntries()
        sentLikes.clear()
        sentLikes.addAll(entries.mapNotNull { entry -> entry.item?.toClothingItem()?.let { SentLike(it) } })
    }

    fun loadMessages(matchId: Int) {
        val match = matches.firstOrNull { it.id == matchId } ?: return
        scope.launch {
            try {
                val res = apiCall { api.getMessages(matchId.toLong()) }
                val msgs = extractMessages(res).map { it.toChatMessage() }
                match.messages.clear()
                match.messages.addAll(msgs)
                api.markMessagesRead(matchId.toLong())
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    // ── 내 옷 추가/삭제 ───────────────────────────────────────────────
    fun addMyItem(item: ClothingItem, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val body = CreateItemRequest(
                    name = item.name,
                    brand = item.brand.ifBlank { null },
                    category = item.category.name,
                    size = item.size.ifBlank { null },
                    heightFit = item.heightFit.ifBlank { null },
                    condition = item.condition,
                    description = item.description.ifBlank { null },
                    imageUrl = item.image,
                    wearingImageUrl = item.wearingImage.ifBlank { null },
                    tags = item.tags.ifEmpty { null }
                )
                val res = apiCall { api.createItem(body) }
                myCloset.add(0, res.toClothingItem(fallbackUser = myProfile))
                onResult(true)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    fun removeMyItem(itemId: Int, onResult: (RemoveResult) -> Unit) {
        scope.launch {
            try {
                apiCall { api.deleteItem(itemId.toLong()) }
                myCloset.removeAll { it.id == itemId }
                receivedLikes.removeAll { it.myItem.id == itemId }
                onResult(RemoveResult.Removed)
            } catch (e: ApiException) {
                if (e.errorBody?.code == "ITEM_IN_EXCHANGE") {
                    onResult(RemoveResult.BlockedInExchange)
                } else {
                    errorMessage = e.message
                    onResult(RemoveResult.NotFound)
                }
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(RemoveResult.NotFound)
            }
        }
    }

    // ── 좋아요 / 패스 ─────────────────────────────────────────────────
    fun likeItem(targetItem: ClothingItem, onResult: (MatchResult) -> Unit = {}) {
        scope.launch {
            try {
                val res = apiCall { api.toggleLike(targetItem.id.toLong()) }
                if (res.liked) {
                    if (sentLikes.none { it.item.id == targetItem.id }) {
                        sentLikes.add(0, SentLike(targetItem))
                    }
                    val exchangeId = res.exchange?.id
                    if (res.matched && exchangeId != null) {
                        val full = apiCall { api.getExchange(exchangeId) }
                        val match = full.toMatchItem()
                        matches.removeAll { it.id == match.id }
                        matches.add(0, match)
                        pendingMatchNotifications.add(match)
                        onResult(MatchResult.Matched(match))
                        return@launch
                    }
                    onResult(MatchResult.Liked)
                } else {
                    sentLikes.removeAll { it.item.id == targetItem.id }
                    onResult(MatchResult.Unliked)
                }
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(MatchResult.Liked)
            }
        }
    }

    /** 발견 탭 패스(왼쪽 스와이프/X버튼) — 서버에 기록해서 다음 discover 조회부터 제외되게 함 */
    fun passItem(itemId: Int) {
        scope.launch {
            try {
                api.dislikeItem(itemId.toLong())
            } catch (e: Exception) {
                // 패스는 실패해도 로컬 UX엔 영향 없으므로 조용히 무시하고 로그만 상태에 남김
                errorMessage = e.message
            }
        }
    }

    // ── 교환 상태 전이 ────────────────────────────────────────────────
    fun confirmExchange(matchId: Int) {
        scope.launch {
            try {
                apiCall { api.confirmExchange(matchId.toLong()) }
                val idx = matches.indexOfFirst { it.id == matchId }
                if (idx >= 0) matches[idx] = matches[idx].copy(status = ExchangeStatus.CONFIRMED)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun startShipping(matchId: Int) {
        scope.launch {
            try {
                apiCall { api.shipExchange(matchId.toLong()) }
                val idx = matches.indexOfFirst { it.id == matchId }
                if (idx >= 0) matches[idx] = matches[idx].copy(status = ExchangeStatus.SHIPPING)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun completeExchange(matchId: Int) {
        scope.launch {
            try {
                apiCall { api.completeExchange(matchId.toLong()) }
                val idx = matches.indexOfFirst { it.id == matchId }
                if (idx >= 0) {
                    val match = matches[idx]
                    matches[idx] = match.copy(status = ExchangeStatus.COMPLETE)
                    myCloset.removeAll { it.id == match.myItem.id }
                }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    // ── 채팅 ──────────────────────────────────────────────────────────
    fun sendMessage(matchId: Int, text: String) {
        if (text.isBlank()) return
        val match = matches.firstOrNull { it.id == matchId } ?: return
        scope.launch {
            try {
                val res = apiCall { api.sendMessage(matchId.toLong(), SendMessageRequest(text.trim())) }
                match.messages.add(res.toChatMessage())
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun isChatOpen(status: ExchangeStatus) =
        status == ExchangeStatus.MATCHED ||
        status == ExchangeStatus.CONFIRMED ||
        status == ExchangeStatus.SHIPPING

    // ── 상대 옷장 조회 (다이얼로그 열 때 비동기 로드) ───────────────────
    fun loadUserCloset(userId: Int, onResult: (List<ClothingItem>) -> Unit) {
        scope.launch {
            try {
                val res = apiCall { api.getUserCloset(userId.toLong()) }
                onResult(extractItems(res).map { it.toClothingItem() })
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(emptyList())
            }
        }
    }

    // ── 지연 알림 소비 ───────────────────────────────────────────────
    fun consumePendingNotification(): MatchItem? =
        if (pendingMatchNotifications.isNotEmpty()) pendingMatchNotifications.removeAt(0) else null

    // ── 헬퍼 ─────────────────────────────────────────────────────────
    fun isItemInExchange(itemId: Int): Boolean =
        matches.any { it.myItem.id == itemId && it.status != ExchangeStatus.COMPLETE }

    fun isItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.myItem.id == itemId }

    fun isTheirItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.theirItem.id == itemId }

    // ── 로그아웃 ─────────────────────────────────────────────────────
    fun logout() {
        scope.launch {
            val refresh = TokenManager.refreshToken
            if (!refresh.isNullOrBlank()) {
                runCatching { api.logout(TokenRefreshRequest(refresh)) }
            }
            TokenManager.clear()
            myProfile = null
            myCloset.clear()
            discoverCards.clear()
            matches.clear()
            receivedLikes.clear()
            sentLikes.clear()
            pendingMatchNotifications.clear()
        }
    }
}

sealed class MatchResult {
    object Liked        : MatchResult()
    object Unliked      : MatchResult()
    object AlreadyLiked : MatchResult()
    data class Matched(val match: MatchItem) : MatchResult()
}

sealed class RemoveResult {
    object Removed          : RemoveResult()
    object BlockedInExchange: RemoveResult()
    object NotFound         : RemoveResult()
}
