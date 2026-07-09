package com.fighteam.wannawear.data.remote.dto

data class UpdateProfileRequest(
    val nickname: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class UserProfileResponse(
    val id: Long,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val mannerScore: Double? = null,
    val totalExchanges: Int? = null,
    val location: Map<String, Double>? = null
)

data class PublicUserResponse(
    val id: Long,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val mannerScore: Double? = null,
    val totalExchanges: Int? = null
)
