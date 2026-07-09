package com.fighteam.wannawear.data.remote.dto

data class NotificationResponse(
    val id: Long,
    val type: String? = null,
    val title: String? = null,
    val body: String? = null,
    val exchangeId: Long? = null,
    val isRead: Boolean = false,
    val createdAt: String? = null
)

data class NotificationListEnvelope(
    val notifications: List<NotificationResponse> = emptyList(),
    val total: Long = 0,
    val hasMore: Boolean = false
)

// ⚠️ 스펙 문서에 이 응답의 정확한 JSON 예시가 없어서 관용적인 필드명("count")으로 가정함.
// 실제 응답 필드명이 다르면(예: unreadCount) 백엔드에 확인 후 수정 필요.
data class UnreadCountResponse(
    val count: Int = 0
)

data class DeviceTokenRequest(
    val token: String,
    val platform: String? = "android"
)
