package com.fighteam.wannawear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AccentYellow,
    onPrimary = AccentYellowText,
    background = BgPrimary,
    surface = BgCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun WannaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
