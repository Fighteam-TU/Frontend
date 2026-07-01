package com.fighteam.wannawear.data.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

// ─────────────────────────────────────────────────────────────────────
// 기본 모델
// ─────────────────────────────────────────────────────────────────────

data class User(
    val id: Int,
    val name: String,
    val age: Int,
    val avatar: String
)

data class ClothingItem(
    val id: Int,
    val image: String,
    val wearingImage: String = "",
    val name: String,
    val brand: String,
    val size: String,
    val heightFit: String = "",
    val condition: String,
    val category: ClothingCategory = ClothingCategory.OTHER,
    val description: String = "",
    val user: User,
    val distance: String = "",
    val tags: List<String> = emptyList(),
    val isListed: Boolean = false
)

enum class ClothingCategory(val label: String) {
    TOP("상의"), BOTTOM("하의"), OUTER("아우터"),
    DRESS("원피스/세트"), SHOES("신발"), ACC("악세서리"), OTHER("기타")
}

// ─────────────────────────────────────────────────────────────────────
// 채팅 메시지
// ─────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id: Long,
    val senderId: Int,
    val text: String,
    val timestamp: String
)

// ─────────────────────────────────────────────────────────────────────
// 교환 상태 (5단계)
// ─────────────────────────────────────────────────────────────────────

enum class ExchangeStatus(val label: String, val description: String) {
    WAITING  ("받은 관심",    "상대방이 내 옷에 좋아요를 눌렀어요"),
    MATCHED  ("교환 대기",    "서로 좋아요! 교환을 확정해보세요"),
    CONFIRMED("배송 준비 중", "교환이 확정됐어요. 배송 안내를 확인하세요"),
    SHIPPING ("배송 중",      "서로 배송이 시작됐어요"),
    COMPLETE ("교환 완료",    "교환이 성공적으로 완료됐어요")
}

data class MatchItem(
    val id: Int,
    val myItem: ClothingItem,
    val theirItem: ClothingItem,
    val partner: User,
    val status: ExchangeStatus,
    val date: String,
    // ✅ MutableList → SnapshotStateList : Compose recomposition 즉시 반영
    val messages: SnapshotStateList<ChatMessage> = mutableStateListOf(),
    val partnerAddress: String = "서울시 마포구 서교동 123-45, 203호"
)

// ─────────────────────────────────────────────────────────────────────
// 좋아요 관계
// ─────────────────────────────────────────────────────────────────────

data class LikeRecord(
    val fromUserId: Int,
    val toItemId: Int,
    val toUserId: Int
)

// ⚠️ 백엔드 연동 이후 "받은 관심"/"보낸 관심"은 ID로 여러 풀을 뒤져서 아이템을
//    되찾는 방식(LikeRecord) 대신, 서버가 내려준 실제 객체를 그대로 들고 있는
//    방식으로 바꿨다. 예전에 반복해서 발생했던 "아이템을 못 찾아서 누락되는" 버그
//    클래스 자체를 원천 차단하기 위함.
data class ReceivedLike(
    val fromUser: User,
    val myItem: ClothingItem
)

data class SentLike(
    val item: ClothingItem
)

// ─────────────────────────────────────────────────────────────────────
// 더미 데이터
// ─────────────────────────────────────────────────────────────────────

object DummyData {
    val users = listOf(
        User(1, "지민", 24, "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=80&h=80&fit=crop"),
        User(2, "수연", 22, "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=80&h=80&fit=crop"),
        User(3, "태양", 26, "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=80&h=80&fit=crop"),
        User(4, "하은", 23, "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=80&h=80&fit=crop"),
        User(5, "나연", 25, "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=80&h=80&fit=crop")
    )
    val me = User(0, "김지은", 24, "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=120&h=120&fit=crop")

    val discoverItems = listOf(
        ClothingItem(1,
            "https://images.unsplash.com/photo-1551537482-f2075a1d41f2?w=420&h=700&fit=crop",
            "https://images.unsplash.com/photo-1509631179647-0177331693ae?w=420&h=700&fit=crop",
            "빈티지 레더 라이더 재킷", "Schott NYC", "M", "168~178cm", "거의 새것",
            ClothingCategory.OUTER, "어깨 부분 미세 스크래치 있으나 착용 시 거의 안 보임",
            users[0], "2.1km", listOf("빈티지", "아우터", "가죽")),
        ClothingItem(2,
            "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=420&h=700&fit=crop",
            "", "플로럴 패턴 미디 드레스", "& Other Stories", "S", "160~167cm", "양호",
            ClothingCategory.DRESS, "세탁 후 보관, 착용감 편함",
            users[1], "5.3km", listOf("드레스", "봄/여름")),
        ClothingItem(3,
            "https://images.unsplash.com/photo-1542272604-787c3835535d?w=420&h=700&fit=crop",
            "", "501 오리지널 데님 재킷", "Levi's", "L", "170~180cm", "보통",
            ClothingCategory.OUTER, "소매 끝 약간 해어짐",
            users[2], "1.8km", listOf("데님", "아이코닉")),
        ClothingItem(4,
            "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=420&h=700&fit=crop",
            "", "울 오버사이즈 크루넥", "COS", "FREE", "155~175cm", "거의 새것",
            ClothingCategory.TOP, "1회 착용, 드라이클리닝 권장",
            users[3], "3.7km", listOf("니트", "가을/겨울")),
        ClothingItem(5,
            "https://images.unsplash.com/photo-1539533018447-63fcce2678e3?w=420&h=700&fit=crop",
            "", "더블 브레스티드 트렌치", "Mango", "M", "163~172cm", "양호",
            ClothingCategory.OUTER, "벨트 포함, 단추 전부 이상 없음",
            users[4], "6.2km", listOf("코트", "클래식"))
    )

