package com.fighteam.wannawear.data.remote.dto

data class ReviewRequest(
    val score: Int
)

data class ReviewResponse(
    val id: Long,
    val exchangeId: Long? = null,
    val revieweeId: Long? = null,
    val score: Int? = null,
    val createdAt: String? = null
)

data class ReviewStatusResponse(
    val exchangeId: Long? = null,
    val canReview: Boolean = false,
    val myReviewSubmitted: Boolean = false,
    val partnerReviewSubmitted: Boolean = false,
    val myScoreGiven: Int? = null
)
