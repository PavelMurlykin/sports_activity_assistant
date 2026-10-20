package com.pamurlykin.sportsactivityassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pamurlykin.sportsactivityassistant.ui.SportsActivityApp
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SportsActivityApplication
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(app.repository),
            )
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(0xE6F3F6F7.toInt(), 0xE610191F.toInt()) { darkTheme },
                )
            }
            SportsActivityTheme(themeMode) {
                SportsActivityApp(viewModel = viewModel)
            }
        }
    }
}
