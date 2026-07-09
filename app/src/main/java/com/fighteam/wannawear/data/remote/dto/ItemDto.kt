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
    // listed | in_exchange | exchanged — 2026-07-04 MatchRoom 스펙에서 명시적으로 확인됨.
    // ⚠️ MatchRoom의 myWantList/theirWantList/candidates에 들어있는 아이템은 "이 방에서 이미
    // 확정됐다"는 이유만으로 in_exchange일 수 있음(에러 아님, 문서 6절 참고).
    val status: String? = null,
    val createdAt: String? = null,
    val user: UserSummaryDto? = null
)

/** GET /items/discover, /items/me, /users/{id}/closet 공통 응답 형태: { items, total? } */
data class ItemListEnvelope(
    val items: List<ItemResponse> = emptyList(),
    val total: Long? = null,
    val page: Int? = null
)
