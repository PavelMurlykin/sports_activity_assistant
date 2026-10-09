package com.pamurlykin.sportsactivityassistant.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val items = listOf(
        Triple(Destination.Schedule.route, stringResource(R.string.nav_schedule), Icons.Rounded.CalendarMonth),
        Triple(Destination.Statistics.route, stringResource(R.string.nav_statistics), Icons.Rounded.QueryStats),
        Triple(Destination.SportsCenters.route, stringResource(R.string.nav_centers), Icons.Rounded.LocationOn),
        Triple(Destination.DataManagement.route, stringResource(R.string.nav_data), Icons.Rounded.Storage),
    )
    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(modifier) {
    if (showBottomBar && landscape) NavigationRail(Modifier.fillMaxHeight()) {
        items.forEach { (route, label, icon) ->
            NavigationRailItem(
                selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                onClick = { navigate(route) },
                icon = { Icon(icon, contentDescription = label) },
            )
        }
    }
    Scaffold(
        modifier = Modifier.weight(1f),
        bottomBar = {
            if (showBottomBar && !landscape) {
                NavigationBar {
                    items.forEach { (route, label, icon) ->
                        val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navigate(route)
                            },
                            icon = { Icon(imageVector = icon, contentDescription = null) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
}
