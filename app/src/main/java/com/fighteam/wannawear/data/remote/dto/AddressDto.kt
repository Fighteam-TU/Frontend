package com.fighteam.wannawear.data.remote.dto

data class AddressRequest(
    val label: String? = null,
    val recipient: String? = null,
    val postalCode: String? = null,
    val address1: String,
    val address2: String? = null,
    val isDefault: Boolean? = null
)

data class AddressResponse(
    val id: Long,
    val label: String? = null,
    val recipient: String? = null,
    val postalCode: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val isDefault: Boolean? = null
)
