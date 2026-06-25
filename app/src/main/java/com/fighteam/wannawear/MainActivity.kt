package com.fighteam.wannawear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.fighteam.wannawear.ui.navigation.WannaWearNavGraph
import com.fighteam.wannawear.ui.theme.WannaWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WannaWearTheme {
                WannaWearNavGraph()
            }
        }
    }
}
