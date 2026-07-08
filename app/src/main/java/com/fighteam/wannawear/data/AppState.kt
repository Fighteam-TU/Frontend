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
                // ⚠️ 2026-07-08 추가 — isItemInExchange/isTheirItemInExchange가 matchRooms 기준으로
                // 바뀌면서, 매칭 탭에 한 번도 안 들어가도 "교환중" 배지가 정확해야 하니 로그인
                // 직후부터 항상 최신 상태를 들고 있어야 한다.
                launch { runCatching { loadMatchRooms() } }
            }
            // 로그인 전에 FCM 토큰이 먼저 발급됐을 수 있어서, 로그인 성공 시점에 한 번 더 등록 시도
            TokenManager.deviceToken?.let { registerDeviceToken(it) }
            refreshUnreadNotificationCount()
            // ⚠️ 2026-07-08 추가 — 채팅방을 열 때만 소켓을 연결하던 걸 앱 전역 상시 연결로 바꿔서,
            // 채팅방 밖에 있어도 exchange_modification_requested 같은 실시간 브로드캐스트를 받게 함.
            ChatSocketManager.connectGlobal()
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
                // ⚠️ 2026-07-08 버그 수정: 서버 응답은 "최신순 정렬"(내림차순)로 오는데, 채팅 화면은
                // 옛날 메시지가 위/최근 메시지가 아래로 가는 시간순(오름차순)을 기대함 — 그대로
                // 쓰면 채팅방을 나갔다가 다시 들어올 때만(REST로 재로드될 때만) 순서가 뒤집혀 보임
                // (실시간 소켓 메시지는 한 개씩 끝에 append되니까 증상이 안 드러났음). reversed()로
                // 뒤집어서 항상 시간순으로 저장한다.
                val msgs = res.messages.map { it.toChatMessage() }.reversed()
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
                    // 매칭방의 선택 목록에 조용히 추가됨"이라는 뜻(문서 10절). 예전엔 로컬에 그 방을
                    // "이미 알고 있을 때만" 새로고침해서, 아직 matchRooms를 안 불러온 상태(예: 매칭
                    // 탭에 한 번도 안 들어간 경우)에서 좋아요를 누르면 방 목록에 새 방이 아예 안 뜨는
                    // 문제가 있었음 — 항상 조회해서 없으면 새로 추가, 있으면 갱신하도록 통일.
                    if (res.roomId != null) {
                        val roomId = res.roomId.toClientId()
                        scope.launch { runCatching { refreshMatchRoomDetail(roomId) } }
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

    /** SELECTING/MATCHED/CONFIRMED에서만 가능.
     *  ✅ 2026-07-08 추가: 취소해도 서로 좋아요가 남아있으면(다른 아이템들로) 매칭 자체가
     *  사라지는 게 아니라 새 방으로 이어져야 한다는 요청 반영. 서버는 방 취소 시 매칭 재확인을
     *  자동으로 안 돌리지만(실측), 좋아요를 껐다 켜면 서버가 매칭을 새로 만들어주는 걸 라이브로
     *  확인함 — 그 경로를 취소 직후 자동으로 한 번 트리거해서 "상대와 방은 사라졌는데 서로
     *  좋아요는 남아있는" 어정쩡한 상태를 해소한다. 이미 다른 활성 방이 있으면 건드리지 않음
     *  (상대당 방 1개 유지). 실패해도 조용히 무시 — 이건 부가 기능이라 취소 자체는 이미 끝난 뒤임. */
    fun cancelMatchRoom(roomId: Int, onResult: (Boolean) -> Unit = {}) {
        val room = matchRooms.firstOrNull { it.id == roomId }
        scope.launch {
            try {
                apiCall { api.cancelMatchRoom(roomId.toLong()) }
                val idx = matchRooms.indexOfFirst { it.id == roomId }
                if (idx >= 0) matchRooms[idx] = matchRooms[idx].copy(status = MatchRoomStatus.CANCELLED)
                onResult(true)
                room?.partner?.id?.let { recreateMatchIfMutualLikesRemain(it) }
            } catch (e: Exception) {
                errorMessage = e.message
                onResult(false)
            }
        }
    }

    /** 상대와 활성 방이 없는데도 서로 좋아요가 남아있으면, 좋아요 하나를 껐다 켜서 서버의 매칭
     *  재확인을 트리거한다(라이브 검증 완료 — 서버가 새 방을 만들어줌). */
    private fun recreateMatchIfMutualLikesRemain(partnerId: Int) {
        scope.launch {
            try {
                if (activeMatchRoomWith(partnerId) != null) return@launch // 이미 방이 있으면 건드리지 않음
                val myLikeOnPartnerItem = sentLikes.firstOrNull { it.item.user.id == partnerId } ?: return@launch
                val partnerStillLikesMine = receivedLikesForDisplay().any { it.fromUser.id == partnerId }
                if (!partnerStillLikesMine) return@launch

                val itemId = myLikeOnPartnerItem.item.id.toLong()
                apiCall { api.toggleLike(itemId) }              // 껐다
                val res = apiCallRequired { api.toggleLike(itemId) } // 다시 켜서 서버 매칭 체크 재트리거
                val summary = res.exchange
                if (res.matched && summary != null) {
                    val match = summary.toMatchItem().fixItemPerspective()
                    matches.removeAll { it.id == match.id }
                    matches.add(0, match)
                    pendingMatchNotifications.add(match)
                }
                if (res.roomId != null) refreshMatchRoomDetail(res.roomId.toClientId())
            } catch (_: Exception) {
                // 부가 기능 — 실패해도 사용자에게 별도 에러를 띄우지 않음(취소 자체는 이미 성공함)
            }
        }
    }

    /** SHIPPING 이전 상태에서만 가능 — 상태를 SELECTING으로 롤백. 성공 시 서버가 채팅 커스텀
     *  메시지 + 상대방 토스트 + 알림을 알아서 발송해준다(문서 §6 기준).
     *  ⚠️ 2026-07-08: 백엔드가 suggestedOfferItemIds 필드를 정식 지원하게 되어(문서 §12),
     *  기존에 채팅 텍스트로 우회 전달하던 "내가 제시하는 옷" 정보를 이제 정식 필드로 같이 보낸다. */
    fun requestMatchRoomModification(
        roomId: Int,
        proposedItemIds: List<Int>,
        suggestedOfferItemIds: List<Int> = emptyList(),
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            try {
                apiCall {
                    api.requestMatchRoomModification(
                        roomId.toLong(),
                        ModificationRequest(
                            proposedItemIds = proposedItemIds.map { it.toLong() },
                            suggestedOfferItemIds = suggestedOfferItemIds.map { it.toLong() }
                        )
                    )
                }
                refreshMatchRoomDetail(roomId)
                onResult(true)
            } catch (e: ApiException) {
                errorMessage = when (e.errorBody?.code) {
                    "MODIFICATION_NOT_ALLOWED" -> e.errorBody.message ?: "지금은 수정 요청을 할 수 없는 상태예요"
                    "ITEM_NOT_IN_CANDIDATES"   -> e.errorBody.message ?: "제시하는 아이템은 본인 소유의 아이템만 가능해요"
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
                // ⚠️ 2026-07-08 버그 수정: 서버가 "최신순 정렬"(내림차순)로 내려주는데 그대로 써서,
                // 채팅방을 나갔다가 다시 들어오면(REST 재로드 시) 최근 메시지가 위쪽에 뜨는 순서
                // 뒤집힘 버그가 있었음 — loadMessages와 동일한 원인/수정.
                val msgs = res.messages.map { it.toChatMessage() }.reversed()
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

    // ⚠️ 2026-07-08 재설계 — 사용자 리포트로 확인된 버그: isItemInExchange/isTheirItemInExchange가
    // matches(Exchange 단일쌍 모델)를 기준으로 판정했는데, 이 모델의 myItem/theirItem은 방이
    // "처음 생성될 때"의 아이템 쌍에 고정되고 이후 매칭룸(N:M)에서 선택이 바뀌어도 안 따라옴
    // (roomId==exchangeId로 같은 테이블이지만, Exchange API는 그 시점의 대표 아이템만 보여주는
    // legacy 뷰). 그래서 방 선택에서 뺀 아이템(A)은 영원히 "교환중"으로 남고, 새로 넣은 아이템
    // (B)은 이 로직에 아예 안 잡히는 문제가 있었음 — matchRooms(실시간 선택 상태)를 기준으로
    // 다시 판정한다. 겸사겸사 요청사항 반영: "교환중"(등록한 사람 화면의 배지) 기준 시점도
    // 매칭 성사가 아니라 실제 발송(SHIPPING) 이후로 미룸 — 그 전까진 언제든 선택이 바뀔 수
    // 있으니까 너무 이르게 막을 필요 없음.
    // ⚠️ 다른 사용자에게 그 옷이 보이는지/좋아요 가능한지는 서버의 discover 피드 필터링에 달려
    // 있어서 프론트가 직접 못 고침 — 같은 기준(발송 이후부터)으로 서버도 맞춰달라고 backend-
    // 전달사항.md에 요청함.
    private val inExchangeRoomStatuses = setOf(MatchRoomStatus.SHIPPING, MatchRoomStatus.COMPLETE)

    fun isItemInExchange(itemId: Int): Boolean =
        matchRooms.any { room -> room.status in inExchangeRoomStatuses && room.theirWantList.any { it.id == itemId } }

    // ⚠️ 이전엔 이 체크가 3곳(ClosetScreen 보낸관심/옷장모아보기, SearchScreen)에 각각 복붙돼있었고,
    //    전부 CANCELLED 제외를 빼먹어서 "취소된 교환"의 상대 아이템도 계속 "교환중"으로 보이는
    //    버그가 있었음. 공용 함수로 통일.
    fun isTheirItemInExchange(itemId: Int): Boolean =
        matchRooms.any { room -> room.status in inExchangeRoomStatuses && room.myWantList.any { it.id == itemId } }

    fun isItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.myItem.id == itemId }

    fun isTheirItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.theirItem.id == itemId }

    /** 옷장 모아보기에서 아이템을 숨길지 판단 — SHIPPING 이상(양쪽 다 배송 시작)이면 숨김.
     *  MATCHED/CONFIRMED까지는 계속 보여줘서(하트 토글 등) 다른 옷도 둘러볼 수 있게 한다. */
    fun isTheirItemShippedOrCompleted(itemId: Int): Boolean =
        matches.any {
            it.theirItem.id == itemId && (it.status == ExchangeStatus.SHIPPING || it.status == ExchangeStatus.COMPLETE)
        }

    // ⚠️ 서버가 매칭 성사 후에도 GET /api/likes/received에서 그 항목을 안 지워줌(실측 확인).
    //    처음엔 "매칭되면 바로 숨김" 처리했었는데, 그러면 매칭된 사람의 옷장을 더 못 보게 돼서
    //    (하트 토글로 다른 옷도 둘러보는 메리트가 사라짐) — "양쪽 다 배송 시작(SHIPPING) 이상"
    //    일 때만 숨기도록 완화함. 그 전까지는 "받은 관심"에 남아있되 매칭중 표시만 해준다.
    //    CANCELLED는 당연히 제외 — 취소됐으면 다시 판단할 수 있게 계속 나와야 함.
    fun hasActiveOrCompletedExchangeWith(myItemId: Int, partnerUserId: Int): Boolean =
        matches.any {
            it.myItem.id == myItemId && it.partner.id == partnerUserId &&
                (it.status == ExchangeStatus.SHIPPING || it.status == ExchangeStatus.COMPLETE)
        }

    /** "받은 관심" 카드에 "매칭중" 배지를 보여줄지 판단용 — SHIPPING 이전이면 true.
     *  기존 Exchange뿐 아니라 활성 매칭룸(theirWantList에 이 아이템 포함)도 매칭중으로 본다. */
    fun hasPendingExchangeWith(myItemId: Int, partnerUserId: Int): Boolean =
        matches.any {
            it.myItem.id == myItemId && it.partner.id == partnerUserId &&
                (it.status == ExchangeStatus.MATCHED || it.status == ExchangeStatus.CONFIRMED)
        } || activeMatchRooms().any { room ->
            room.partner.id == partnerUserId && room.theirWantList.any { it.id == myItemId }
        }

    /** "받은 관심" 탭/배지가 실제로 보여줄 목록.
     *  ✅ 2026-07-08: 매칭룸이 생긴 상대를 별도 카드로 빼지 않고 일반 좋아요와 똑같이 취급한다.
     *  서버가 방 생성/병합 과정에서 좋아요 레코드를 지워버리는 케이스(백엔드 버그, 라이브 확인)가
     *  있어서, 활성 방의 theirWantList(상대가 원하는 내 옷)를 좋아요처럼 합쳐 보정한다 —
     *  이미 같은 (상대, 아이템) 좋아요가 살아있으면 중복 추가하지 않음. */
    fun receivedLikesForDisplay(): List<ReceivedLike> {
        val base = receivedLikes.filter { like ->
            !isItemCompleted(like.myItem.id) &&
                !hasActiveOrCompletedExchangeWith(like.myItem.id, like.fromUser.id)
        }
        val supplements = activeMatchRooms().flatMap { room ->
            room.theirWantList
                .filter { item -> base.none { it.fromUser.id == room.partner.id && it.myItem.id == item.id } }
                .map { ReceivedLike(fromUser = room.partner, myItem = it) }
        }
        return base + supplements
    }

    // ⚠️ 2026-07-07 추가 — N:M MatchRoom 도입 이후 발견한 문제: 상대와 이미 매칭룸이 생기면
    // (roomId 병합 경로) 그 아이템에 대한 서버의 GET /likes/received 응답이 더 이상 내려오지
    // 않는 것으로 보임(실측 — 매칭된 카드가 "받은 관심"에서 통째로 사라짐). 그래서 "받은 관심"은
    // 이제 원본 좋아요 목록뿐 아니라, SHIPPING 이전 매칭룸의 theirWantList(상대가 원하는 내 옷)도
    // 같이 봐야 매칭된 옷이 안 사라진다. 방이 SHIPPING 이상이면 hasActiveOrCompletedExchangeWith와
    // 동일한 기준으로 자연히 빠진다(아래 activeMatchRoomWith가 그 방들을 아예 안 돌려줌).
    private val activeRoomStatuses = setOf(MatchRoomStatus.SELECTING, MatchRoomStatus.MATCHED, MatchRoomStatus.CONFIRMED)

    /** SHIPPING 이전(아직 "받은 관심" 취급 대상) 상태의, 특정 상대와의 매칭룸. 없으면 null. */
    fun activeMatchRoomWith(partnerUserId: Int): MatchRoom? =
        matchRooms.firstOrNull { it.partner.id == partnerUserId && it.status in activeRoomStatuses }

    /** 상단 배지/그룹 계산에 쓰는, SHIPPING 이전 매칭룸 전체 목록 */
    fun activeMatchRooms(): List<MatchRoom> = matchRooms.filter { it.status in activeRoomStatuses }

    // ⚠️ 2026-07-08 백엔드 확인 완료 — 2026-07-07에 발견했던 "같은 상대와 중복 방 생성" 현상은
    // 실제로는 그 시점(2026-07-03) 이전의 레거시 toggle() 로직(활성 방 존재 체크 없이 무조건
    // 새 Exchange 생성)이 남긴 과거 데이터였고, 현재 로직(findActiveRoomsBetweenUsers + 유저쌍
    // 락)으로는 재현 불가함을 백엔드가 DB/로그 조사로 확정함(backend-report-response-2026-07-08.md
    // §13.1). 그래서 여기 있던 "중복 방 그룹 조회/자동 정리" 관련 코드는 전부 제거함 — 더 이상
    // 필요 없음.

    // ── 로그아웃 ─────────────────────────────────────────────────────
    fun logout() {
        unregisterDeviceToken()
        ChatSocketManager.disconnectGlobal()
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
