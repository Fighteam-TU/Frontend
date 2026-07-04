package com.fighteam.wannawear.data.remote

import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.remote.dto.MessageResponse
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * ws://.../ws/chat?token={accessToken} — STOMP 아님, 순수 텍스트 프레임에 JSON.
 * {"event": "...", "data": {...}} 형태로 송수신 (API_SPEC.md 12절).
 * 채팅은 이게 메인 경로이고, ApiService의 REST 메시지 엔드포인트는 초기 히스토리 로딩용 폴백.
 */
object ChatSocketManager {
    private val gson = Gson()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var joinedExchangeId: Int? = null

    fun connect(exchangeId: Int) {
        if (joinedExchangeId == exchangeId && webSocket != null) return
        disconnect()

        val token = TokenManager.accessToken ?: return
        val wsUrl = RetrofitClient.BASE_URL
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + "/ws/chat?token=$token"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                send("join_exchange", mapOf("exchangeId" to exchangeId))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(exchangeId, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // 소켓 연결 실패해도 조용히 넘어감 — REST(GET messages)로 이미 히스토리는 로드돼 있고,
                // 사용자는 여전히 REST 폴백(AppState.sendMessage)으로 메시지를 보낼 수 있음
            }
        })
        joinedExchangeId = exchangeId
    }

    fun disconnect() {
        joinedExchangeId?.let { send("leave_exchange", mapOf("exchangeId" to it)) }
        webSocket?.close(1000, null)
        webSocket = null
        joinedExchangeId = null
    }

    fun sendMessage(exchangeId: Int, content: String) {
        if (content.isBlank()) return
        send("send_message", mapOf("exchangeId" to exchangeId, "content" to content))
    }

    fun sendTyping(exchangeId: Int, isTyping: Boolean) {
        send(if (isTyping) "typing_start" else "typing_stop", mapOf("exchangeId" to exchangeId))
    }

    fun markRead(exchangeId: Int) {
        send("read_messages", mapOf("exchangeId" to exchangeId))
    }

    private fun send(event: String, data: Map<String, Any>) {
        val json = gson.toJson(mapOf("event" to event, "data" to data))
        webSocket?.send(json)
    }

    private fun handleIncoming(exchangeId: Int, text: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val envelope = gson.fromJson(text, Map::class.java) as? Map<String, Any?> ?: return
            val event = envelope["event"] as? String ?: return
            @Suppress("UNCHECKED_CAST")
            val data = envelope["data"] as? Map<String, Any?> ?: return

            when (event) {
                "new_message" -> {
                    val messageJson = gson.toJson(data["message"])
                    val message = gson.fromJson(messageJson, MessageResponse::class.java) ?: return
                    mainScope.launch {
                        AppState.appendIncomingMessage(exchangeId, message.toChatMessage())
                        AppState.appendIncomingRoomMessage(exchangeId, message.toChatMessage())
                    }
                }
                // ⚠️ 2026-07-04 추가, v0.1 설계 제안(match-room-spec.md §8) — 백엔드 미배포.
                // 문서상 이 이벤트는 "해당 유저의 모든 활성 연결"에 브로드캐스트된다고 돼있어서,
                // 지금처럼 특정 방에 join한 소켓 하나만으로는 다른 방에서 온 이벤트를 놓칠 수 있음
                // (지금 구조는 화면별로 그때그때 연결하는 방식이라 상시 연결이 아님) — 실제 배포되면
                // 소켓 연결 유지 전략을 앱 전역으로 바꿀지 재검토 필요.
                "exchange_modification_requested" -> {
                    @Suppress("UNCHECKED_CAST")
                    val requestedBy = data["requestedBy"] as? Map<String, Any?>
                    val roomId = (data["roomId"] as? Number)?.toInt() ?: exchangeId
                    val requesterName = requestedBy?.get("nickname") as? String ?: "상대방"
                    mainScope.launch {
                        AppState.onModificationRequestedRealtime(roomId, requesterName)
                    }
                }
                // user_typing / user_stop_typing / messages_read는 필요해지면 여기서 확장
            }
        } catch (e: Exception) {
            // 형식이 안 맞는 프레임은 무시 (연결 자체는 유지)
        }
    }
}
