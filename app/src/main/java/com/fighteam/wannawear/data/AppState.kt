package com.fighteam.wannawear.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.fighteam.wannawear.data.model.*

object AppState {

    // ── 기본 상태 ─────────────────────────────────────────────────────
    val myCloset: SnapshotStateList<ClothingItem> = mutableStateListOf(*DummyData.myCloset.toTypedArray())
    val matches:  SnapshotStateList<MatchItem>    = mutableStateListOf(*DummyData.matches.toTypedArray())

    // 남이 내 옷에 보낸 좋아요
    val likeRecords: SnapshotStateList<LikeRecord> = mutableStateListOf(*DummyData.likeRecords.toTypedArray())
    // 내가 보낸 좋아요
    val myLikes: SnapshotStateList<LikeRecord> = mutableStateListOf(*DummyData.myLikes.toTypedArray())

    // 발견 카드 덱
    val discoverCards: SnapshotStateList<ClothingItem> = mutableStateListOf(*DummyData.discoverItems.toTypedArray())

    // ── 교환중 태그용: 매칭된 내 아이템 ID 집합 ──────────────────────
    // MATCHED / CONFIRMED / SHIPPING 상태인 match 의 myItem.id 를 보관
    val matchedMyItemIds: SnapshotStateList<Int> = mutableStateListOf()

    // ── 지연 알림: 먼저 좋아요를 눌렀던 쪽에게 띄울 매치 팝업 큐 ─────
    // 로그인 직후 3~5초 뒤에 하나씩 소비
    val pendingMatchNotifications: SnapshotStateList<MatchItem> = mutableStateListOf()

    // ── 내 옷 추가 ────────────────────────────────────────────────────
    fun addMyItem(item: ClothingItem) {
        myCloset.add(0, item)
    }

    // ── 좋아요 토글 ──────────────────────────────────────────────────
    fun likeItem(targetItem: ClothingItem): MatchResult {
        val existingIdx = myLikes.indexOfFirst { it.toItemId == targetItem.id }

        // 이미 좋아요 → 취소
        if (existingIdx >= 0) {
            myLikes.removeAt(existingIdx)
            return MatchResult.Unliked
        }

        // 좋아요 추가
        myLikes.add(
            LikeRecord(
                fromUserId = DummyData.me.id,
                toItemId   = targetItem.id,
                toUserId   = targetItem.user.id
            )
        )

        // 상대방이 먼저 내 옷에 좋아요를 눌렀는지 확인
        val theirLike = likeRecords.firstOrNull { it.fromUserId == targetItem.user.id }
        if (theirLike != null) {
            val myItem = myCloset.firstOrNull { it.id == theirLike.toItemId }
                ?: myCloset.firstOrNull()
                ?: return MatchResult.Liked

            val newMatch = MatchItem(
                id        = System.currentTimeMillis().toInt(),
                myItem    = myItem,
                theirItem = targetItem,
                partner   = targetItem.user,
                status    = ExchangeStatus.MATCHED,
                date      = "방금"
            )
            matches.add(0, newMatch)

            // 교환중 태그: 내 아이템 ID 등록
            if (!matchedMyItemIds.contains(myItem.id)) {
                matchedMyItemIds.add(myItem.id)
            }

            // 상대방(먼저 누른 쪽) 지연 알림에 추가
            // 실제 서버 환경이면 푸시 알림이지만, 여기서는 pendingMatchNotifications 큐로 시뮬레이션
            pendingMatchNotifications.add(newMatch)

            return MatchResult.Matched(newMatch)
        }

        return MatchResult.Liked
    }

    // ── 교환 확정 ────────────────────────────────────────────────────
    fun confirmExchange(matchId: Int) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx >= 0) {
            val updated = matches[idx].copy(status = ExchangeStatus.CONFIRMED)
            matches[idx] = updated
            // 교환중 태그 유지 (CONFIRMED 도 교환중)
            if (!matchedMyItemIds.contains(updated.myItem.id)) {
                matchedMyItemIds.add(updated.myItem.id)
            }
        }
    }

    // ── 배송 시작 ────────────────────────────────────────────────────
    fun startShipping(matchId: Int) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx >= 0) {
            matches[idx] = matches[idx].copy(status = ExchangeStatus.SHIPPING)
        }
    }

    // ── 채팅 메시지 전송 ──────────────────────────────────────────────
    fun sendMessage(matchId: Int, text: String) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx < 0 || text.isBlank()) return
        val match = matches[idx]
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        match.messages.add(
            ChatMessage(
                id        = System.currentTimeMillis(),
                senderId  = DummyData.me.id,
                text      = text.trim(),
                timestamp = now
            )
        )
        // SnapshotStateList 리컴포지션 트리거
        matches[idx] = match.copy(date = match.date)
    }

    // ── 채팅 가능 여부 ────────────────────────────────────────────────
    fun isChatOpen(status: ExchangeStatus) =
        status == ExchangeStatus.MATCHED ||
        status == ExchangeStatus.CONFIRMED ||
        status == ExchangeStatus.SHIPPING

    // ── 교환 완료 처리 ────────────────────────────────────────────────
    // COMPLETE 상태가 되면:
    //   · matchedMyItemIds 에서 제거 (교환중 태그 해제)
    //   · myCloset 에서 해당 아이템 제거
    //   · myLikes / likeRecords 에서 관련 기록 제거
    fun completeExchange(matchId: Int) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx < 0) return

        val match = matches[idx]
        matches[idx] = match.copy(status = ExchangeStatus.COMPLETE)

        val myItemId    = match.myItem.id
        val theirItemId = match.theirItem.id

        // 교환중 태그 해제
        matchedMyItemIds.remove(myItemId)

        // 내 옷장에서 제거
        myCloset.removeAll { it.id == myItemId }

        // 좋아요 기록 정리
        myLikes.removeAll     { it.toItemId == theirItemId }
        likeRecords.removeAll { it.toItemId == myItemId && it.fromUserId == match.partner.id }
    }

    // ── 지연 알림 소비 ───────────────────────────────────────────────
    fun consumePendingNotification(): MatchItem? {
        return if (pendingMatchNotifications.isNotEmpty())
            pendingMatchNotifications.removeAt(0)
        else null
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────
    fun getLikesOnMyItems(): List<LikeRecord> = likeRecords.toList()

    fun getUserCloset(userId: Int): List<ClothingItem> =
        DummyData.discoverItems.filter { it.user.id == userId }
            .ifEmpty { DummyData.discoverItems.take(2) }

    /** 특정 아이템이 현재 교환중인지 */
    fun isItemInExchange(itemId: Int): Boolean = matchedMyItemIds.contains(itemId)

    /** 특정 아이템이 교환완료(COMPLETE)된 match 에 포함되어 있는지 */
    fun isItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.myItem.id == itemId }

    /** 특정 남의 아이템이 교환완료된 match 에 포함되어 있는지 (보낸 관심 탭 필터용) */
    fun isTheirItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.theirItem.id == itemId }
}

sealed class MatchResult {
    object Liked      : MatchResult()
    object Unliked    : MatchResult()
    object AlreadyLiked : MatchResult()
    data class Matched(val match: MatchItem) : MatchResult()
}
