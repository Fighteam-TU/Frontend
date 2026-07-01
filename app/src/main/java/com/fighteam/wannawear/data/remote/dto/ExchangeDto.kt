package com.fighteam.wannawear.data.remote.dto

data class ExchangeResponse(
    val id: Long,
    val status: String? = null,
    val myItem: ItemResponse? = null,
    val theirItem: ItemResponse? = null,
    val partner: PublicUserResponse? = null,
    // 상대방이 confirm 하기 전엔 서버가 이 필드 자체를 안 내려줌
    val partnerAddress: String? = null,
    val myConfirmed: Boolean? = null,
    val theirConfirmed: Boolean? = null,
    val myShipped: Boolean? = null,
    val theirShipped: Boolean? = null,
    val myReceived: Boolean? = null,
    val theirReceived: Boolean? = null,
    // status == CANCELLED 일 때만 존재
    val cancelledByMe: Boolean? = null,
    val lastMessage: LastMessageDto? = null,
    val matchedAt: String? = null
)

data class LastMessageDto(
    val content: String? = null,
    val sentAt: String? = null
)

/** GET /exchanges 응답: { exchanges, total } */
data class ExchangeListEnvelope(
    val exchanges: List<ExchangeResponse> = emptyList(),
    val total: Long? = null
)

/** POST /likes/{itemId} 로 매칭이 성립했을 때 오는, 완전히 타입화된 요약 정보.
 *  partnerItem = 상대가 낸 아이템(내가 받을 것), myItem = 내가 낸 아이템(상대가 받을 것). */
data class ExchangeSummaryDto(
    val id: Long,
    val status: String? = null, // 항상 "MATCHED"
    val partnerItem: ItemResponse? = null,
    val myItem: ItemResponse? = null,
    val partner: PublicUserResponse? = null
)

/** confirm/ship/complete/cancel 응답 공통 형태 — 액션별로 실제 오는 필드만 채워짐 */
data class ExchangeActionResponse(
    val exchangeId: Long? = null,
    val status: String? = null,
    val myConfirmed: Boolean? = null,
    val theirConfirmed: Boolean? = null,
    val myShipped: Boolean? = null,
    val theirShipped: Boolean? = null,
    val myReceived: Boolean? = null,
    val theirReceived: Boolean? = null
)
