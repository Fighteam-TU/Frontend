package com.fighteam.wannawear.data

import com.fighteam.wannawear.data.model.ClothingCategory
import com.fighteam.wannawear.data.model.ClothingItem
import com.fighteam.wannawear.data.model.ReceivedLike
import com.fighteam.wannawear.data.model.User

/**
 * ⚠️ 테스트용 목업 데이터 — 실제 서버에는 전혀 저장되지 않고, 앱이 켜져있는 동안만
 * 발견 탭 / 받은 관심 탭에 보이는 "가짜" 데이터다. 실제 유저가 늘어나서 더 이상
 * 필요 없어지면:
 *   1) 아래 [ENABLE_MOCK_TEST_DATA] 를 false로 바꾸거나
 *   2) 이 파일을 통째로 지우고 AppState.kt의 seedMockTestData() 호출 한 줄만 제거하면 됨.
 *
 * id는 전부 음수(-)로 만들어서 실제 서버 아이템(항상 양수 id)과 절대 겹치지 않게 했다.
 * AppState의 likeItem/passItem/loadUserCloset/removeMyItem은 id<0 이면 실제 서버를
 * 호출하지 않고 로컬에서만 흉내내도록 분기돼있다 (가짜 id로 서버를 호출하면 404가 남).
 */
const val ENABLE_MOCK_TEST_DATA = true

val mockUsers = listOf(
    User(id = -201, name = "민지(목업)", age = 24,
        avatar = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=80&h=80&fit=crop"),
    User(id = -202, name = "태오(목업)", age = 27,
        avatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=80&h=80&fit=crop"),
    User(id = -203, name = "하은(목업)", age = 23,
        avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=80&h=80&fit=crop")
)

/** 발견 탭에 섞여 보일 목업 아이템 — "옷 추가" 테스트용 */
val mockDiscoverItems = listOf(
    ClothingItem(
        id = -101,
        image = "https://images.unsplash.com/photo-1551537482-f2075a1d41f2?w=420&h=700&fit=crop",
        name = "빈티지 레더 라이더 재킷(목업)", brand = "Schott NYC", size = "M", heightFit = "168~178cm",
        condition = "거의 새것", category = ClothingCategory.OUTER,
        description = "테스트용 목업 데이터입니다. 실제 서버에는 없는 아이템이에요.",
        user = mockUsers[0], tags = listOf("빈티지", "아우터")
    ),
    ClothingItem(
        id = -102,
        image = "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=420&h=700&fit=crop",
        name = "플로럴 미디 드레스(목업)", brand = "& Other Stories", size = "S", heightFit = "160~167cm",
        condition = "양호", category = ClothingCategory.DRESS,
        description = "테스트용 목업 데이터입니다.",
        user = mockUsers[1], tags = listOf("드레스", "봄/여름")
    ),
    ClothingItem(
        id = -103,
        image = "https://images.unsplash.com/photo-1542272604-787c3835535d?w=420&h=700&fit=crop",
        name = "501 오리지널 데님 재킷(목업)", brand = "Levi's", size = "L", heightFit = "170~180cm",
        condition = "보통", category = ClothingCategory.OUTER,
        description = "테스트용 목업 데이터입니다.",
        user = mockUsers[2], tags = listOf("데님", "아이코닉")
    )
)

/** 받은 관심 탭에 보일 목업 — 내 옷장과 무관한 가짜 "내 옷" 하나를 만들어서 붙인다 */
fun mockReceivedLikes(myProfile: User?): List<ReceivedLike> {
    val myUser = myProfile ?: User(0, "나", 0, "")
    val myMockItem = ClothingItem(
        id = -301,
        image = "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=200&h=260&fit=crop",
        name = "스트라이프 셔츠(목업)", brand = "COS", size = "M", condition = "양호",
        category = ClothingCategory.TOP, user = myUser, isListed = true
    )
    return listOf(
        ReceivedLike(fromUser = mockUsers[0], myItem = myMockItem),
        ReceivedLike(fromUser = mockUsers[1], myItem = myMockItem)
    )
}
