package com.fighteam.wannawear.data.remote.dto

data class DeviceTokenRequest(
    val fcmToken: String,
    val platform: String? = "android"
)

data class SwipeRequest(
    val clothesId: Long,
    val liked: Boolean
)

data class CardData(
    val ownerId: Long? = null,
    val clothesId: Long? = null,
    val clothesUrl: String? = null
)