    // 발견 탭 "새 아이템 보기"에서 추가로 섞이는 여분 풀.
    // ⚠️ discoverItems 와 별도 리스트이므로, 이 풀의 아이템에 좋아요를 누르면
    //    "보낸 관심" 탭 등에서 discoverItems 만 조회할 경우 아이템을 찾지 못해
    //    누락되는 문제가 있었음 → allDiscoverItems 로 통합해서 참조할 것.
    val extraDiscoverItems = listOf(
        ClothingItem(101, "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=420&h=700&fit=crop",
            "", "린넨 오버핏 셔츠", "Uniqlo", "L", "170~182cm", "거의 새것",
            ClothingCategory.TOP, "두 번 착용, 세탁 완료",
            users[1], "3.2km", listOf("린넨", "여름", "오버핏")),
        ClothingItem(102, "https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=420&h=700&fit=crop",
            "", "카고 와이드 팬츠", "Carhartt", "M", "165~178cm", "양호",
            ClothingCategory.BOTTOM, "포켓 지퍼 일부 빡빡함",
            users[2], "1.4km", listOf("카고", "와이드", "스트릿")),
        ClothingItem(103, "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=420&h=700&fit=crop",
            "", "버킷햇", "Supreme", "FREE", "—", "보통",
            ClothingCategory.ACC, "땀 자국 약간, 세탁 필요",
            users[3], "4.7km", listOf("모자", "스트릿")),
        ClothingItem(104, "https://images.unsplash.com/photo-1518791841217-8f162f1912da?w=420&h=700&fit=crop",
            "", "플리스 집업 재킷", "Patagonia", "S", "158~168cm", "거의 새것",
            ClothingCategory.OUTER, "1회 착용, 새상품급",
            users[4], "2.9km", listOf("플리스", "아웃도어", "가을")),
        ClothingItem(105, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=420&h=700&fit=crop",
            "", "러닝화 Air Max", "Nike", "260", "—", "양호",
            ClothingCategory.SHOES, "밑창 닳음 보통, 갑피 양호",
            users[0], "0.8km", listOf("스니커즈", "러닝", "나이키")),
        ClothingItem(106, "https://images.unsplash.com/photo-1467043237213-65f2da53396f?w=420&h=700&fit=crop",
            "", "체크 울 머플러", "Acne Studios", "FREE", "—", "거의 새것",
            ClothingCategory.ACC, "세탁 이력 없음, 드라이 권장",
            users[1], "5.1km", listOf("머플러", "울", "겨울")),
        ClothingItem(107, "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=420&h=700&fit=crop",
            "", "레더 미니백", "A.P.C", "FREE", "—", "양호",
            ClothingCategory.ACC, "스크래치 없음, 내부 깨끗",
            users[2], "6.3km", listOf("가방", "레더", "미니백")),
        ClothingItem(108, "https://images.unsplash.com/photo-1485230895905-ec40ba36b9bc?w=420&h=700&fit=crop",
            "", "슬립 원피스", "Reformation", "XS", "155~163cm", "거의 새것",
            ClothingCategory.DRESS, "택 그대로 있음",
            users[3], "3.5km", listOf("원피스", "슬립", "봄")),
    )

    // ✅ 발견 화면에서 노출될 수 있는 모든 아이템의 단일 소스.
    //    좋아요/매칭/옷장보기 등 id 로 아이템을 되찾는 모든 곳은 이 리스트를 사용해야 함.
    val allDiscoverItems: List<ClothingItem> by lazy { discoverItems + extraDiscoverItems }

