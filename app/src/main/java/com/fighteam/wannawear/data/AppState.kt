package com.fighteam.wannawear.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
    // 전역 안내 토스트(에러 아님) — 예: 실시간 교환 수정요청 알림. NavGraph에서 관찰해서 표시.
    var infoMessage by mutableStateOf<String?>(null)

    val myCloset: SnapshotStateList<ClothingItem> = mutableStateListOf()
    val discoverCards: SnapshotStateList<ClothingItem> = mutableStateListOf()
    val matches: SnapshotStateList<MatchItem> = mutableStateListOf()
    val receivedLikes: SnapshotStateList<ReceivedLike> = mutableStateListOf()
    val sentLikes: SnapshotStateList<SentLike> = mutableStateListOf()
    val addresses: SnapshotStateList<Address> = mutableStateListOf()
    val pendingMatchNotifications: SnapshotStateList<MatchItem> = mutableStateListOf()
    // ⚠️ 2026-07-04 추가, v0.1 설계 제안 단계(match-room-spec.md) — 백엔드 미배포.
    val matchRooms: SnapshotStateList<MatchRoom> = mutableStateListOf()

    val hasDefaultAddress: Boolean get() = addresses.any { it.isDefault }

    // ⚠️ 백엔드 버그 우회: GET /api/exchanges 및 GET /api/exchanges/{id}가 myItem/theirItem을
    // 요청자가 누구든 상관없이 고정된 값으로 내려줌 (A로 조회해도 B로 조회해도 완전히 동일한 값 —
    // 실측 확인함). 상태 플래그(myConfirmed 등)는 요청자 기준으로 정확히 내려오니 그건 그대로 두고,
    // 아이템 객체만 내 user id 기준으로 다시 맞춘다. POST /api/likes 응답(매칭 성사 시점)은 이미
    // 정확해서 이 보정을 걸어도 조건이 안 맞아 그냥 통과한다(무해함).
    private fun MatchItem.fixItemPerspective(): MatchItem {
        val myId = myUserId
        return if (myId > 0 && myItem.user.id != myId && theirItem.user.id == myId) {
            copy(myItem = theirItem, theirItem = myItem)
        } else this
    }

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
            // 로그인 전에 FCM 토큰이 먼저 발급됐을 수 있어서, 로그인 성공 시점에 한 번 더 등록 시도
            TokenManager.deviceToken?.let { registerDeviceToken(it) }
            refreshUnreadNotificationCount()
            isLoading = false
        }
    }

    suspend fun loadMyProfile() {
        val res = apiCallRequired { api.getMyProfile() }
        myProfile = res.toUser()
    }

    /** 프로필 수정 화면에서 최신 bio를 미리 채워넣기 위한 원본 응답 조회 (User 모델엔 bio가 없음) */
    suspend fun fetchMyProfileRaw(): UserProfileResponse = apiCallRequired { api.getMyProfile() }

    /** nickname/bio/avatarUrl 중 null이 아닌 값만 서버에 반영된다 (PATCH /api/users/me) */
    fun updateProfile(
        nickname: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            try {
                val res = apiCallRequired {
                    api.updateProfile(UpdateProfileRequest(nickname = nickname, bio = bio, avatarUrl = avatarUrl))
                }
                myProfile = res.toUser()
                onResult(true)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    suspend fun loadMyCloset() {
        val res = apiCallRequired { api.getMyItems() }
        // ⚠️ 서버 /api/items/me?status=all 은 이미 교환 완료(exchanged)된 아이템도 섞어서 줄 수 있고,
        //    ItemResponse엔 그걸 구분할 필드 자체가 없다. 그래서 내가 아는 매칭 정보(COMPLETE 상태)
        //    기준으로 이미 나간 옷은 옷장에서 걸러낸다 — 안 그러면 완료된 옷이 다시 "교환중"으로
        //    남아있는 것처럼 보이고, 심지어 그 옷으로 또 매칭이 생기는 버그로 이어진다.
        val completedMyItemIds = matches
            .filter { it.status == ExchangeStatus.COMPLETE }
            .map { it.myItem.id }
            .toSet()
        myCloset.clear()
        myCloset.addAll(
            res.items.map { it.toClothingItem(fallbackUser = myProfile) }
                .filterNot { it.id in completedMyItemIds }
        )
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

    // ⚠️ 실시간 소켓/푸시가 없는 화면(매칭/옷장 등)의 최소한의 "새로고침" 수단.
    //    탭 전환 시(LaunchedEffect) 조용히 호출 — 실패해도 화면은 그냥 이전 데이터 유지.
    fun refreshExchanges()     { scope.launch { runCatching { loadExchanges() } } }
    fun refreshMyCloset()      { scope.launch { runCatching { loadMyCloset() } } }
    fun refreshReceivedLikes() { scope.launch { runCatching { loadReceivedLikes() } } }
    fun refreshSentLikes()     { scope.launch { runCatching { loadSentLikes() } } }

    suspend fun loadExchanges() {
        val res = apiCallRequired { api.getExchanges() }
        matches.clear()
        matches.addAll(res.exchanges.map { it.toMatchItem().fixItemPerspective() })
        flushPendingReviews()
        autoCancelLeftoverMatchesForCompletedItems()
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
                val res = apiCallRequired { api.toggleLike(targetItem.id.toLong()) }
                if (res.liked) {
                    if (sentLikes.none { it.item.id == targetItem.id }) {
                        sentLikes.add(0, SentLike(targetItem))
                    }
                    discoverCards.removeAll { it.id == targetItem.id }
                    val summary = res.exchange
                    if (res.matched && summary != null) {
                        // ✅ 응답 자체가 완전히 타입화되어 있어(partnerItem/myItem/partner) 별도 재조회 불필요
                        val match = summary.toMatchItem().fixItemPerspective()
                        matches.removeAll { it.id == match.id }
                        matches.add(0, match)
                        pendingMatchNotifications.add(match)
                        onResult(MatchResult.Matched(match))
                        return@launch
                    }
                    // ⚠️ v1.0 MatchRoom 스펙: matched=false여도 roomId가 있으면 "이미 진행 중인
                    // 매칭방의 선택 목록에 조용히 추가됨"이라는 뜻(문서 10절). 별도 팝업 없이,
                    // 그 방을 이미 알고 있다면 백그라운드로 최신화해둔다.
                    if (res.roomId != null) {
                        val roomId = res.roomId.toClientId()
                        if (matchRooms.any { it.id == roomId }) {
                            scope.launch { runCatching { refreshMatchRoomDetail(roomId) } }
                        }
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

    /** 발견 탭 패스(왼쪽 스와이프/X버튼) — 서버에 기록해서 다음 discover 조회부터 제외되게 함.
     *  ⚠️ 2026-07-04: 백엔드가 "dislike가 실제로 좋아요로 저장되던" 버그를 고쳐서 재검증
     *  완료(sentLikes/receivedLikes에 더 이상 안 뜸) — 다시 정상 호출하도록 되돌림.
     */
    fun passItem(itemId: Int) {
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
                    // ⚠️ 같은 아이템으로 걸려있던 다른 매칭(백엔드 중복 매칭 버그로 생긴 것들)을
                    //    사용자가 수동으로 안 지워도 여기서 자동으로 정리한다.
                    autoCancelLeftoverMatchesForCompletedItems()
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
            val updated = res.toMatchItem().fixItemPerspective()
            val idx = matches.indexOfFirst { it.id == matchId }
            if (idx >= 0) {
                matches[idx] = updated.copy(messages = matches[idx].messages)
            } else {
                matches.add(0, updated)
            }
            flushPendingReviews()
            autoCancelLeftoverMatchesForCompletedItems()
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    // ⚠️ 백엔드 버그(같은 아이템으로 매칭이 여러 건 성사될 수 있음) 대응: 어떤 아이템이 이미
    //    COMPLETE(교환 완료)됐는데도 같은 아이템에 걸린 다른 매칭이 MATCHED/CONFIRMED/SHIPPING
    //    상태로 남아있으면 자동으로 취소한다. completeExchange 직후, 그리고 loadExchanges/
    //    refreshExchangeDetail로 매칭 목록을 새로 받아올 때마다 호출해서, 다른 세션에서 이미
    //    완료된 경우까지 놓치지 않고 정리한다. 사용자가 수동으로 하나씩 취소할 필요 없음.
    private fun autoCancelLeftoverMatchesForCompletedItems() {
        val activeStatuses = setOf(ExchangeStatus.MATCHED, ExchangeStatus.CONFIRMED, ExchangeStatus.SHIPPING)
        val completedItemIds = matches.filter { it.status == ExchangeStatus.COMPLETE }.map { it.myItem.id }.toSet()
        if (completedItemIds.isEmpty()) return
        matches
            .filter { it.status in activeStatuses && it.myItem.id in completedItemIds }
            .forEach { stale -> cancelExchange(stale.id) }
    }

    // ── MatchRoom (2026-07-04 추가, v1.0 확정 스펙 — 백엔드 구현/배포/E2E검증 완료) ──
    // ⚠️ match-room-spec.md v1.0 기준. roomId==exchangeId(같은 테이블/row 공유), 채팅도
    // 전용 엔드포인트(getMatchRoomMessages 등)로 확정됨.

    suspend fun loadMatchRooms() {
        val res = apiCallRequired { api.getMatchRooms() }
        matchRooms.clear()
        matchRooms.addAll(res.matchRooms.map { it.toMatchRoom() })
    }

    fun refreshMatchRooms() { scope.launch { runCatching { loadMatchRooms() } } }

    /** 선택 화면 진입 시 후보 목록(내가 좋아요한 상대 아이템들 / 참고용 상대가 좋아요한 내 아이템들) 조회 */
    fun loadMatchRoomCandidates(
        roomId: Int,
        onResult: (myCandidates: List<ClothingItem>, theirCandidates: List<ClothingItem>) -> Unit
    ) {
        scope.launch {
            try {
                val res = apiCallRequired { api.getMatchRoomCandidates(roomId.toLong()) }
                onResult(res.myCandidates.map { it.toClothingItem() }, res.theirCandidates.map { it.toClothingItem() })
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(emptyList(), emptyList())
            }
        }
    }

    /** 내 selected 목록 전체 교체 (SELECTING 상태에서만 가능).
     *  ⚠️ v1.0 확정: 이미 잠근 상태에서 다시 호출하면 내 잠금(myLockedSelection)이 서버에서
     *  자동으로 풀림 — 별도 "잠금 해제" API 없음(문서 6절). 응답을 그대로 반영하면 알아서 처리됨. */
    fun updateMatchRoomSelection(roomId: Int, itemIds: List<Int>, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val res = apiCallRequired {
                    api.updateMatchRoomSelection(roomId.toLong(), SelectionRequest(itemIds.map { it.toLong() }))
                }
                replaceMatchRoom(res.toMatchRoom())
                onResult(true)
            } catch (e: ApiException) {
                errorMessage = when (e.errorBody?.code) {
                    "ITEM_IN_EXCHANGE"          -> "이미 다른 방에서 진행 중인 아이템이 포함돼 있어요"
                    "ITEM_NOT_IN_CANDIDATES"    -> "선택할 수 없는 아이템이 포함돼 있어요"
                    "EXCHANGE_STATUS_INVALID"   -> "지금은 선택을 바꿀 수 없는 상태예요"
                    else -> e.message
                }
                onResult(false)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    /** 내 선택 잠금 — 양쪽 다 하면 MATCHED로 전환 */
    fun lockMatchRoomSelection(roomId: Int, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                apiCall { api.lockMatchRoomSelection(roomId.toLong()) }
                refreshMatchRoomDetail(roomId)
                onResult(true)
            } catch (e: ApiException) {
                errorMessage = when (e.errorBody?.code) {
                    "EMPTY_SELECTION"         -> "받고 싶은 옷을 하나 이상 골라주세요"
                    "EXCHANGE_STATUS_INVALID" -> "지금은 선택을 잠글 수 없는 상태예요"
                    else -> e.message
                }
                onResult(false)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    /** 배송지 확인 (기존 confirmExchange와 동일한 의미) */
    fun confirmMatchRoom(roomId: Int, onResult: (ConfirmResult) -> Unit = {}) {
        scope.launch {
            try {
                apiCall { api.confirmMatchRoom(roomId.toLong()) }
                refreshMatchRoomDetail(roomId)
                onResult(ConfirmResult.Success)
            } catch (e: ApiException) {
                if (e.errorBody?.code == "NOT_FOUND") {
                    onResult(ConfirmResult.NeedsAddress)
                } else {
                    errorMessage = e.message
                    onResult(ConfirmResult.Error(e.message ?: "배송지 확인에 실패했어요"))
                }
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(ConfirmResult.Error(e.message ?: "배송지 확인에 실패했어요"))
            }
        }
    }

    fun shipMatchRoom(roomId: Int) {
        scope.launch {
            try {
                apiCall { api.shipMatchRoom(roomId.toLong()) }
                refreshMatchRoomDetail(roomId)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun completeMatchRoom(roomId: Int) {
        scope.launch {
            try {
                apiCall { api.completeMatchRoom(roomId.toLong()) }
                refreshMatchRoomDetail(roomId)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /** SELECTING/MATCHED/CONFIRMED에서만 가능 */
    fun cancelMatchRoom(roomId: Int, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                apiCall { api.cancelMatchRoom(roomId.toLong()) }
                val idx = matchRooms.indexOfFirst { it.id == roomId }
                if (idx >= 0) matchRooms[idx] = matchRooms[idx].copy(status = MatchRoomStatus.CANCELLED)
                onResult(true)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    /** SHIPPING 이전 상태에서만 가능 — 상태를 SELECTING으로 롤백. 성공 시 서버가 채팅 커스텀
     *  메시지 + 상대방 토스트 + 알림을 알아서 발송해준다(문서 §6 기준). */
    fun requestMatchRoomModification(roomId: Int, proposedItemIds: List<Int>, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                apiCall {
                    api.requestMatchRoomModification(roomId.toLong(), ModificationRequest(proposedItemIds.map { it.toLong() }))
                }
                refreshMatchRoomDetail(roomId)
                onResult(true)
            } catch (e: ApiException) {
                errorMessage = when (e.errorBody?.code) {
                    "MODIFICATION_NOT_ALLOWED" -> "이미 발송된 교환은 수정 요청을 할 수 없어요"
                    else -> e.message
                }
                onResult(false)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    private suspend fun refreshMatchRoomDetail(roomId: Int) {
        try {
            val res = apiCallRequired { api.getMatchRoom(roomId.toLong()) }
            replaceMatchRoom(res.toMatchRoom())
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    private fun replaceMatchRoom(updated: MatchRoom) {
        val idx = matchRooms.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            matchRooms[idx] = updated.copy(messages = matchRooms[idx].messages)
        } else {
            matchRooms.add(0, updated)
        }
    }

    fun loadMatchRoomMessages(roomId: Int) {
        val room = matchRooms.firstOrNull { it.id == roomId } ?: return
        scope.launch {
            try {
                val res = apiCallRequired { api.getMatchRoomMessages(roomId.toLong()) }
                val msgs = res.messages.map { it.toChatMessage() }
                room.messages.clear()
                room.messages.addAll(msgs)
                apiCall { api.markMatchRoomMessagesRead(roomId.toLong()) }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun sendMatchRoomMessage(roomId: Int, text: String) {
        if (text.isBlank()) return
        val room = matchRooms.firstOrNull { it.id == roomId } ?: return
        scope.launch {
            try {
                val res = apiCallRequired { api.sendMatchRoomMessage(roomId.toLong(), SendMessageRequest(text.trim())) }
                appendIncomingRoomMessage(roomId, res.toChatMessage())
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun appendIncomingRoomMessage(roomId: Int, message: ChatMessage) {
        val room = matchRooms.firstOrNull { it.id == roomId } ?: return
        if (room.messages.none { it.id == message.id }) {
            room.messages.add(message)
        }
    }

    /** ChatSocketManager가 WS exchange_modification_requested 이벤트를 받으면 호출.
     *  화면과 무관하게 즉시 토스트를 띄우고, 방 최신 상태를 백그라운드로 재조회한다. */
    fun onModificationRequestedRealtime(roomId: Int, requesterName: String) {
        infoMessage = "${requesterName}님이 교환 수정을 요청했어요. 확인해보세요!"
        scope.launch { runCatching { refreshMatchRoomDetail(roomId) } }
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

    // ── 알림 (2026-07-02 추가) ────────────────────────────────────────
    val notifications: SnapshotStateList<NotificationItem> = mutableStateListOf()
    var unreadNotificationCount by mutableStateOf(0)
        private set
    private var notificationPage = 1
    var hasMoreNotifications by mutableStateOf(true)
        private set

    /** 알림 화면 진입 시 첫 페이지부터 새로 로드 */
    fun loadNotifications() {
        scope.launch {
            try {
                val res = apiCallRequired { api.getNotifications(page = 1) }
                notificationPage = 1
                notifications.clear()
                notifications.addAll(res.notifications.map { it.toNotificationItem() })
                hasMoreNotifications = res.hasMore
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /** 무한스크롤 다음 페이지 */
    fun loadMoreNotifications() {
        if (!hasMoreNotifications) return
        scope.launch {
            try {
                val nextPage = notificationPage + 1
                val res = apiCallRequired { api.getNotifications(page = nextPage) }
                notificationPage = nextPage
                notifications.addAll(res.notifications.map { it.toNotificationItem() })
                hasMoreNotifications = res.hasMore
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /** 앱 포그라운드 진입 시/주기적으로 호출해서 뱃지 숫자 갱신 (실시간 소켓 없음) */
    fun refreshUnreadNotificationCount() {
        scope.launch {
            runCatching { apiCallRequired { api.getUnreadNotificationCount() } }
                .onSuccess { unreadNotificationCount = it.count }
        }
    }

    fun markNotificationRead(notificationId: Int) {
        val idx = notifications.indexOfFirst { it.id == notificationId }
        if (idx >= 0 && !notifications[idx].isRead) {
            notifications[idx] = notifications[idx].copy(isRead = true)
            unreadNotificationCount = (unreadNotificationCount - 1).coerceAtLeast(0)
        }
        scope.launch {
            try {
                apiCall { api.markNotificationRead(notificationId.toLong()) }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun markAllNotificationsRead() {
        for (i in notifications.indices) notifications[i] = notifications[i].copy(isRead = true)
        unreadNotificationCount = 0
        scope.launch {
            try {
                apiCall { api.markAllNotificationsRead() }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /** FirebaseMessagingService.onNewToken()과 로그인 성공 직후 둘 다에서 호출됨 (중복 호출 무해) */
    fun registerDeviceToken(token: String) {
        if (token.isBlank()) return
        TokenManager.deviceToken = token
        if (!TokenManager.isLoggedIn) return // 로그인 전이면 서버 호출은 로그인 후 재시도로 넘김
        scope.launch {
            runCatching { apiCall { api.registerDeviceToken(DeviceTokenRequest(token = token, platform = "android")) } }
        }
    }

    /** 로그아웃 시 호출 — 이 기기로는 더 이상 푸시 안 받도록 서버에서 해제 */
    private fun unregisterDeviceToken() {
        val token = TokenManager.deviceToken ?: return
        scope.launch {
            runCatching { apiCall { api.unregisterDeviceToken(token) } }
        }
    }

    // ── 평점 (2026-07-02 추가, 2026-07-03 조기선택 지원) ────────────────
    // 서버는 status==COMPLETE(양쪽 다 수령확인)일 때만 리뷰 제출을 받아준다. 근데 유저 입장에선
    // "나는 이미 수령확인 눌렀는데" 상대가 누르기 전까진 평점을 못 남기는 게 불편해서, 내가 미리
    // 고른 점수를 로컬에 잠깐 들고 있다가 status가 COMPLETE로 바뀌는 순간(상대도 눌렀을 때)
    // 자동으로 서버에 제출한다. matchId -> 내가 고른 점수.
    val pendingReviewScores: SnapshotStateMap<Int, Int> = mutableStateMapOf()

    /** 리뷰 화면(별점 선택)에서 호출. 이미 COMPLETE면 즉시 제출, 아니면 로컬에 대기시켜둠 */
    fun stageOrSubmitReview(matchId: Int, score: Int, onResult: (Boolean) -> Unit = {}) {
        val match = matches.firstOrNull { it.id == matchId }
        if (match?.status == ExchangeStatus.COMPLETE) {
            submitReview(matchId, score, onResult)
        } else {
            pendingReviewScores[matchId] = score
            onResult(true) // 로컬 저장 자체는 성공 — 실제 제출은 양쪽 다 완료되면 자동으로 됨
        }
    }

    /** matches가 갱신될 때마다 호출: 대기 중이던 리뷰 중 COMPLETE로 바뀐 게 있으면 자동 제출 */
    private fun flushPendingReviews() {
        if (pendingReviewScores.isEmpty()) return
        val ready = pendingReviewScores.filterKeys { matchId ->
            matches.firstOrNull { it.id == matchId }?.status == ExchangeStatus.COMPLETE
        }
        ready.forEach { (matchId, score) ->
            pendingReviewScores.remove(matchId)
            submitReview(matchId, score)
        }
    }

    /** COMPLETE 상태에서만 성공. score 1~5. 코멘트 없음(스펙 확정) */
    fun submitReview(exchangeId: Int, score: Int, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                apiCallRequired { api.submitReview(exchangeId.toLong(), ReviewRequest(score = score)) }
                onResult(true)
            } catch (e: ApiException) {
                errorMessage = when (e.errorBody?.code) {
                    "REVIEW_ALREADY_SUBMITTED" -> "이미 평점을 남겼어요"
                    "EXCHANGE_STATUS_INVALID"  -> "교환이 완료된 후에만 평점을 남길 수 있어요"
                    else -> e.message
                }
                onResult(false)
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    /** 교환 상세 진입 시 평점 모달 노출 여부 판단용 */
    suspend fun getReviewStatus(exchangeId: Int): ReviewStatusResponse? =
        try {
            apiCallRequired { api.getReviewStatus(exchangeId.toLong()) }
        } catch (e: Exception) {
            null
        }

    // ── 검색 (2026-07-02 추가) ────────────────────────────────────────
    val searchResults: SnapshotStateList<ClothingItem> = mutableStateListOf()
    var isSearching by mutableStateOf(false)
        private set

    /** q가 비어있으면 서버가 400을 주므로 호출 전에 걸러낸다. discover와 달리 이미 스와이프한
     *  아이템도 결과에 포함됨(스펙 3절 — 검색은 의도적 재탐색으로 간주). */
    fun searchItems(query: String) {
        if (query.isBlank()) {
            searchResults.clear()
            return
        }
        scope.launch {
            isSearching = true
            try {
                val res = apiCallRequired { api.searchItems(query.trim()) }
                searchResults.clear()
                searchResults.addAll(res.items.map { it.toClothingItem() })
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isSearching = false
            }
        }
    }

    // ── 신고 (2026-07-03 추가, 2026-07-04 데모 전용으로 전환) ───────────
    // ⚠️ 백엔드에서 신고 기능을 만들 계획이 없어서, 실제 서버 호출 없이 로컬에서만 흉내낸다.
    //    reportedMatchIds는 AppState의 인메모리 상태라 앱 프로세스가 재시작되면(=재접속하면)
    //    자연스럽게 초기화됨 — 별도 영속 저장 안 함, 데모용 UX만 제공.
    //    표준 남용 방지책은 UI에서 그대로 유지: 정해진 사유 목록 중 필수 선택, 최종 확인 단계,
    //    교환 1건당 1회만 가능.
    val reportedMatchIds: SnapshotStateList<Int> = mutableStateListOf()

    fun canReport(matchId: Int): Boolean = matchId !in reportedMatchIds

    fun reportExchange(matchId: Int, reason: String, detail: String?, onResult: (Boolean) -> Unit = {}) {
        if (!canReport(matchId)) { onResult(false); return }
        // 실제 서버 호출 없음 — 데모: 로컬에서 즉시 "접수됨" 처리
        reportedMatchIds.add(matchId)
        onResult(true)
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

    /** 상대 프로필 요약(매너온도/총교환수) 조회 — 옷장 모아보기에서 특정 사람 선택 시 사용 */
    suspend fun getPublicProfile(userId: Int): PublicUserResponse? =
        try {
            apiCallRequired { api.getPublicProfile(userId.toLong()) }
        } catch (e: Exception) {
            null
        }

    // ── 상대 옷장 조회 (다이얼로그 열 때 비동기 로드) ───────────────────
    // ⚠️ 반환값 null = 불러오기 실패(네트워크/서버 에러), emptyList() = 진짜로 옷장이 비어있음.
    //    예전엔 실패도 emptyList()로 뭉뚱그려서 "옷장이 비어있다"고 잘못 보이는 문제가 있었음.
    fun loadUserCloset(userId: Int, onResult: (List<ClothingItem>?) -> Unit) {
        scope.launch {
            try {
                val res = apiCallRequired { api.getUserCloset(userId.toLong()) }
                onResult(res.items.map { it.toClothingItem() })
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(null)
            }
        }
    }

    // ── 지연 알림 소비 ───────────────────────────────────────────────
    fun consumePendingNotification(): MatchItem? =
        if (pendingMatchNotifications.isNotEmpty()) pendingMatchNotifications.removeAt(0) else null

    // ── 헬퍼 ─────────────────────────────────────────────────────────
    fun isItemInExchange(itemId: Int): Boolean =
        matches.any { it.myItem.id == itemId && it.status != ExchangeStatus.COMPLETE && it.status != ExchangeStatus.CANCELLED }

    // ⚠️ 이전엔 이 체크가 3곳(ClosetScreen 보낸관심/옷장모아보기, SearchScreen)에 각각 복붙돼있었고,
    //    전부 CANCELLED 제외를 빼먹어서 "취소된 교환"의 상대 아이템도 계속 "교환중"으로 보이는
    //    버그가 있었음. 공용 함수로 통일.
    fun isTheirItemInExchange(itemId: Int): Boolean =
        matches.any { it.theirItem.id == itemId && it.status != ExchangeStatus.COMPLETE && it.status != ExchangeStatus.CANCELLED }

    fun isItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.myItem.id == itemId }

    fun isTheirItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.theirItem.id == itemId }

    // ⚠️ 서버가 매칭 성사 후에도 GET /api/likes/received에서 그 항목을 안 지워줌(실측 확인) —
    //    그래서 이미 좋아요를 눌러서 매칭이 성사된 상대가 "받은 관심"에 계속 남아있는 문제가 있었음.
    //    CANCELLED는 제외 — 취소됐으면 다시 판단할 수 있게 "받은 관심"에 나와야 함.
    fun hasActiveOrCompletedExchangeWith(myItemId: Int, partnerUserId: Int): Boolean =
        matches.any { it.myItem.id == myItemId && it.partner.id == partnerUserId && it.status != ExchangeStatus.CANCELLED }

    // ── 로그아웃 ─────────────────────────────────────────────────────
    fun logout() {
        unregisterDeviceToken()
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
            notifications.clear()
            unreadNotificationCount = 0
            searchResults.clear()
            pendingReviewScores.clear()
            matchRooms.clear()
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
