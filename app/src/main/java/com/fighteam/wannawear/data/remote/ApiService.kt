package com.fighteam.wannawear.data.remote

import com.fighteam.wannawear.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * WannaWear 백엔드 REST API — 팀 작성 API_SPEC.md 기준(2026-07 스냅샷).
 * 채팅은 WebSocket(ChatSocketManager)이 메인 경로이고, 여기 메시지 엔드포인트는
 * 초기 히스토리 로딩용 REST 폴백이다.
 */
interface ApiService {

    // ── Auth (인증 불필요) ───────────────────────────────────────────
    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthResponse>

    @POST("/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthResponse>

    @POST("/api/auth/logout")
    suspend fun logout(@Body body: TokenRefreshRequest): ApiResponse<Unit>

    @POST("/api/auth/token/refresh")
    suspend fun refreshToken(@Body body: TokenRefreshRequest): ApiResponse<TokenRefreshResponse>

    // ── Users ─────────────────────────────────────────────────────
    @GET("/api/users/me")
    suspend fun getMyProfile(): ApiResponse<UserProfileResponse>

    @PATCH("/api/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResponse<UserProfileResponse>

    @GET("/api/users/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: Long): ApiResponse<PublicUserResponse>

    @GET("/api/users/{userId}/closet")
    suspend fun getUserCloset(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<ItemListEnvelope>

    // ── Addresses (교환 confirm 시 상대에게 노출될 배송지) ───────────
    @GET("/api/addresses")
    suspend fun getAddresses(): ApiResponse<List<AddressResponse>>

    @POST("/api/addresses")
    suspend fun addAddress(@Body body: AddressRequest): ApiResponse<AddressResponse>

    @DELETE("/api/addresses/{addressId}")
    suspend fun deleteAddress(@Path("addressId") addressId: Long): ApiResponse<Unit>

    // ── Items ─────────────────────────────────────────────────────
    @POST("/api/items")
    suspend fun createItem(@Body body: CreateItemRequest): ApiResponse<ItemResponse>

    @GET("/api/items/{itemId}")
    suspend fun getItem(@Path("itemId") itemId: Long): ApiResponse<ItemResponse>

    @PATCH("/api/items/{itemId}")
    suspend fun updateItem(@Path("itemId") itemId: Long, @Body body: UpdateItemRequest): ApiResponse<ItemResponse>

    @DELETE("/api/items/{itemId}")
    suspend fun deleteItem(@Path("itemId") itemId: Long): ApiResponse<Unit>

    /** status: all | listed | in_exchange | exchanged */
    @GET("/api/items/me")
    suspend fun getMyItems(
        @Query("status") status: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<ItemListEnvelope>

    /** ⚠️ 위치 파라미터 없음. 본인 아이템/이미 스와이프한 아이템은 서버가 자동 제외. */
    @GET("/api/items/discover")
    suspend fun discoverItems(
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 20
    ): ApiResponse<ItemListEnvelope>

    // ── Likes ─────────────────────────────────────────────────────
    @POST("/api/likes/{itemId}")
    suspend fun toggleLike(@Path("itemId") itemId: Long): ApiResponse<LikeToggleResponse>

    @POST("/api/likes/{itemId}/dislike")
    suspend fun dislikeItem(@Path("itemId") itemId: Long): ApiResponse<DislikeResponse>

    @GET("/api/likes/sent")
    suspend fun getSentLikes(): ApiResponse<List<SentLikeEntryDto>>

    @GET("/api/likes/received")
    suspend fun getReceivedLikes(): ApiResponse<List<ReceivedLikeEntryDto>>

    // ── Exchanges ─────────────────────────────────────────────────
    /** status: all | MATCHED | CONFIRMED | SHIPPING | COMPLETE | CANCELLED */
    @GET("/api/exchanges")
    suspend fun getExchanges(
        @Query("status") status: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<ExchangeListEnvelope>

    @GET("/api/exchanges/{exchangeId}")
    suspend fun getExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<ExchangeResponse>

    /** 호출한 유저 본인의 기본 주소가 없으면 NOT_FOUND 에러 */
    @PATCH("/api/exchanges/{exchangeId}/confirm")
    suspend fun confirmExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<ExchangeActionResponse>

    @PATCH("/api/exchanges/{exchangeId}/ship")
    suspend fun shipExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<ExchangeActionResponse>

    @PATCH("/api/exchanges/{exchangeId}/complete")
    suspend fun completeExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<ExchangeActionResponse>

    /** MATCHED/CONFIRMED 상태에서만 가능, 참여자 아무나 호출해도 즉시 취소됨 */
    @PATCH("/api/exchanges/{exchangeId}/cancel")
    suspend fun cancelExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<ExchangeActionResponse>

    // ── Chat (REST 폴백 — 메인 경로는 WebSocket) ─────────────────────
    @GET("/api/exchanges/{exchangeId}/messages")
    suspend fun getMessages(
        @Path("exchangeId") exchangeId: Long,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 50
    ): ApiResponse<MessageListEnvelope>

    @POST("/api/exchanges/{exchangeId}/messages")
    suspend fun sendMessage(
        @Path("exchangeId") exchangeId: Long,
        @Body body: SendMessageRequest
    ): ApiResponse<MessageResponse>

    @PATCH("/api/exchanges/{exchangeId}/messages/read")
    suspend fun markMessagesRead(@Path("exchangeId") exchangeId: Long): ApiResponse<ReadResponse>

    // ── Uploads ───────────────────────────────────────────────────
    @Multipart
    @POST("/api/uploads")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: RequestBody
    ): ApiResponse<UploadResponse>

    // ── Notifications (2026-07-02 추가) ──────────────────────────────
    @GET("/api/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<NotificationListEnvelope>

    @GET("/api/notifications/unread-count")
    suspend fun getUnreadNotificationCount(): ApiResponse<UnreadCountResponse>

    @PATCH("/api/notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") notificationId: Long): ApiResponse<Unit>

    @PATCH("/api/notifications/read-all")
    suspend fun markAllNotificationsRead(): ApiResponse<Unit>

    @POST("/api/notifications/device-tokens")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): ApiResponse<Unit>

    @DELETE("/api/notifications/device-tokens")
    suspend fun unregisterDeviceToken(@Query("token") token: String): ApiResponse<Unit>

    // ── Review / 평점 (2026-07-02 추가) ──────────────────────────────
    @POST("/api/exchanges/{exchangeId}/review")
    suspend fun submitReview(
        @Path("exchangeId") exchangeId: Long,
        @Body body: ReviewRequest
    ): ApiResponse<ReviewResponse>

    @GET("/api/exchanges/{exchangeId}/review")
    suspend fun getReviewStatus(@Path("exchangeId") exchangeId: Long): ApiResponse<ReviewStatusResponse>

    // ── 검색 (2026-07-02 추가) ────────────────────────────────────────
    /** q가 비어있으면 서버가 400 VALIDATION_ERROR를 반환하니 호출 전에 빈 문자열 체크 필요 */
    @GET("/api/items/search")
    suspend fun searchItems(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<ItemListEnvelope>

    // ── MatchRoom (2026-07-04 추가, v1.0 확정 스펙 — 백엔드 구현/배포/E2E검증 완료) ──
    // ⚠️ match-room-spec.md v1.0 기준. roomId는 기존 exchangeId와 같은 값(같은 테이블/row 공유).
    /** status: all | SELECTING | MATCHED | CONFIRMED | SHIPPING | COMPLETE | CANCELLED */
    @GET("/api/match-rooms")
    suspend fun getMatchRooms(
        @Query("status") status: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<MatchRoomListEnvelope>

    @GET("/api/match-rooms/{roomId}")
    suspend fun getMatchRoom(@Path("roomId") roomId: Long): ApiResponse<MatchRoomResponse>

    /** 내가 selected에 담을 수 있는 후보(=내가 좋아요한 상대 아이템들) 조회 */
    @GET("/api/match-rooms/{roomId}/candidates")
    suspend fun getMatchRoomCandidates(@Path("roomId") roomId: Long): ApiResponse<MatchRoomCandidatesResponse>

    /** 내 selected 목록 전체 교체 (SELECTING 상태에서만 가능) */
    @PATCH("/api/match-rooms/{roomId}/selection")
    suspend fun updateMatchRoomSelection(
        @Path("roomId") roomId: Long,
        @Body body: SelectionRequest
    ): ApiResponse<MatchRoomResponse>

    /** 내 선택 잠금 — 양쪽 다 하면 MATCHED로 전환 */
    @PATCH("/api/match-rooms/{roomId}/lock-selection")
    suspend fun lockMatchRoomSelection(@Path("roomId") roomId: Long): ApiResponse<MatchRoomActionResponse>

    @PATCH("/api/match-rooms/{roomId}/confirm")
    suspend fun confirmMatchRoom(@Path("roomId") roomId: Long): ApiResponse<MatchRoomActionResponse>

    @PATCH("/api/match-rooms/{roomId}/ship")
    suspend fun shipMatchRoom(@Path("roomId") roomId: Long): ApiResponse<MatchRoomActionResponse>

    @PATCH("/api/match-rooms/{roomId}/complete")
    suspend fun completeMatchRoom(@Path("roomId") roomId: Long): ApiResponse<MatchRoomActionResponse>

    /** SELECTING/MATCHED/CONFIRMED에서만 가능 */
    @PATCH("/api/match-rooms/{roomId}/cancel")
    suspend fun cancelMatchRoom(@Path("roomId") roomId: Long): ApiResponse<MatchRoomActionResponse>

    /** SHIPPING 이전 상태에서만 가능 — 상태를 SELECTING으로 롤백 */
    @PATCH("/api/match-rooms/{roomId}/request-modification")
    suspend fun requestMatchRoomModification(
        @Path("roomId") roomId: Long,
        @Body body: ModificationRequest
    ): ApiResponse<MatchRoomActionResponse>

    // ⚠️ v1.0 확정 스펙 §6: 기존 ChatService를 그대로 위임하는 것뿐이라 동작은 100% 동일하지만,
    // 경로가 명시적으로 제공됨 — /api/exchanges/... 대신 이걸 쓴다.
    @GET("/api/match-rooms/{roomId}/messages")
    suspend fun getMatchRoomMessages(
        @Path("roomId") roomId: Long,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 50
    ): ApiResponse<MessageListEnvelope>

    @POST("/api/match-rooms/{roomId}/messages")
    suspend fun sendMatchRoomMessage(
        @Path("roomId") roomId: Long,
        @Body body: SendMessageRequest
    ): ApiResponse<MessageResponse>

    @PATCH("/api/match-rooms/{roomId}/messages/read")
    suspend fun markMatchRoomMessagesRead(@Path("roomId") roomId: Long): ApiResponse<ReadResponse>
}
