package com.fighteam.wannawear.data.remote.dto

/**
 * MatchRoom API DTO — 백엔드 전달 문서 "match-room-spec.md" v0.1 초안 기준 (2026-07-04).
 * ⚠️ 아직 백엔드에 실제로 배포되지 않은 설계 제안 단계입니다. 필드명/엔드포인트가 실제 구현
 * 중 바뀔 수 있고, 라이브 서버로 검증하지 못한 상태입니다 — 문서가 갱신되면 이 파일도 맞춰서
 * 수정해야 합니다.
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

    val modificationRequestedByMe: Boolean? = null,
    val modificationRequestedByThem: Boolean? = null,
    val modificationProposedItemIds: List<Long>? = null,

    val lastMessage: LastMessageDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// ⚠️ 목록 응답의 정확한 wrapper 키 이름이 문서에 명시돼 있지 않아 기존 컨벤션(exchanges, notifications 등)에
// 맞춰 "matchRooms"로 가정함 — 실제 API 붙일 때 확인 필요.
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
    val proposedItemIds: List<Long>
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
