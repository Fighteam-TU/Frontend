package com.fighteam.wannawear.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.fighteam.wannawear.data.model.*

object AppState {

    // ✅ 이미 완료(COMPLETE)된 매치에 쓰인 내 옷은 실제로는 상대에게 넘어간 상태이므로
    //    처음부터 "내 옷장"에 다시 뜨면 안 됨. (completeExchange()가 하는 정리를
    //    시드 데이터에도 동일하게 적용해서 일관성을 맞춤)
    val myCloset:  SnapshotStateList<ClothingItem> = mutableStateListOf(
        *DummyData.myCloset.filter { item ->
            DummyData.matches.none { it.status == ExchangeStatus.COMPLETE && it.myItem.id == item.id }
        }.toTypedArray()
    )
    val matches:   SnapshotStateList<MatchItem>    = mutableStateListOf(*DummyData.matches.toTypedArray())
    val likeRecords: SnapshotStateList<LikeRecord> = mutableStateListOf(*DummyData.likeRecords.toTypedArray())
    // 완료된 매치와 관련된 "받은 관심" 기록도 함께 정리 (completeExchange()가 하는 것과 동일한 정리)
    val matchedMyItemIds: SnapshotStateList<Int>   = mutableStateListOf(
        *DummyData.matches.filter { it.status != ExchangeStatus.COMPLETE }.map { it.myItem.id }.distinct().toTypedArray()
    )
    val myLikes:   SnapshotStateList<LikeRecord>   = mutableStateListOf(*DummyData.myLikes.toTypedArray())
    // ✅ 이미 좋아요한 아이템은 처음부터 발견 덱에서 제외.
    //    (buildFreshDeck()은 이 필터를 이미 하고 있었는데, 최초 덱 구성만 빠져있어서
    //     기본 더미데이터로 미리 좋아요 되어있는 아이템이 다시 뜨는 문제가 있었음 —
    //     그 카드에 다시 좋아요를 누르면 토글되어 "보낸 관심"에서 사라지는 버그로 이어짐)
    val discoverCards: SnapshotStateList<ClothingItem> = mutableStateListOf(
        *DummyData.discoverItems.filter { item -> myLikes.none { it.toItemId == item.id } }.toTypedArray()
    )
    val pendingMatchNotifications: SnapshotStateList<MatchItem> = mutableStateListOf()

    // ── 내 옷 추가 ────────────────────────────────────────────────────
    fun addMyItem(item: ClothingItem) {
        myCloset.add(0, item)
    }

    // ── 내 옷 삭제(내리기) ───────────────────────────────────────────
    // 교환이 진행 중인(matched 이상) 아이템은 삭제할 수 없음 — 상대방과의
    // 매칭/채팅/배송 데이터 정합성이 깨지기 때문에 먼저 교환을 완료하거나
    // 매치 화면에서 처리하도록 유도한다.
    fun removeMyItem(itemId: Int): RemoveResult {
        if (isItemInExchange(itemId)) return RemoveResult.BlockedInExchange

        val removed = myCloset.removeAll { it.id == itemId }
        if (!removed) return RemoveResult.NotFound

        // 이 옷에 달려 있던 "받은 관심" 기록도 함께 정리
        likeRecords.removeAll { it.toItemId == itemId }
        return RemoveResult.Removed
    }

    // ── 좋아요 토글 ──────────────────────────────────────────────────
    fun likeItem(targetItem: ClothingItem): MatchResult {
        val existingIdx = myLikes.indexOfFirst { it.toItemId == targetItem.id }

        // 이미 좋아요 → 취소
        if (existingIdx >= 0) {
            myLikes.removeAt(existingIdx)
            return MatchResult.Unliked
        }

        myLikes.add(LikeRecord(
            fromUserId = DummyData.me.id,
            toItemId   = targetItem.id,
            toUserId   = targetItem.user.id
        ))

        // 상대방이 이미 내 옷에 좋아요를 눌렀는지 확인
        val theirLike = likeRecords.firstOrNull { it.fromUserId == targetItem.user.id }
        if (theirLike != null) {
            // ⚠️ 예전엔 대상 아이템을 못 찾으면 ?: myCloset.firstOrNull() 로 아무 옷이나
            //    대신 골라 매칭을 만들어버렸음 → 실제로 좋아요 받은 적 없는 엉뚱한 옷이
            //    매칭되는 버그. 이제는 정확히 좋아요 받은 그 옷을 못 찾으면 매칭을 만들지 않음.
            val myItem = myCloset.firstOrNull { it.id == theirLike.toItemId }
                ?: return MatchResult.Liked

            // ⚠️ 같은 옷이 이미 다른 상대와 교환 진행 중이면 중복 매칭 방지
            //    (한 아이템이 동시에 두 사람과 매칭되는 걸 막음)
            if (isItemInExchange(myItem.id)) return MatchResult.Liked

            // ✅ ID 오버플로 방지: 현재 최대 ID + 1
            val newId = (matches.maxOfOrNull { it.id } ?: 0) + 1
            val newMatch = MatchItem(
                id        = newId,
                myItem    = myItem,
                theirItem = targetItem,
                partner   = targetItem.user,
                status    = ExchangeStatus.MATCHED,
                date      = "방금"
            )
            matches.add(0, newMatch)

            if (!matchedMyItemIds.contains(myItem.id)) {
                matchedMyItemIds.add(myItem.id)
            }
            pendingMatchNotifications.add(newMatch)
            return MatchResult.Matched(newMatch)
        }

        return MatchResult.Liked
    }

