package com.fighteam.wannawear.data.model

data class User(
    val id: Int,
    val name: String,
    val age: Int,
    val avatar: String
)

data class ClothingItem(
    val id: Int,
    val image: String,
    val name: String,
    val brand: String,
    val size: String,
    val condition: String,
    val user: User,
    val distance: String,
    val tags: List<String>,
    val isListed: Boolean = false
)

data class MatchItem(
    val id: Int,
    val myItem: ClothingItem,
    val theirItem: ClothingItem,
    val partner: User,
    val status: ExchangeStatus,
    val date: String
)

enum class ExchangeStatus(val label: String) {
    COMPLETE("교환 완료"),
    SHIPPING("배송 중"),
    PENDING("교환 대기")
}

object DummyData {
    private val users = listOf(
        User(1, "지민", 24, "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=80&h=80&fit=crop"),
        User(2, "수연", 22, "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=80&h=80&fit=crop"),
        User(3, "태양", 26, "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=80&h=80&fit=crop"),
        User(4, "하은", 23, "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=80&h=80&fit=crop"),
        User(5, "나연", 25, "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=80&h=80&fit=crop")
    )

    val discoverItems = listOf(
        ClothingItem(1, "https://images.unsplash.com/photo-1551537482-f2075a1d41f2?w=420&h=700&fit=crop",
            "빈티지 레더 라이더 재킷", "Schott NYC", "M", "거의 새것", users[0], "2.1km", listOf("빈티지","아우터","가죽")),
        ClothingItem(2, "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=420&h=700&fit=crop",
            "플로럴 패턴 미디 드레스", "& Other Stories", "S", "양호", users[1], "5.3km", listOf("드레스","봄/여름")),
        ClothingItem(3, "https://images.unsplash.com/photo-1542272604-787c3835535d?w=420&h=700&fit=crop",
            "501 오리지널 데님 재킷", "Levi's", "L", "보통", users[2], "1.8km", listOf("데님","아이코닉")),
        ClothingItem(4, "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=420&h=700&fit=crop",
            "울 오버사이즈 크루넥", "COS", "FREE", "거의 새것", users[3], "3.7km", listOf("니트","가을/겨울")),
        ClothingItem(5, "https://images.unsplash.com/photo-1539533018447-63fcce2678e3?w=420&h=700&fit=crop",
            "더블 브레스티드 트렌치", "Mango", "M", "양호", users[4], "6.2km", listOf("코트","클래식"))
    )

    val myCloset = listOf(
        ClothingItem(10, "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=200&h=260&fit=crop",
            "스트라이프 셔츠", "COS", "M", "양호", users[0], "", emptyList(), isListed = true),
        ClothingItem(11, "https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=200&h=260&fit=crop",
            "슬림 데님", "Levi's 501", "28", "양호", users[0], "", emptyList()),
        ClothingItem(12, "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=200&h=260&fit=crop",
            "블랙 니트", "Uniqlo", "M", "양호", users[0], "", emptyList()),
        ClothingItem(13, "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=200&h=260&fit=crop",
            "화이트 티셔츠", "Everlane", "S", "양호", users[0], "", emptyList())
    )

    val matches = listOf(
        MatchItem(1, myCloset[0],
            ClothingItem(20, "https://images.unsplash.com/photo-1548126032-079a0fb0099d?w=200&h=260&fit=crop",
                "플리스 풀오버", "Nike", "M", "양호", users[0], "", emptyList()),
            users[0], ExchangeStatus.COMPLETE, "2일 전"),
        MatchItem(2, myCloset[1],
            ClothingItem(21, "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=200&h=260&fit=crop",
                "플로럴 미디 드레스", "& Other Stories", "S", "양호", users[1], "", emptyList()),
            users[1], ExchangeStatus.SHIPPING, "5시간 전"),
        MatchItem(3, myCloset[2],
            ClothingItem(22, "https://images.unsplash.com/photo-1542272604-787c3835535d?w=200&h=260&fit=crop",
                "데님 재킷", "Levi's", "L", "보통", users[2], "", emptyList()),
            users[2], ExchangeStatus.PENDING, "방금")
    )
}
