package com.fighteam.wannawear.data.remote.dto

/**
 * 서버 공통 응답 래퍼: { success, data, error, timestamp }
 * 스웨거의 모든 ApiResponseXxx 스키마가 이 형태를 공유한다.
 */
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: ErrorBody? = null,
    val timestamp: String? = null
)

data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
    val detail: Any? = null
)

/** ApiResponse.data 가 null 이거나 success=false 일 때 던지는 예외 */
class ApiException(val errorBody: ErrorBody?, val httpCode: Int? = null) :
    Exception(errorBody?.message ?: "알 수 없는 서버 오류가 발생했어요")
