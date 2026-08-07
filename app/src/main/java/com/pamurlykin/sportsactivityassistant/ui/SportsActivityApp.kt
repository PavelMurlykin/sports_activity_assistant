package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pamurlykin.sportsactivityassistant.ui.navigation.Destination
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.screen.DataManagementScreen
import com.pamurlykin.sportsactivityassistant.ui.screen.ScheduleScreen
import com.pamurlykin.sportsactivityassistant.ui.screen.SportsCentersScreen
import com.pamurlykin.sportsactivityassistant.ui.screen.StatisticsDetailScreen
import com.pamurlykin.sportsactivityassistant.ui.screen.StatisticsScreen

@Composable
fun SportsActivityApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.hierarchy?.none { it.route == Destination.StatisticsDetails.route } != false

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val items = listOf(
                        Triple(Destination.Schedule.route, "Расписание", Icons.Rounded.CalendarMonth),
                        Triple(Destination.Statistics.route, "Статистика", Icons.Rounded.QueryStats),
                        Triple(Destination.SportsCenters.route, "Центры", Icons.Rounded.LocationOn),
                        Triple(Destination.DataManagement.route, "Данные", Icons.Rounded.Storage),
                    )
                    items.forEach { (route, label, icon) ->
                        val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Schedule.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Destination.Schedule.route) {
                ScheduleScreen(viewModel = viewModel)
            }
            composable(Destination.Statistics.route) {
                StatisticsScreen(
                    viewModel = viewModel,
                    onOpenSport = { sportId ->
                        navController.navigate(Destination.StatisticsDetails.createRoute(sportId))
                    },
                )
            }
            composable(Destination.SportsCenters.route) {
                SportsCentersScreen(viewModel = viewModel)
            }
            composable(Destination.DataManagement.route) {
                DataManagementScreen(viewModel = viewModel)
            }
            composable(
                route = Destination.StatisticsDetails.route,
                arguments = listOf(navArgument("sportId") { type = NavType.IntType }),
            ) { entry ->
                val sportId = entry.arguments?.getInt("sportId") ?: return@composable
                StatisticsDetailScreen(
                    sportId = sportId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
