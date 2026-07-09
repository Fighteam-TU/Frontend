package com.fighteam.wannawear.data.remote

import androidx.compose.runtime.mutableStateOf

/**
 * 리프레시 토큰까지 만료/무효화되어 재로그인이 필요할 때 TokenAuthenticator가 이 플래그를 세운다.
 * NavGraph가 이 값을 관찰해서 강제로 로그인 화면으로 되돌린다.
 */
object SessionEvents {
    val sessionExpired = mutableStateOf(false)

    fun markExpired() { sessionExpired.value = true }
    fun consume() { sessionExpired.value = false }
}
