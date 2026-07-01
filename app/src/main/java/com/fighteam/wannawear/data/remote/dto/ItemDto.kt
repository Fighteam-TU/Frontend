package com.fighteam.wannawear.data.remote.dto

data class CreateItemRequest(
    val name: String,
    val brand: String? = null,
    val category: String,
    val size: String? = null,
    val heightFit: String? = null,
    val condition: String,
    val description: String? = null,
    val imageUrl: String,
    val wearingImageUrl: String? = null,
    val tags: List<String>? = null
)

data class UpdateItemRequest(
    val name: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val size: String? = null,
    val heightFit: String? = null,
    val condition: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val wearingImageUrl: String? = null,
    val tags: List<String>? = null,
    val isListed: Boolean? = null
)

data class ItemResponse(
    val id: Long,
    val name: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val size: String? = null,
    val heightFit: String? = null,
    val condition: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val wearingImageUrl: String? = null,
    val tags: List<String>? = null,
    val distanceKm: Double? = null,
    val likeCount: Int? = null,
    val isLikedByMe: Boolean? = null,
    val createdAt: String? = null,
    val user: UserSummaryDto? = null
)

/**
 * ⚠️ /api/items/discover, /api/items/me, /api/users/{id}/closet 는 스웨거상
 * ApiResponseMapStringObject(즉, data가 Map<String, Any>)로만 노출되어 있어
 * 정확한 페이지네이션 필드명이 스펙에 없음. items/list/content 순서로 추정해서
 * 파싱하고, total/hasNext 류는 있으면 쓰고 없으면 무시하도록 방어적으로 작성함.
 * 실제 응답을 한 번 확인해서 필드명이 다르면 ItemListEnvelope 파싱 부분만 고치면 됨.
 */
data class ItemListEnvelope(
    val items: List<ItemResponse> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    val hasNext: Boolean? = null
)
