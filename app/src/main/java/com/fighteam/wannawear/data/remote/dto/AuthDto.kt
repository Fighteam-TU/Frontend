package com.fighteam.wannawear.data.remote.dto

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val nickname: String,
    val birthYear: Int? = null
)

data class TokenRefreshRequest(
    val refreshToken: String
)

data class TokenRefreshResponse(
    val accessToken: String,
    val expiresIn: Long? = null
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserSummaryDto? = null
)

data class UserSummaryDto(
    val id: Long,
    val nickname: String? = null,
    val avatarUrl: String? = null
)
