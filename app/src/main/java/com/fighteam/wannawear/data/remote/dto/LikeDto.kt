package com.fighteam.wannawear.data.remote.dto

data class LikeToggleResponse(
    val liked: Boolean = false,
    val matched: Boolean = false,
    val exchange: ExchangeSummaryDto? = null
)

/**
 * ⚠️ /api/likes/sent, /api/likes/received 는 List<Map<String,Object>> 로만 노출되어
 * 정확한 키를 알 수 없음. item/user/likedAt 정도로 추정해서 매핑하고,
 * 실제 응답 확인 후 필드명이 다르면 이 클래스와 매퍼(Mappers.kt)만 손보면 됨.
 */
data class LikeEntryDto(
    val item: ItemResponse? = null,
    val fromUser: PublicUserResponse? = null,
    val toUser: PublicUserResponse? = null,
    val likedAt: String? = null
)
