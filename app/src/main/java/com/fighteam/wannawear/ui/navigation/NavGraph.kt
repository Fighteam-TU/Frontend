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
import com.fighteam.wannawear.ui.screen.*
import com.fighteam.wannawear.ui.theme.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Discover : Screen("discover", "발견",  Icons.Default.Search)
    object Matches  : Screen("matches",  "매칭",  Icons.Default.Favorite)
    object Closet   : Screen("closet",   "옷장",  Icons.Default.CheckCircle)
    object Profile  : Screen("profile",  "나",    Icons.Default.Person)
}

val bottomNavItems = listOf(Screen.Discover, Screen.Matches, Screen.Closet, Screen.Profile)

@Composable
fun WannaWearNavGraph() {
    var isLoggedIn by remember { mutableStateOf(false) }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
        return
    }

    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            NavigationBar(
                containerColor = NavBgColor,
                tonalElevation = 0.dp,
                modifier = Modifier.height(66.dp)
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(20.dp)) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentYellow,
                            selectedTextColor = AccentYellow,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary,
                            indicatorColor = NavBgColor
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Discover.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Discover.route) { DiscoverScreen() }
            composable(Screen.Matches.route)  { MatchesScreen() }
            composable(Screen.Closet.route)   { ClosetScreen() }
            composable(Screen.Profile.route)  { ProfileScreen() }
        }
    }
}
