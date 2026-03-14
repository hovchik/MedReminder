package com.example.medreminder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.medreminder.ui.addmedication.AddMedicationScreen
import com.example.medreminder.ui.main.MedicationListScreen

@Composable
fun MedReminderNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "medication_list") {
        composable("medication_list") {
            MedicationListScreen(
                onAddMedication = { navController.navigate("add_medication") },
                onMedicationClick = { id -> navController.navigate("edit_medication/$id") }
            )
        }
        composable("add_medication") {
            AddMedicationScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = "edit_medication/{medicationId}",
            arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
        ) {
            AddMedicationScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
