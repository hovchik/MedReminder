package com.medreminder.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medreminder.presentation.screens.addmed.AddEditMedicationScreen
import com.medreminder.presentation.screens.adherence.AdherenceScreen
import com.medreminder.presentation.screens.caregiver.CaregiverScreen
import com.medreminder.presentation.screens.history.HistoryScreen
import com.medreminder.presentation.screens.home.HomeScreen
import com.medreminder.presentation.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddMedication : Screen("add_medication")
    data object EditMedication : Screen("edit_medication/{medicationId}") {
        fun createRoute(medicationId: Long) = "edit_medication/$medicationId"
    }
    data object Adherence : Screen("adherence")
    data object History : Screen("history")
    data object Caregiver : Screen("caregiver")
    data object Settings : Screen("settings")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Today", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Adherence, "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    BottomNavItem(Screen.History, "History", Icons.Filled.History, Icons.Outlined.History),
    BottomNavItem(Screen.Caregiver, "Family", Icons.Filled.People, Icons.Outlined.People),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedReminderNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(tonalElevation = 0.dp) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.screen.route) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAddMedication = { navController.navigate(Screen.AddMedication.route) },
                    onEditMedication = { id ->
                        navController.navigate(Screen.EditMedication.createRoute(id))
                    }
                )
            }
            composable(Screen.AddMedication.route) {
                AddEditMedicationScreen(
                    medicationId = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditMedication.route,
                arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getLong("medicationId")
                AddEditMedicationScreen(
                    medicationId = medicationId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Adherence.route) { AdherenceScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Caregiver.route) { CaregiverScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
