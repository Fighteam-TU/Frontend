package com.fighteam.wannawear.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.MatchItem
import com.fighteam.wannawear.ui.screen.*
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.delay

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Discover      : Screen("discover",           "발견", Icons.Default.Search)
    object Matches       : Screen("matches",            "매칭", Icons.Default.Favorite)
    object Closet        : Screen("closet",             "옷장", Icons.Default.CheckCircle)
    object Profile       : Screen("profile",            "나",   Icons.Default.Person)
    object AddItem       : Screen("add_item",           "추가", Icons.Default.Add)
    object Chat          : Screen("chat/{matchId}",     "채팅", Icons.Default.ChatBubbleOutline)
    object ShippingGuide : Screen("shipping/{matchId}", "배송", Icons.Default.LocalShipping)
}

val bottomNavItems = listOf(Screen.Discover, Screen.Matches, Screen.Closet, Screen.Profile)

private val hideBottomBarPrefixes = listOf("add_item", "chat/", "shipping/")

@Composable
fun WannaWearNavGraph() {
    // ⚠️ 백엔드 로그인 연동 전 임시 처리: 로그인 화면을 건너뛰고 바로 로그인된 상태로 시작.
    //    백엔드 인증이 연동되면 false로 되돌리고 LoginScreen에서 실제 로그인 처리 필요.
    var isLoggedIn by remember { mutableStateOf(true) }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
        return
    }

    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route ?: ""
    val showBottomBar = hideBottomBarPrefixes.none { currentRoute.startsWith(it) }

    // 지연 알림
    var delayedMatchNotification by remember { mutableStateOf<MatchItem?>(null) }
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && AppState.pendingMatchNotifications.isNotEmpty()) {
            delay(3500L)
            delayedMatchNotification = AppState.consumePendingNotification()
        }
    }
    delayedMatchNotification?.let { match ->
        RealMatchPopup(match = match, onClose = { delayedMatchNotification = null })
    }

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = NavBgColor,
                    tonalElevation = 0.dp,
                    modifier       = Modifier.height(66.dp)
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(20.dp)) },
                            label = { Text(screen.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = AccentYellow,
                                selectedTextColor   = AccentYellow,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor      = NavBgColor
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Discover.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Discover.route) { DiscoverScreen() }
            composable(Screen.Matches.route) {
                MatchesScreen(
                    onOpenChat          = { matchId -> navController.navigate("chat/$matchId") },
                    onOpenShippingGuide = { matchId -> navController.navigate("shipping/$matchId") }
                )
            }
            composable(Screen.Closet.route) {
                ClosetScreen(onNavigateToAdd = { navController.navigate(Screen.AddItem.route) })
            }
            composable(Screen.Profile.route)  { ProfileScreen() }
            composable(Screen.AddItem.route)  { AddItemScreen(onBack = { navController.popBackStack() }) }
            composable("chat/{matchId}") { back ->
                val matchId = back.arguments?.getString("matchId")?.toIntOrNull() ?: return@composable
                ChatScreen(matchId = matchId, onBack = { navController.popBackStack() })
            }
            composable("shipping/{matchId}") { back ->
                val matchId = back.arguments?.getString("matchId")?.toIntOrNull() ?: return@composable
                ShippingGuideScreen(matchId = matchId, onBack = { navController.popBackStack() })
            }
        }
    }
}
