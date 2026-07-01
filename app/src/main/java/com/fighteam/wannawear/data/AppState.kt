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
 * 실제 WannaWear 백엔드(팀 API_SPEC.md 기준)와 통신하는 전역 상태.
 * - 발견 탭엔 위치 파라미터가 없다 (택배 교환이라 실시간 위치 불필요).
 * - 좋아요 매칭 판별은 서버가 전담(POST /likes/{itemId})하고 결과(ExchangeSummary)를 그대로 반영한다.
 * - confirm/ship/complete는 "양쪽이 각자 호출"해야 다음 상태로 넘어가므로, 액션 후엔 항상
 *   상세를 재조회해서 서버가 알려주는 진짜 상태(my / their 플래그)로 동기화한다.
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
    val addresses: SnapshotStateList<Address> = mutableStateListOf()
    val pendingMatchNotifications: SnapshotStateList<MatchItem> = mutableStateListOf()

    val hasDefaultAddress: Boolean get() = addresses.any { it.isDefault }

    // ── 공통 API 호출 래퍼 ────────────────────────────────────────────
    // success=false 면 예외로 던지고, data가 없는 성공 응답(로그아웃/삭제류)은 null을 그대로 돌려준다.
    private suspend fun <T> apiCall(block: suspend () -> ApiResponse<T>): T? {
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
        if (!response.success) throw ApiException(response.error)
        return response.data
    }

    /** data가 반드시 있어야 하는 호출용 (없으면 서버 이상 응답으로 간주) */
    private suspend fun <T> apiCallRequired(block: suspend () -> ApiResponse<T>): T =
        apiCall(block) ?: throw ApiException(ErrorBody(code = "EMPTY_RESPONSE", message = "서버 응답이 비어있어요"))

    // ── 초기 데이터 로드 (로그인 직후 1회 호출) ───────────────────────
    fun loadInitialData() {
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
                launch { runCatching { loadDiscoverFeed() } }
                launch { runCatching { loadExchanges() } }
                launch { runCatching { loadReceivedLikes() } }
                launch { runCatching { loadSentLikes() } }
                launch { runCatching { loadAddresses() } }
            }
            // ⚠️ 테스트용 목업 — MockTestData.kt의 ENABLE_MOCK_TEST_DATA를 false로 바꾸면 꺼짐
            if (ENABLE_MOCK_TEST_DATA) seedMockTestData()
            isLoading = false
        }
    }

    suspend fun loadMyProfile() {
        val res = apiCallRequired { api.getMyProfile() }
        myProfile = res.toUser()
    }

    suspend fun loadMyCloset() {
        val res = apiCallRequired { api.getMyItems() }
        myCloset.clear()
        myCloset.addAll(res.items.map { it.toClothingItem(fallbackUser = myProfile) })
    }

    /** category: TOP/BOTTOM/OUTER/DRESS/SHOES 등 자유 문자열, null이면 전체 */
    suspend fun loadDiscoverFeed(category: String? = null) {
        val res = apiCallRequired { api.discoverItems(category) }
        discoverCards.clear()
        discoverCards.addAll(res.items.map { it.toClothingItem() })
    }

    /** "새 아이템 보기" 버튼 — 서버가 이미 좋아요/싫어요 한 아이템은 알아서 제외해준다 */
    fun refreshDiscoverFeed(category: String? = null) {
        scope.launch {
            try {
                loadDiscoverFeed(category)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    suspend fun loadExchanges() {
        val res = apiCallRequired { api.getExchanges() }
        matches.clear()
        matches.addAll(res.exchanges.map { it.toMatchItem() })
    }

    suspend fun loadReceivedLikes() {
        val res = apiCallRequired { api.getReceivedLikes() }
        receivedLikes.clear()
        receivedLikes.addAll(
            res.mapNotNull { entry ->
                val item = entry.toItem?.toClothingItem() ?: return@mapNotNull null
                val fromUser = entry.fromUser?.toUser() ?: return@mapNotNull null
                ReceivedLike(fromUser = fromUser, myItem = item)
            }
        )
    }

    suspend fun loadSentLikes() {
        val res = apiCallRequired { api.getSentLikes() }
        sentLikes.clear()
        sentLikes.addAll(res.mapNotNull { entry -> entry.item?.toClothingItem()?.let { SentLike(it) } })
    }

    suspend fun loadAddresses() {
        val res = apiCall { api.getAddresses() } ?: emptyList()
        addresses.clear()
        addresses.addAll(res.map { it.toAddress() })
    }

    /** 목업 데이터를 발견 탭/받은 관심 탭에 살짝 섞어 넣는다. 이미 들어가 있으면 중복 추가 안 함. */
    private fun seedMockTestData() {
        val newDiscover = mockDiscoverItems.filter { mock -> discoverCards.none { it.id == mock.id } }
        discoverCards.addAll(newDiscover)
        val newLikes = mockReceivedLikes(myProfile).filter { mock ->
            receivedLikes.none { it.fromUser.id == mock.fromUser.id && it.myItem.id == mock.myItem.id }
        }
        receivedLikes.addAll(0, newLikes)
    }

    fun loadMessages(matchId: Int) {
        val match = matches.firstOrNull { it.id == matchId } ?: return
        scope.launch {
            try {
                val res = apiCallRequired { api.getMessages(matchId.toLong()) }
                val msgs = res.messages.map { it.toChatMessage() }
                match.messages.clear()
                match.messages.addAll(msgs)
                apiCall { api.markMessagesRead(matchId.toLong()) }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /** 채팅/채팅소켓에서 새 메시지를 받았을 때 로컬 목록에 중복없이 반영 */
    fun appendIncomingMessage(matchId: Int, message: ChatMessage) {
        val match = matches.firstOrNull { it.id == matchId } ?: return
        if (match.messages.none { it.id == message.id }) {
            match.messages.add(message)
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
                val res = apiCallRequired { api.createItem(body) }
                myCloset.add(0, res.toClothingItem(fallbackUser = myProfile))
                onResult(true)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    fun removeMyItem(itemId: Int, onResult: (RemoveResult) -> Unit) {
        if (itemId < 0) { // 목업 "내 옷"은 로컬에서만 지운다
            myCloset.removeAll { it.id == itemId }
            receivedLikes.removeAll { it.myItem.id == itemId }
            onResult(RemoveResult.Removed)
            return
        }
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
        // ⚠️ 목업 아이템(id<0)은 서버에 없는 가짜 데이터라 로컬에서만 흉내낸다 (매칭은 발생 안 함)
        if (targetItem.id < 0) {
            if (sentLikes.none { it.item.id == targetItem.id }) sentLikes.add(0, SentLike(targetItem))
            discoverCards.removeAll { it.id == targetItem.id }
            onResult(MatchResult.Liked)
            return
        }
        scope.launch {
            try {
                val res = apiCallRequired { api.toggleLike(targetItem.id.toLong()) }
                if (res.liked) {
                    if (sentLikes.none { it.item.id == targetItem.id }) {
                        sentLikes.add(0, SentLike(targetItem))
                    }
                    discoverCards.removeAll { it.id == targetItem.id }
                    val summary = res.exchange
                    if (res.matched && summary != null) {
                        // ✅ 응답 자체가 완전히 타입화되어 있어(partnerItem/myItem/partner) 별도 재조회 불필요
                        val match = summary.toMatchItem()
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
            } catch (e: ApiException) {
                // 본인 아이템에 좋아요 시도(CANNOT_LIKE_OWN_ITEM) 등은 조용히 무시
                errorMessage = e.message
                onResult(MatchResult.Liked)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(MatchResult.Liked)
            }
        }
    }

    /** 발견 탭 패스(왼쪽 스와이프/X버튼) — 서버에 기록해서 다음 discover 조회부터 제외되게 함 */
    fun passItem(itemId: Int) {
        if (itemId < 0) return // 목업 아이템은 서버에 기록할 필요 없음
        scope.launch {
            try {
                apiCall { api.dislikeItem(itemId.toLong()) }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    // ── 교환 상태 전이 ────────────────────────────────────────────────
    // confirm/ship/complete는 "각자 자기 쪽만" 호출하는 방식이라, 액션 후엔 항상 상세를
    // 재조회해서 서버가 판단한 진짜 상태(양쪽 플래그 + status)로 동기화한다.

    fun confirmExchange(matchId: Int, onResult: (ConfirmResult) -> Unit = {}) {
        scope.launch {
            try {
                apiCall { api.confirmExchange(matchId.toLong()) }
                refreshExchangeDetail(matchId)
                onResult(ConfirmResult.Success)
            } catch (e: ApiException) {
                if (e.errorBody?.code == "NOT_FOUND") {
                    onResult(ConfirmResult.NeedsAddress)
                } else {
                    errorMessage = e.message
                    onResult(ConfirmResult.Error(e.message ?: "교환 확정에 실패했어요"))
                }
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(ConfirmResult.Error(e.message ?: "교환 확정에 실패했어요"))
            }
        }
    }

    fun startShipping(matchId: Int) {
        scope.launch {
            try {
                apiCall { api.shipExchange(matchId.toLong()) }
                refreshExchangeDetail(matchId)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun completeExchange(matchId: Int) {
        scope.launch {
            try {
                apiCall { api.completeExchange(matchId.toLong()) }
                refreshExchangeDetail(matchId)
                val match = matches.firstOrNull { it.id == matchId }
                if (match != null && match.status == ExchangeStatus.COMPLETE) {
                    myCloset.removeAll { it.id == match.myItem.id }
                }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /** MATCHED/CONFIRMED 상태에서만 가능. 참여자 아무나 호출해도 즉시 취소됨(상대 동의 불필요) */
    fun cancelExchange(matchId: Int, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                apiCall { api.cancelExchange(matchId.toLong()) }
                val idx = matches.indexOfFirst { it.id == matchId }
                if (idx >= 0) {
                    matches[idx] = matches[idx].copy(status = ExchangeStatus.CANCELLED, cancelledByMe = true)
                }
                onResult(true)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    /** 액션(confirm/ship/complete) 이후 서버의 진짜 상태로 동기화 */
    private suspend fun refreshExchangeDetail(matchId: Int) {
        try {
            val res = apiCallRequired { api.getExchange(matchId.toLong()) }
            val updated = res.toMatchItem()
            val idx = matches.indexOfFirst { it.id == matchId }
            if (idx >= 0) {
                matches[idx] = updated.copy(messages = matches[idx].messages)
            } else {
                matches.add(0, updated)
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    // ── 주소 ─────────────────────────────────────────────────────────
    fun addAddress(
        address1: String,
        recipient: String? = null,
        postalCode: String? = null,
        label: String? = null,
        isDefault: Boolean = true,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (address1.isBlank()) { onResult(false); return }
        scope.launch {
            try {
                val res = apiCallRequired {
                    api.addAddress(
                        AddressRequest(
                            label = label, recipient = recipient, postalCode = postalCode,
                            address1 = address1, isDefault = isDefault
                        )
                    )
                }
                if (isDefault) {
                    // 서버가 기존 기본 주소를 자동 해제하므로 로컬도 동일하게 반영
                    for (i in addresses.indices) addresses[i] = addresses[i].copy(isDefault = false)
                }
                addresses.add(0, res.toAddress())
                onResult(true)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    fun deleteAddress(addressId: Int) {
        scope.launch {
            try {
                apiCall { api.deleteAddress(addressId.toLong()) }
                addresses.removeAll { it.id == addressId }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    // ── 채팅 (REST 전송 — 실시간 수신은 ChatSocketManager 사용 권장) ───
    fun sendMessage(matchId: Int, text: String) {
        if (text.isBlank()) return
        val match = matches.firstOrNull { it.id == matchId } ?: return
        scope.launch {
            try {
                val res = apiCallRequired { api.sendMessage(matchId.toLong(), SendMessageRequest(text.trim())) }
                appendIncomingMessage(matchId, res.toChatMessage())
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
        if (userId < 0) { // 목업 유저는 서버에 없으니 목업 아이템 중에서만 필터링
            onResult(mockDiscoverItems.filter { it.user.id == userId })
            return
        }
        scope.launch {
            try {
                val res = apiCallRequired { api.getUserCloset(userId.toLong()) }
                onResult(res.items.map { it.toClothingItem() })
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
        matches.any { it.myItem.id == itemId && it.status != ExchangeStatus.COMPLETE && it.status != ExchangeStatus.CANCELLED }

    fun isItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.myItem.id == itemId }

    fun isTheirItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.theirItem.id == itemId }

    // ── 로그아웃 ─────────────────────────────────────────────────────
    fun logout() {
        scope.launch {
            val refresh = TokenManager.refreshToken
            if (!refresh.isNullOrBlank()) {
                runCatching { apiCall { api.logout(TokenRefreshRequest(refresh)) } }
            }
            TokenManager.clear()
            myProfile = null
            myCloset.clear()
            discoverCards.clear()
            matches.clear()
            receivedLikes.clear()
            sentLikes.clear()
            addresses.clear()
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

sealed class ConfirmResult {
    object Success     : ConfirmResult()
    object NeedsAddress: ConfirmResult()
    data class Error(val message: String) : ConfirmResult()
}
