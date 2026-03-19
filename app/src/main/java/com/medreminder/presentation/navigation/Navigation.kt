package com.medreminder.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medreminder.R
import com.medreminder.presentation.screens.addmed.AddEditMedicationScreen
import com.medreminder.presentation.screens.adherence.AdherenceScreen
import com.medreminder.presentation.screens.caregiver.CaregiverScreen
import com.medreminder.presentation.screens.history.HistoryScreen
import com.medreminder.presentation.screens.home.HomeScreen
import com.medreminder.presentation.screens.ocr.OcrScannerScreen
import com.medreminder.presentation.screens.onboarding.OnboardingScreen
import com.medreminder.presentation.screens.schedules.SchedulesScreen
import com.medreminder.ai.setupwizard.LocalAiSetupWizard
import com.medreminder.presentation.screens.insights.AiInsightsScreen
import com.medreminder.presentation.screens.medanalysis.MedicationAnalysisScreen
import com.medreminder.presentation.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object AddMedication : Screen("add_medication")
    data object EditMedication : Screen("edit_medication/{medicationId}") {
        fun createRoute(medicationId: Long) = "edit_medication/$medicationId"
    }
    data object AddMedicationFromScan : Screen("add_medication_scan?name={name}&dosage={dosage}") {
        fun createRoute(name: String, dosage: String): String {
            val encodedName = java.net.URLEncoder.encode(name.ifBlank { " " }, "UTF-8")
            val encodedDosage = java.net.URLEncoder.encode(dosage.ifBlank { " " }, "UTF-8")
            return "add_medication_scan?name=$encodedName&dosage=$encodedDosage"
        }
    }
    data object OcrScanner : Screen("ocr_scanner")
    data object Schedules : Screen("schedules")
    data object Adherence : Screen("adherence")
    data object History : Screen("history")
    data object Caregiver : Screen("caregiver")
    data object Settings : Screen("settings")
    data object LocalAiSetup : Screen("local_ai_setup")
    data object AiInsights : Screen("ai_insights")
    data object MedicationAnalysis : Screen("medication_analysis/{medicationId}") {
        fun createRoute(medicationId: Long) = "medication_analysis/$medicationId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_today, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Schedules, R.string.nav_schedules, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomNavItem(Screen.History, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    BottomNavItem(Screen.Caregiver, R.string.nav_family, Icons.Filled.People, Icons.Outlined.People),
    BottomNavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedReminderNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mainRoutes = bottomNavItems.map { it.screen.route }
    val showBottomBar = currentRoute in mainRoutes

    // Determine start destination via the StartDestinationViewModel
    val startViewModel: StartDestinationViewModel = hiltViewModel()
    val startDestination by startViewModel.startDestination.collectAsState()

    // Wait for start destination to be resolved
    if (startDestination == null) return

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
                                    contentDescription = stringResource(item.labelResId)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(item.labelResId),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            },
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
            startDestination = startDestination!!,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onAddMedication = { navController.navigate(Screen.AddMedication.route) },
                    onNavigateToInsights = { navController.navigate(Screen.AiInsights.route) }
                )
            }
            composable(Screen.AddMedication.route) {
                AddEditMedicationScreen(
                    medicationId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onScanMedication = { navController.navigate(Screen.OcrScanner.route) }
                )
            }
            composable(
                route = Screen.EditMedication.route,
                arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getLong("medicationId")
                AddEditMedicationScreen(
                    medicationId = medicationId,
                    onNavigateBack = { navController.popBackStack() },
                    onScanMedication = { navController.navigate(Screen.OcrScanner.route) },
                    onViewAnalysis = { id ->
                        navController.navigate(Screen.MedicationAnalysis.createRoute(id))
                    }
                )
            }
            composable(
                route = Screen.AddMedicationFromScan.route,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("dosage") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val name = (backStackEntry.arguments?.getString("name") ?: "").trim()
                val dosage = (backStackEntry.arguments?.getString("dosage") ?: "").trim()
                AddEditMedicationScreen(
                    medicationId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onScanMedication = { navController.navigate(Screen.OcrScanner.route) },
                    scannedName = name,
                    scannedDosage = dosage
                )
            }
            composable(Screen.OcrScanner.route) {
                OcrScannerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onMedicationScanned = { name, dosage ->
                        navController.popBackStack()
                        navController.navigate(Screen.AddMedicationFromScan.createRoute(name, dosage))
                    }
                )
            }
            composable(Screen.Schedules.route) {
                SchedulesScreen(
                    onAddMedication = { navController.navigate(Screen.AddMedication.route) },
                    onEditMedication = { id ->
                        navController.navigate(Screen.EditMedication.createRoute(id))
                    }
                )
            }
            composable(Screen.Adherence.route) { AdherenceScreen() }
            composable(Screen.AiInsights.route) {
                AiInsightsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Caregiver.route) { CaregiverScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAiSetup = { navController.navigate(Screen.LocalAiSetup.route) }
                )
            }
            composable(Screen.LocalAiSetup.route) {
                LocalAiSetupWizard(
                    onDismiss = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.MedicationAnalysis.route,
                arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: 0L
                MedicationAnalysisScreen(
                    medicationId = medicationId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
