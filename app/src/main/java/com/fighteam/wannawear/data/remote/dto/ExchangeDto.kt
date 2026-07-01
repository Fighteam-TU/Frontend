package com.fighteam.wannawear.data.remote.dto

data class ExchangeResponse(
    val id: Long,
    val status: String? = null,
    val myItem: ItemResponse? = null,
    val theirItem: ItemResponse? = null,
    val partner: PublicUserResponse? = null,
    val partnerAddress: String? = null,
    val myShipped: Boolean? = null,
    val theirShipped: Boolean? = null,
    val lastMessage: LastMessageDto? = null,
    val matchedAt: String? = null
)

data class LastMessageDto(
    val content: String? = null,
    val sentAt: String? = null
)

/** ⚠️ GET /api/exchanges 응답도 MapStringObject라 정확한 필드명 미확정 — exchanges 키로 추정 */
data class ExchangeListEnvelope(
    val exchanges: List<ExchangeResponse> = emptyList(),
    val total: Int? = null
)

/** LikeToggleResponse.exchange 는 스펙상 필드 타입이 정의되지 않은 느슨한 객체라
 *  여기서는 최소 정보(id, status)만 우선 반영하고, 상세는 GET /api/exchanges/{id}로 재조회한다. */
data class ExchangeSummaryDto(
    val id: Long? = null,
    val status: String? = null
)
