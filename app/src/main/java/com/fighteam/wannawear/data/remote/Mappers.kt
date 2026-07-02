package com.fighteam.wannawear.data.remote

import androidx.compose.runtime.mutableStateListOf
import com.fighteam.wannawear.data.model.*
import com.fighteam.wannawear.data.remote.dto.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 서버 응답 DTO ↔ 클라이언트 도메인 모델(Models.kt) 변환 지점. */

// ── ID / 문자열 변환 헬퍼 ────────────────────────────────────────────

/** 서버 ID는 Long이지만 클라이언트 모델은 Int를 씀 (데모 스케일에서는 안전) */
fun Long.toClientId(): Int = this.toInt()

fun parseCategory(raw: String?): ClothingCategory =
    try {
        ClothingCategory.valueOf(raw?.trim()?.uppercase() ?: "OTHER")
    } catch (e: Exception) {
        ClothingCategory.OTHER
    }

fun parseExchangeStatus(raw: String?): ExchangeStatus =
    try {
        ExchangeStatus.valueOf(raw?.trim()?.uppercase() ?: "MATCHED")
    } catch (e: Exception) {
        ExchangeStatus.MATCHED
    }

fun parseNotificationType(raw: String?): NotificationType =
    try {
        NotificationType.valueOf(raw?.trim()?.uppercase() ?: "UNKNOWN")
    } catch (e: Exception) {
        NotificationType.UNKNOWN
    }

/** ISO-8601(ex: 2026-06-30T10:20:00.123 또는 ...Z) -> "방금"/"n분 전"/"n시간 전"/"n일 전" */
fun formatRelativeDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = parseIsoInstant(iso)
        val minutes = ChronoUnit.MINUTES.between(instant, Instant.now())
        when {
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            minutes < 60 * 24 -> "${minutes / 60}시간 전"
            else -> "${minutes / (60 * 24)}일 전"
        }
    } catch (e: Exception) {
        iso
    }
}

/** ISO-8601 -> "HH:mm" (채팅 말풍선 타임스탬프용) */
fun formatClockTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        parseIsoInstant(iso).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

/** 서버가 오프셋 있는 Instant("...Z")와 오프셋 없는 LocalDateTime("2026-07-01T14:16:41.606")을
 *  섞어서 내려주므로 (spec 7절 createdAt, 2절 timestamp 예시가 서로 다름) 둘 다 시도한다.
 *  LocalDateTime 문자열은 서버 로컬 타임존(KST)로 간주. */
private fun parseIsoInstant(iso: String): Instant =
    try {
        Instant.parse(iso)
    } catch (e: Exception) {
        java.time.LocalDateTime.parse(iso).atZone(ZoneId.of("Asia/Seoul")).toInstant()
    }

// ── DTO → 도메인 모델 ────────────────────────────────────────────────

fun UserSummaryDto.toUser(): User = User(
    id     = id.toClientId(),
    name   = nickname ?: "알 수 없음",
    age    = 0,
    avatar = avatarUrl ?: "",
    mannerScore = mannerScore
)

fun PublicUserResponse.toUser(): User = User(
    id     = id.toClientId(),
    name   = nickname ?: "알 수 없음",
    age    = 0,
    avatar = avatarUrl ?: "",
    mannerScore = mannerScore
)

fun UserProfileResponse.toUser(): User = User(
    id     = id.toClientId(),
    name   = nickname ?: "알 수 없음",
    age    = 0,
    avatar = avatarUrl ?: "",
    mannerScore = mannerScore
)

/** ItemResponse.user 가 없을 때(내 아이템 목록 등)는 fallbackUser로 채운다.
 *  ⚠️ 실 스펙엔 위치 기반 거리 정보가 없음 — distance는 항상 빈 문자열. */
fun ItemResponse.toClothingItem(fallbackUser: User? = null): ClothingItem = ClothingItem(
    id           = id.toClientId(),
    image        = imageUrl ?: "",
    wearingImage = wearingImageUrl ?: "",
    name         = name ?: "",
    brand        = brand ?: "",
    size         = size ?: "",
    heightFit    = heightFit ?: "",
    condition    = condition ?: "",
    category     = parseCategory(category),
    description  = description ?: "",
    user         = user?.toUser() ?: fallbackUser ?: User(0, "", 0, ""),
    distance     = "",
    tags         = tags ?: emptyList(),
    isListed     = true
)

fun MessageResponse.toChatMessage(): ChatMessage = ChatMessage(
    id        = id,
    senderId  = senderId?.toClientId() ?: -1,
    text      = content ?: "",
    timestamp = formatClockTime(sentAt)
)

fun NotificationResponse.toNotificationItem(): NotificationItem = NotificationItem(
    id         = id.toClientId(),
    type       = parseNotificationType(type),
    title      = title ?: "",
    body       = body ?: "",
    exchangeId = exchangeId?.toClientId(),
    isRead     = isRead,
    createdAt  = formatRelativeDate(createdAt)
)

fun AddressResponse.toAddress(): Address = Address(
    id         = id.toClientId(),
    label      = label ?: "기본 주소",
    recipient  = recipient ?: "",
    postalCode = postalCode ?: "",
    address1   = address1 ?: "",
    address2   = address2 ?: "",
    isDefault  = isDefault ?: false
)

/** 교환 목록/상세 조회로 받은 ExchangeResponse -> MatchItem.
 *  메시지 목록은 여기서 채우지 않고, 채팅방 진입 시 AppState.loadMessages()로 별도 로드. */
fun ExchangeResponse.toMatchItem(): MatchItem = MatchItem(
    id             = id.toClientId(),
    myItem         = myItem?.toClothingItem() ?: emptyClothingItem(),
    theirItem      = theirItem?.toClothingItem() ?: emptyClothingItem(),
    partner        = partner?.toUser() ?: User(0, "알 수 없음", 0, ""),
    status         = parseExchangeStatus(status),
    date           = formatRelativeDate(matchedAt),
    messages       = mutableStateListOf(),
    partnerAddress = partnerAddress,
    myConfirmed    = myConfirmed ?: false,
    theirConfirmed = theirConfirmed ?: false,
    myShipped      = myShipped ?: false,
    theirShipped   = theirShipped ?: false,
    myReceived     = myReceived ?: false,
    theirReceived  = theirReceived ?: false,
    cancelledByMe  = cancelledByMe
)

/** POST /likes/{itemId} 매칭 성립 시 오는 ExchangeSummaryDto -> MatchItem.
 *  partnerItem = 상대가 낸(=내가 받을) 아이템, myItem = 내가 낸(=상대가 받을) 아이템. */
fun ExchangeSummaryDto.toMatchItem(): MatchItem = MatchItem(
    id             = id.toClientId(),
    myItem         = myItem?.toClothingItem() ?: emptyClothingItem(),
    theirItem      = partnerItem?.toClothingItem() ?: emptyClothingItem(),
    partner        = partner?.toUser() ?: User(0, "알 수 없음", 0, ""),
    status         = parseExchangeStatus(status ?: "MATCHED"),
    date           = "방금",
    messages       = mutableStateListOf(),
    partnerAddress = null
)

private fun emptyClothingItem(): ClothingItem = ClothingItem(
    id = -1, image = "", name = "알 수 없는 아이템", brand = "", size = "", condition = "",
    user = User(0, "", 0, "")
)
