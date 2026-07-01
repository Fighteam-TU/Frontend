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
    val likeCount: Int? = null,
    // 일부 API(좋아요 매칭 결과 등)에서는 null로 옴
    val isLikedByMe: Boolean? = null,
    val createdAt: String? = null,
    val user: UserSummaryDto? = null
)

/** GET /items/discover, /items/me, /users/{id}/closet 공통 응답 형태: { items, total? } */
data class ItemListEnvelope(
    val items: List<ItemResponse> = emptyList(),
    val total: Long? = null,
    val page: Int? = null
)
