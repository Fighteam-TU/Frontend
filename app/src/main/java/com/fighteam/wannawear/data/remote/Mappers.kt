package com.fighteam.wannawear.data.remote

import androidx.compose.runtime.mutableStateListOf
import com.fighteam.wannawear.data.model.*
import com.fighteam.wannawear.data.remote.dto.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 서버 응답 DTO ↔ 클라이언트 도메인 모델(Models.kt) 변환 지점.
 * ⚠️ /items/discover, /items/me, /exchanges, /exchanges/{id}/messages, /users/{id}/closet 는
 * 스웨거상 정확한 리스트 필드명이 없는 MapStringObject라, items/exchanges/messages 등
 * "가장 그럴듯한 키 이름"으로 추정해서 꺼낸다. 실제 응답 확인 후 다르면 아래 extract* 함수의
 * 후보 키 목록만 조정하면 된다.
 */
private val gson = Gson()

// ── 느슨한 Map 응답에서 리스트 뽑아내기 ──────────────────────────────

private fun candidateList(map: Map<String, Any?>?, keys: List<String>): List<Any?> {
    if (map == null) return emptyList()
    for (key in keys) {
        val value = map[key]
        if (value is List<*>) return value
    }
    return emptyList()
}

private inline fun <reified T> Any?.toDto(): T? {
    if (this == null) return null
    return try {
        gson.fromJson(gson.toJson(this), T::class.java)
    } catch (e: Exception) {
        null
    }
}

fun extractItems(map: Map<String, Any?>?): List<ItemResponse> =
    candidateList(map, listOf("items", "content", "list", "results")).mapNotNull { it.toDto<ItemResponse>() }

fun extractExchanges(map: Map<String, Any?>?): List<ExchangeResponse> =
    candidateList(map, listOf("exchanges", "items", "content", "list")).mapNotNull { it.toDto<ExchangeResponse>() }

fun extractMessages(map: Map<String, Any?>?): List<MessageResponse> =
    candidateList(map, listOf("messages", "items", "content", "list")).mapNotNull { it.toDto<MessageResponse>() }

fun List<Map<String, Any?>>?.toLikeEntries(): List<LikeEntryDto> =
    this?.mapNotNull { it.toDto<LikeEntryDto>() } ?: emptyList()

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

/** ISO-8601(ex: 2026-06-30T10:20:00Z) -> "방금"/"n분 전"/"n시간 전"/"n일 전" */
fun formatRelativeDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
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
        Instant.parse(iso).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

// ── DTO → 도메인 모델 ────────────────────────────────────────────────

fun UserSummaryDto.toUser(): User = User(
    id     = id.toClientId(),
    name   = nickname ?: "알 수 없음",
    age    = 0,
    avatar = avatarUrl ?: ""
)

fun PublicUserResponse.toUser(): User = User(
    id     = id.toClientId(),
    name   = nickname ?: "알 수 없음",
    age    = 0,
    avatar = avatarUrl ?: ""
)

fun UserProfileResponse.toUser(): User = User(
    id     = id.toClientId(),
    name   = nickname ?: "알 수 없음",
    age    = 0,
    avatar = avatarUrl ?: ""
)

/** ItemResponse.user 가 없을 때(내 아이템 목록 등)는 fallbackUser로 채운다 */
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
    distance     = distanceKm?.let { "%.1fkm".format(it) } ?: "",
    tags         = tags ?: emptyList(),
    isListed     = true
)

fun MessageResponse.toChatMessage(): ChatMessage = ChatMessage(
    id        = id,
    senderId  = senderId?.toClientId() ?: -1,
    text      = content ?: "",
    timestamp = formatClockTime(sentAt)
)

/** ExchangeResponse -> MatchItem. 메시지 목록은 여기서 채우지 않고, 채팅방 진입 시
 *  AppState.loadMessages()로 별도 로드한다 (목록 API 응답엔 메시지 전문이 없음). */
fun ExchangeResponse.toMatchItem(): MatchItem = MatchItem(
    id             = id.toClientId(),
    myItem         = myItem?.toClothingItem() ?: emptyClothingItem(),
    theirItem      = theirItem?.toClothingItem() ?: emptyClothingItem(),
    partner        = partner?.toUser() ?: User(0, "알 수 없음", 0, ""),
    status         = parseExchangeStatus(status),
    date           = formatRelativeDate(matchedAt),
    messages       = mutableStateListOf(),
    partnerAddress = partnerAddress ?: "주소 정보 없음"
)

private fun emptyClothingItem(): ClothingItem = ClothingItem(
    id = -1, image = "", name = "알 수 없는 아이템", brand = "", size = "", condition = "",
    user = User(0, "", 0, "")
)
