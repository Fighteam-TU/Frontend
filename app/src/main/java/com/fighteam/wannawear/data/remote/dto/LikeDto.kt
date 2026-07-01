package com.fighteam.wannawear.data.remote.dto

data class LikeToggleResponse(
    val liked: Boolean = false,
    val matched: Boolean = false,
    // matched=true 일 때만 존재 (id/status/partnerItem/myItem/partner 전부 타입화되어 옴)
    val exchange: ExchangeSummaryDto? = null
)

data class DislikeResponse(
    val disliked: Boolean = false,
    val alreadySwiped: Boolean? = null
)

/** GET /likes/received: [{ fromUser, toItem, likedAt }] */
data class ReceivedLikeEntryDto(
    val fromUser: PublicUserResponse? = null,
    val toItem: ItemResponse? = null,
    val likedAt: String? = null
)

/** GET /likes/sent: [{ item, likedAt }] */
data class SentLikeEntryDto(
    val item: ItemResponse? = null,
    val likedAt: String? = null
)