    // ── 교환 확정 (MATCHED → CONFIRMED) ─────────────────────────────
    fun confirmExchange(matchId: Int) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx < 0) return
        // 이미 CONFIRMED 이상이면 중복 실행 방지
        if (matches[idx].status != ExchangeStatus.MATCHED) return
        val updated = matches[idx].copy(status = ExchangeStatus.CONFIRMED)
        matches[idx] = updated
        if (!matchedMyItemIds.contains(updated.myItem.id)) {
            matchedMyItemIds.add(updated.myItem.id)
        }
    }

    // ── 배송 시작 (CONFIRMED → SHIPPING) ────────────────────────────
    fun startShipping(matchId: Int) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx < 0) return
        if (matches[idx].status != ExchangeStatus.CONFIRMED) return
        matches[idx] = matches[idx].copy(status = ExchangeStatus.SHIPPING)
    }

    // ── 채팅 메시지 전송 ─────────────────────────────────────────────
    fun sendMessage(matchId: Int, text: String) {
        if (text.isBlank()) return
        val match = matches.firstOrNull { it.id == matchId } ?: return
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        // ✅ SnapshotStateList.add() 는 즉시 recomposition 트리거 — copy 트릭 불필요
        match.messages.add(ChatMessage(
            id        = System.currentTimeMillis(),
            senderId  = DummyData.me.id,
            text      = text.trim(),
            timestamp = now
        ))
    }

    // ── 채팅 가능 여부 ────────────────────────────────────────────────
    fun isChatOpen(status: ExchangeStatus) =
        status == ExchangeStatus.MATCHED  ||
        status == ExchangeStatus.CONFIRMED ||
        status == ExchangeStatus.SHIPPING

    // ── 교환 완료 (SHIPPING → COMPLETE) ─────────────────────────────
    fun completeExchange(matchId: Int) {
        val idx = matches.indexOfFirst { it.id == matchId }
        if (idx < 0) return
        if (matches[idx].status != ExchangeStatus.SHIPPING) return

        val match = matches[idx]
        matches[idx] = match.copy(status = ExchangeStatus.COMPLETE)

        val myItemId    = match.myItem.id
        val theirItemId = match.theirItem.id

        matchedMyItemIds.remove(myItemId)
        myCloset.removeAll { it.id == myItemId }
        myLikes.removeAll     { it.toItemId == theirItemId }
        likeRecords.removeAll { it.toItemId == myItemId && it.fromUserId == match.partner.id }
    }

    // ── 지연 알림 소비 ───────────────────────────────────────────────
    fun consumePendingNotification(): MatchItem? =
        if (pendingMatchNotifications.isNotEmpty()) pendingMatchNotifications.removeAt(0) else null

    // ── 헬퍼 ─────────────────────────────────────────────────────────
    fun getLikesOnMyItems(): List<LikeRecord> = likeRecords.toList()

    fun getUserCloset(userId: Int): List<ClothingItem> =
        DummyData.allDiscoverItems.filter { it.user.id == userId }
            .ifEmpty { DummyData.allDiscoverItems.take(2) }

    // ✅ id로 옷을 찾을 수 있는 모든 곳(보낸 관심 탭 등)에서 쓸 단일 조회 지점.
    //    DummyData.allDiscoverItems(발견 화면 풀)뿐 아니라, 매치에 이미 박혀있는 theirItem
    //    (예: 시드 데이터의 매치처럼 발견 풀에는 없고 매치 안에만 존재하는 아이템)까지 함께 찾는다.
    //    안 그러면 "이미 매칭된 옷인데 보낸 관심엔 안 보인다" 같은 누락 버그가 재발할 수 있음.
    fun findClothingItemById(itemId: Int): ClothingItem? =
        DummyData.allDiscoverItems.firstOrNull { it.id == itemId }
            ?: matches.firstOrNull { it.theirItem.id == itemId }?.theirItem

    fun isItemInExchange(itemId: Int): Boolean = matchedMyItemIds.contains(itemId)

    fun isItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.myItem.id == itemId }

    fun isTheirItemCompleted(itemId: Int): Boolean =
        matches.any { it.status == ExchangeStatus.COMPLETE && it.theirItem.id == itemId }
}

sealed class MatchResult {
    object Liked        : MatchResult()
    object Unliked      : MatchResult()
    object AlreadyLiked : MatchResult()
    data class Matched(val match: MatchItem) : MatchResult()
}

sealed class RemoveResult {
    object Removed          : RemoveResult()
    object BlockedInExchange: RemoveResult()
    object NotFound         : RemoveResult()
}
