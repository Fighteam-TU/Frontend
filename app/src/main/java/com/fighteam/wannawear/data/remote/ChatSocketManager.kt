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
 *
 * ⚠️ 2026-07-08 재설계 — 백엔드 문서(backend-report-response-2026-07-08.md §8)에 따르면
 * exchange_modification_requested 같은 이벤트는 "해당 유저의 모든 활성 WS 연결"에 브로드캐스트
 * 된다. 그런데 예전 구조는 ChatScreen을 열 때만 연결하고 나가면 바로 끊어버려서, 채팅방 밖에
 * 있을 땐 이 실시간 이벤트 자체를 받을 방법이 없었다. 그래서 연결 자체는 로그인 세션 동안
 * 앱 전역에서 상시 유지하고(connectGlobal/disconnectGlobal), 특정 채팅방 진입/이탈은 그 위에서
 * join_exchange/leave_exchange만 토글하도록 바꿨다 — 매번 새 소켓을 열고 닫지 않는다.
 */
object ChatSocketManager {
    private val gson = Gson()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var joinedExchangeId: Int? = null

    /** 로그인 성공 시 1회 호출 — 특정 방에 join 하지 않아도 유저 단위 브로드캐스트(수정요청 등)를
     *  받을 수 있도록 소켓만 미리 열어둔다. 이미 열려있으면 아무것도 안 함. */
    fun connectGlobal() {
        if (webSocket != null) return
        openSocket(joinRoomId = null)
    }

    /** 로그아웃 시 호출 — 진짜로 연결을 끊는다. */
    fun disconnectGlobal() {
        joinedExchangeId?.let { send("leave_exchange", mapOf("exchangeId" to it)) }
        webSocket?.close(1000, null)
        webSocket = null
        joinedExchangeId = null
    }

    /** 채팅방 화면 진입 시 호출. 전역 연결이 이미 있으면 그 소켓을 그대로 재사용하며 join만 보내고,
     *  없으면(예: connectGlobal 호출 전에 바로 채팅방으로 딥링크된 경우 등) 새로 연다. */
    fun connect(exchangeId: Int) {
        if (joinedExchangeId == exchangeId && webSocket != null) return
        if (webSocket != null) {
            joinedExchangeId?.let { send("leave_exchange", mapOf("exchangeId" to it)) }
            send("join_exchange", mapOf("exchangeId" to exchangeId))
            joinedExchangeId = exchangeId
        } else {
            openSocket(joinRoomId = exchangeId)
        }
    }

    /** 채팅방 화면 나갈 때 호출. ⚠️ 예전엔 여기서 소켓 자체를 닫았는데, 그러면 채팅방 밖에서는
     *  전역 브로드캐스트도 못 받게 된다 — 이제 join만 해제하고 연결 자체는 로그인 세션 동안 유지. */
    fun disconnect() {
        joinedExchangeId?.let { send("leave_exchange", mapOf("exchangeId" to it)) }
        joinedExchangeId = null
    }

    private fun openSocket(joinRoomId: Int?) {
        val token = TokenManager.accessToken ?: return
        val wsUrl = RetrofitClient.BASE_URL
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + "/ws/chat?token=$token"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                joinRoomId?.let {
                    send("join_exchange", mapOf("exchangeId" to it))
                    joinedExchangeId = it
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // 소켓 연결 실패해도 조용히 넘어감 — REST(GET messages)로 이미 히스토리는 로드돼 있고,
                // 사용자는 여전히 REST 폴백(AppState.sendMessage)으로 메시지를 보낼 수 있음.
                // 다음 connect()/connectGlobal() 호출 시 webSocket==null이라 재연결을 시도하게 된다.
                this@ChatSocketManager.webSocket = null
                joinedExchangeId = null
            }
        })
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

    private fun handleIncoming(text: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val envelope = gson.fromJson(text, Map::class.java) as? Map<String, Any?> ?: return
            val event = envelope["event"] as? String ?: return
            @Suppress("UNCHECKED_CAST")
            val data = envelope["data"] as? Map<String, Any?> ?: return

            when (event) {
                "new_message" -> {
                    // ⚠️ 전역 상시 연결로 바뀌면서, 지금 어느 방에 join돼 있는지와 무관하게 메시지가
                    // 올 수 있어 "이 메시지가 어느 방(exchangeId/roomId) 것인지"를 이벤트 데이터
                    // 자체에서 읽어야 한다. 필드명이 정확히 뭘로 오는지 문서에 명시가 없어서 두 가지
                    // 후보(exchangeId, roomId)를 다 시도하고, 그래도 없으면 마지막으로 join한 방으로
                    // 폴백한다(기존 동작과 동일하게 안전하게 유지).
                    val msgExchangeId = (data["exchangeId"] as? Number)?.toInt()
                        ?: (data["roomId"] as? Number)?.toInt()
                        ?: joinedExchangeId
                        ?: return
                    val messageJson = gson.toJson(data["message"])
                    val message = gson.fromJson(messageJson, MessageResponse::class.java) ?: return
                    mainScope.launch {
                        AppState.appendIncomingMessage(msgExchangeId, message.toChatMessage())
                        AppState.appendIncomingRoomMessage(msgExchangeId, message.toChatMessage())
                    }
                }
                // ⚠️ v1.0 확정 스펙 §8 — 서버가 "해당 유저의 모든 활성 WS 연결"에 브로드캐스트하므로
                // 특정 방에 join 안 돼 있어도(=채팅방을 안 보고 있어도) 이 이벤트는 받아야 한다.
                // 이제 앱 전역 상시 연결 덕분에 실제로 그렇게 동작한다.
                "exchange_modification_requested" -> {
                    @Suppress("UNCHECKED_CAST")
                    val requestedBy = data["requestedBy"] as? Map<String, Any?>
                    val roomId = (data["roomId"] as? Number)?.toInt() ?: joinedExchangeId ?: return
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
