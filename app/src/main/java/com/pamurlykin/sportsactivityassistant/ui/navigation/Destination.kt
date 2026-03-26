package com.pamurlykin.sportsactivityassistant.ui.navigation

sealed class Destination(val route: String) {
    data object Schedule : Destination("schedule")
    data object Statistics : Destination("statistics")
    data object StatisticsDetails : Destination("statistics/{sportId}") {
        fun createRoute(sportId: Int): String = "statistics/$sportId"
    }
}
