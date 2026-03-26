package com.pamurlykin.sportsactivityassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pamurlykin.sportsactivityassistant.ui.SportsActivityApp
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SportsActivityApplication
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(app.repository),
            )
            SportsActivityTheme {
                SportsActivityApp(viewModel = viewModel)
            }
        }
    }
}
