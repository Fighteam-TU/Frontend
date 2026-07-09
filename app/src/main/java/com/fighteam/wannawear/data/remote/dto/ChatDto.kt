package com.fighteam.wannawear.data.remote.dto

data class SendMessageRequest(
    val content: String
)

/** ⚠️ type/payload는 MatchRoom 스펙(v0.1)에서 제안된 확장 필드 — 아직 백엔드 미구현.
 *  기존 텍스트 메시지는 type이 null이거나 "TEXT"로 오는 걸 하위 호환으로 가정. */
data class MessageResponse(
    val id: Long,
    val senderId: Long? = null,
    val senderNickname: String? = null,
    val content: String? = null,
    val type: String? = null, // null 또는 "TEXT" = 일반 메시지, "EXCHANGE_MODIFICATION_REQUEST" = 커스텀 말풍선
    val payload: MessagePayloadDto? = null,
    val isRead: Boolean? = null,
    val sentAt: String? = null
)

/** EXCHANGE_MODIFICATION_REQUEST 타입 메시지에 실리는 구조화 데이터 */
data class MessagePayloadDto(
    val roomId: Long? = null,
    val proposedItemIds: List<Long>? = null,
    // ⚠️ 2026-07-08 추가 — 백엔드 최종 스펙 §7
    val suggestedOfferItemIds: List<Long>? = null,
    val ctaLabel: String? = null
)

/** GET .../messages 응답: { messages, hasMore } (최신순 정렬) */
data class MessageListEnvelope(
    val messages: List<MessageResponse> = emptyList(),
    val hasMore: Boolean? = null
)

data class ReadResponse(
    val updatedCount: Int? = null
)
