package com.fighteam.wannawear.data.remote

import com.fighteam.wannawear.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * WannaWear 백엔드 REST API (스웨거 openapi.json 기준, 2026-07 스냅샷).
 * data가 명확한 타입으로 정의된 엔드포인트는 그대로 매핑했고,
 * ApiResponseMapStringObject / ApiResponseListMapStringObject 처럼 느슨하게 타입된
 * 응답은 Map<String, Any> 또는 List<Map<String, Any>>로 받아서 Mappers.kt에서
 * 방어적으로 파싱한다 (필드명이 실제와 다르면 그쪽만 고치면 됨).
 */
interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────
    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthResponse>

    @POST("/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthResponse>

    @POST("/api/auth/logout")
    suspend fun logout(@Body body: TokenRefreshRequest): ApiResponse<Map<String, String>>

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
    ): ApiResponse<Map<String, Any>>

    // ── Items ─────────────────────────────────────────────────────
    @POST("/api/items")
    suspend fun createItem(@Body body: CreateItemRequest): ApiResponse<ItemResponse>

    @GET("/api/items/{itemId}")
    suspend fun getItem(@Path("itemId") itemId: Long): ApiResponse<ItemResponse>

    @PATCH("/api/items/{itemId}")
    suspend fun updateItem(@Path("itemId") itemId: Long, @Body body: UpdateItemRequest): ApiResponse<ItemResponse>

    @DELETE("/api/items/{itemId}")
    suspend fun deleteItem(@Path("itemId") itemId: Long): ApiResponse<Map<String, String>>

    @GET("/api/items/me")
    suspend fun getMyItems(
        @Query("status") status: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Map<String, Any>>

    @GET("/api/items/discover")
    suspend fun discoverItems(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 10.0,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Map<String, Any>>

    // ── Likes ─────────────────────────────────────────────────────
    @POST("/api/likes/{itemId}")
    suspend fun toggleLike(@Path("itemId") itemId: Long): ApiResponse<LikeToggleResponse>

    @POST("/api/likes/{itemId}/dislike")
    suspend fun dislikeItem(@Path("itemId") itemId: Long): ApiResponse<Map<String, Any>>

    @GET("/api/likes/sent")
    suspend fun getSentLikes(): ApiResponse<List<Map<String, Any>>>

    @GET("/api/likes/received")
    suspend fun getReceivedLikes(): ApiResponse<List<Map<String, Any>>>

    // ── Exchanges ─────────────────────────────────────────────────
    @GET("/api/exchanges")
    suspend fun getExchanges(
        @Query("status") status: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Map<String, Any>>

    @GET("/api/exchanges/{exchangeId}")
    suspend fun getExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<ExchangeResponse>

    @PATCH("/api/exchanges/{exchangeId}/confirm")
    suspend fun confirmExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<Map<String, Any>>

    @PATCH("/api/exchanges/{exchangeId}/ship")
    suspend fun shipExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<Map<String, Any>>

    @PATCH("/api/exchanges/{exchangeId}/complete")
    suspend fun completeExchange(@Path("exchangeId") exchangeId: Long): ApiResponse<Map<String, Any>>

    // ── Chat ──────────────────────────────────────────────────────
    @GET("/api/exchanges/{exchangeId}/messages")
    suspend fun getMessages(
        @Path("exchangeId") exchangeId: Long,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 50
    ): ApiResponse<Map<String, Any>>

    @POST("/api/exchanges/{exchangeId}/messages")
    suspend fun sendMessage(
        @Path("exchangeId") exchangeId: Long,
        @Body body: SendMessageRequest
    ): ApiResponse<MessageResponse>

    @PATCH("/api/exchanges/{exchangeId}/messages/read")
    suspend fun markMessagesRead(@Path("exchangeId") exchangeId: Long): ApiResponse<Map<String, Any>>

    // ── Addresses ─────────────────────────────────────────────────
    @GET("/api/addresses")
    suspend fun getAddresses(): ApiResponse<List<AddressResponse>>

    @POST("/api/addresses")
    suspend fun addAddress(@Body body: AddressRequest): ApiResponse<AddressResponse>

    @DELETE("/api/addresses/{addressId}")
    suspend fun deleteAddress(@Path("addressId") addressId: Long): ApiResponse<Map<String, Boolean>>

    // ── Device Tokens (FCM) ──────────────────────────────────────
    @POST("/api/device-tokens")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): ApiResponse<Map<String, Boolean>>

    // ── Uploads ───────────────────────────────────────────────────
    @Multipart
    @POST("/api/uploads")
    suspend fun uploadFile(
        @Query("purpose") purpose: String = "item_image",
        @Part file: MultipartBody.Part
    ): ApiResponse<Map<String, String>>

    // ── Swipe (구 방식 — 발견 피드는 items/discover, 좋아요 판정은 likes/{itemId} 사용 권장) ──
    @GET("/api/swipe/feed")
    suspend fun swipeFeed(@Query("size") size: Int): ApiResponse<List<CardData>>

    @POST("/api/swipe/swipe")
    suspend fun swipe(@Body body: SwipeRequest): ResponseBody
}
