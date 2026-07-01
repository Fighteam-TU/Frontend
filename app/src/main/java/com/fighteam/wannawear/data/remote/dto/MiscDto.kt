package com.fighteam.wannawear.data.remote.dto

/** POST /api/uploads 응답: { fileUrl } — 이 값을 그대로 imageUrl/avatarUrl 등에 넣으면 됨 */
data class UploadResponse(
    val fileUrl: String
)
