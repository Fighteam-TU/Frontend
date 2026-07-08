package com.fighteam.wannawear.data.remote.dto

/**
 * MatchRoom API DTO — 백엔드 최종 스펙 문서(backend-report-response-2026-07-08.md) 기준.
 * ⚠️ 2026-07-08: 백엔드가 전체 스펙을 확정/구현/검증 완료했다고 전달함. 오늘부로 이 문서를
 * 기준으로 필드 갱신함 (suggestedOfferItemIds 신규 추가 등).
 */

data class MatchRoomResponse(
    val id: Long,
    val status: String? = null, // SELECTING | MATCHED | CONFIRMED | SHIPPING | COMPLETE | CANCELLED
    val partner: PublicUserResponse? = null,

    val myWantList: List<ItemResponse>? = null,
    val theirWantList: List<ItemResponse>? = null,

    val myLockedSelection: Boolean? = null,
    val theirLockedSelection: Boolean? = null,

    val myConfirmed: Boolean? = null,
    val theirConfirmed: Boolean? = null,
    val myShipped: Boolean? = null,
    val theirShipped: Boolean? = null,
    val myReceived: Boolean? = null,
    val theirReceived: Boolean? = null,

    val partnerAddress: String? = null,
    // status == CANCELLED 일 때만 의미 있음 (true=내가 취소, false=상대방이 취소)
    val cancelledByMe: Boolean? = null,

    val modificationRequestedByMe: Boolean? = null,
    val modificationRequestedByThem: Boolean? = null,
    val modificationProposedItemIds: List<Long>? = null,
    // ⚠️ 2026-07-08 신규 — 요청자가 "제시하고 싶다"고 표시한 자기 소유 아이템 id들(권유 참고용).
    val modificationSuggestedOfferItemIds: List<Long>? = null,

    val lastMessage: LastMessageDto? = null,
    // ⚠️ v1.0 확정 스펙: createdAt/updatedAt이 아니라 matchedAt 하나만 내려옴 (ExchangeResponse와 동일한 필드명)
    val matchedAt: String? = null
)

data class MatchRoomListEnvelope(
    val matchRooms: List<MatchRoomResponse> = emptyList(),
    val total: Long? = null
)

data class MatchRoomCandidatesResponse(
    val myCandidates: List<ItemResponse> = emptyList(),
    val theirCandidates: List<ItemResponse> = emptyList()
)

data class SelectionRequest(
    val itemIds: List<Long>
)

data class ModificationRequest(
    val proposedItemIds: List<Long>,
    // ⚠️ 2026-07-08 신규(선택 필드) — 요청자 본인 소유 아이템만 가능, 검증은 후보풀이 아니라
    // "본인 소유 여부"로 이뤄짐. 생략 시 서버가 빈 목록으로 처리.
    val suggestedOfferItemIds: List<Long>? = null
)

/** lock-selection/confirm/ship/complete/cancel/request-modification 응답 공통 형태
 *  — 액션별로 실제 오는 필드만 채워짐(ExchangeActionResponse와 동일한 패턴) */
data class MatchRoomActionResponse(
    val roomId: Long? = null,
    val status: String? = null,
    val myLockedSelection: Boolean? = null,
    val theirLockedSelection: Boolean? = null,
    val myConfirmed: Boolean? = null,
    val theirConfirmed: Boolean? = null,
    val myShipped: Boolean? = null,
    val theirShipped: Boolean? = null,
    val myReceived: Boolean? = null,
    val theirReceived: Boolean? = null
)