    val myCloset = mutableListOf(
        ClothingItem(10,
            "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=200&h=260&fit=crop",
            "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=200&h=260&fit=crop",
            "스트라이프 셔츠", "COS", "M", "163~172cm", "양호",
            ClothingCategory.TOP, "깔끔하게 세탁 완료",
            me, "", listOf("셔츠", "캐주얼"), isListed = true),
        ClothingItem(11,
            "https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=200&h=260&fit=crop",
            "", "슬림 데님", "Levi's 501", "28", "163~172cm", "양호",
            ClothingCategory.BOTTOM, "밑단 수선 있음",
            me, "", listOf("데님", "슬림핏")),
        ClothingItem(12,
            "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=200&h=260&fit=crop",
            "", "블랙 니트", "Uniqlo", "M", "160~175cm", "양호",
            ClothingCategory.TOP, "보풀 거의 없음",
            me, "", listOf("니트", "베이직")),
        ClothingItem(13,
            "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=200&h=260&fit=crop",
            "", "화이트 티셔츠", "Everlane", "S", "158~167cm", "양호",
            ClothingCategory.TOP, "새상품급",
            me, "", listOf("티셔츠", "베이직"))
    )

    // ✅ MutableList → SnapshotStateList로 초기화
    val matches = mutableListOf(
        MatchItem(
            id = 1, myItem = myCloset[0],
            theirItem = ClothingItem(20,
                "https://images.unsplash.com/photo-1548126032-079a0fb0099d?w=200&h=260&fit=crop",
                name = "플리스 풀오버", brand = "Nike", size = "M", heightFit = "165~175cm",
                condition = "양호", user = users[0]),
            partner = users[0], status = ExchangeStatus.COMPLETE, date = "2일 전",
            messages = mutableStateListOf(
                ChatMessage(1L, users[0].id, "안녕하세요! 교환 기대돼요 😊", "10:20"),
                ChatMessage(2L, me.id, "저도요! 잘 부탁드려요", "10:21")
            )
        ),
        MatchItem(
            id = 2, myItem = myCloset[1],
            theirItem = ClothingItem(21,
                "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=200&h=260&fit=crop",
                name = "플로럴 미디 드레스", brand = "& Other Stories", size = "S",
                heightFit = "160~167cm", condition = "양호", user = users[1]),
            partner = users[1], status = ExchangeStatus.SHIPPING, date = "5시간 전",
            messages = mutableStateListOf(
                ChatMessage(3L, users[1].id, "오늘 발송했어요! 운송장 번호 알려드릴게요", "14:05"),
                ChatMessage(4L, me.id, "감사합니다 저도 오늘 보냈어요 🙌", "14:10")
            )
        ),
        MatchItem(
            id = 3, myItem = myCloset[2],
            theirItem = ClothingItem(22,
                "https://images.unsplash.com/photo-1542272604-787c3835535d?w=200&h=260&fit=crop",
                name = "데님 재킷", brand = "Levi's", size = "L", heightFit = "170~180cm",
                condition = "보통", user = users[2]),
            partner = users[2], status = ExchangeStatus.MATCHED, date = "방금",
            messages = mutableStateListOf(
                ChatMessage(5L, users[2].id, "매칭됐네요! 반갑습니다", "09:00")
            )
        )
    )

    val likeRecords = mutableListOf(
        // ⚠️ 정합성 규칙: MATCHED 이상(SHIPPING 등) 상태의 매치는 반드시 "그 사람이 내 옷에
        //    좋아요를 눌렀다"는 likeRecords 기록이 있어야 함 (likeItem()의 매칭 조건이 이걸 전제로 함).
        //    이전엔 matches[1](수연)/matches[2](태양)에 해당하는 기록이 아예 없어서
        //    "받은 관심" 탭에 진행 중인 교환 상대가 안 뜨는 버그가 있었음 → 아래 두 건으로 정정.
        LikeRecord(fromUserId = users[1].id, toItemId = myCloset[1].id, toUserId = me.id), // 수연 → 슬림 데님 (매치#2, 배송중)
        LikeRecord(fromUserId = users[2].id, toItemId = myCloset[2].id, toUserId = me.id), // 태양 → 블랙 니트 (매치#3, 매칭됨)
        // 아직 매칭되지 않은 순수 "받은 관심" — 내가 하은의 옷에 좋아요를 누르면 매칭이 성립됨
        LikeRecord(fromUserId = users[3].id, toItemId = myCloset[3].id, toUserId = me.id)  // 하은 → 화이트 티셔츠
    )
    val myLikes = mutableListOf(
        LikeRecord(fromUserId = me.id, toItemId = discoverItems[0].id, toUserId = users[0].id),
        // ⚠️ 정합성 규칙: 매치가 성립했다는 건 "내가 상대 옷에 좋아요를 보냈다"는 뜻이므로
        //    MATCHED 이상 상태의 매치는 myLikes에도 반드시 기록이 있어야 함.
        //    이전엔 matches[1](수연)/matches[2](태양)의 theirItem(id 21, 22)에 대한
        //    myLikes 기록이 아예 없어서 "보낸 관심" 탭에 이미 매칭된 옷이 안 뜨는 버그가 있었음.
        LikeRecord(fromUserId = me.id, toItemId = 21, toUserId = users[1].id), // 나 → 수연의 플로럴 미디 드레스 (매치#2)
        LikeRecord(fromUserId = me.id, toItemId = 22, toUserId = users[2].id)  // 나 → 태양의 데님 재킷 (매치#3)
    )
}
