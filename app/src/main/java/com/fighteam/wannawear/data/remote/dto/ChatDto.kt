package com.fighteam.wannawear.data.remote.dto

data class SendMessageRequest(
    val content: String
)

data class MessageResponse(
    val id: Long,
    val senderId: Long? = null,
    val senderNickname: String? = null,
    val content: String? = null,
    val isRead: Boolean? = null,
    val sentAt: String? = null
)

/** GET .../messages 응답: { messages, hasMore } (최신순 정렬) */
data class MessageListEnvelope(
    val messages: List<MessageResponse> = emptyList(),
    val hasMore: Boolean? = null
)

data class ReadResponse(
    val updatedCount: Int? = null
)
