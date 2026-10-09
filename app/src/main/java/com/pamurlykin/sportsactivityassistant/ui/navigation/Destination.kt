package com.pamurlykin.sportsactivityassistant.ui.navigation

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

sealed class Destination(val route: String) {
    data object Schedule : Destination("schedule")
    data object Statistics : Destination("statistics")
    data object SportsCenters : Destination("sports-centers")
    data object DataManagement : Destination("data-management")
    data object StatisticsDetails : Destination("statistics/{sportId}") {
        fun createRoute(sportId: Int): String = "statistics/${sportId}"
    }
}
