package com.fighteam.wannawear.data.remote.dto

data class LikeToggleResponse(
    val liked: Boolean = false,
    val matched: Boolean = false,
    // matched=true 일 때만 존재 (id/status/partnerItem/myItem/partner 전부 타입화되어 옴)
    val exchange: ExchangeSummaryDto? = null,
    // ⚠️ 2026-07-04 MatchRoom v1.0 추가: matched=false여도 값이 있으면 "이미 있는 매칭방의
    // 선택 목록에 추가됨"이라는 뜻 (문서 10절 — 상대와 이미 진행 중인 방이 있으면 새 Exchange
    // 대신 그 방에 합쳐짐).
    val roomId: Long? = null,
    val roomStatus: String? = null
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
