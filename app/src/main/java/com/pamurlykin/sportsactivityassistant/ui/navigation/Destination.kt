package com.pamurlykin.sportsactivityassistant.ui.navigation

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

sealed class Destination(val route: String) {
    data object TrainingEntry : Destination("workout?date={date}&sportId={sportId}&trainingId={trainingId}&planKey={planKey}") {
        fun createRoute(date: java.time.LocalDate = java.time.LocalDate.now(), sportId: Int = 0, trainingId: Long = 0, planKey: String? = null): String =
            "workout?date=$date&sportId=$sportId&trainingId=$trainingId&planKey=${android.net.Uri.encode(planKey.orEmpty())}"
    }
    data object Schedule : Destination("schedule")
    data object Statistics : Destination("statistics")
    data object SportsCenters : Destination("sports-centers")
    data object DataManagement : Destination("data-management")
    data object StatisticsDetails : Destination("statistics/{sportId}") {
        fun createRoute(sportId: Int): String = "statistics/${sportId}"
    }
}
