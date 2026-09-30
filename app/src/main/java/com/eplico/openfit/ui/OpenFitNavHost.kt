package com.eplico.openfit.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eplico.openfit.ui.calendar.CalendarScreen
import com.eplico.openfit.ui.common.AppIcons
import com.eplico.openfit.ui.exercises.ExercisePickerScreen
import com.eplico.openfit.ui.log.ExerciseLogScreen
import com.eplico.openfit.ui.presets.PresetEditScreen
import com.eplico.openfit.ui.presets.PresetsScreen
import com.eplico.openfit.ui.settings.SettingsScreen
import com.eplico.openfit.ui.workout.WorkoutScreen
import java.time.LocalDate

private object Routes {
    const val WORKOUT = "workout"
    const val CALENDAR = "calendar"
    const val PRESETS = "presets"
    const val SETTINGS = "settings"
    const val LOG = "log/{workoutExerciseId}"
    const val PRESET_EDIT = "preset/{presetId}"
    const val PICK_FOR_WORKOUT = "pick/workout/{date}"
    const val PICK_FOR_PRESET = "pick/preset/{presetId}"

    fun log(workoutExerciseId: Long) = "log/$workoutExerciseId"
    fun presetEdit(presetId: Long) = "preset/$presetId"
    fun pickForWorkout(date: LocalDate) = "pick/workout/${date.toEpochDay()}"
    fun pickForPreset(presetId: Long) = "pick/preset/$presetId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.WORKOUT, "Workout", AppIcons.Dumbbell),
    Tab(Routes.CALENDAR, "Calendar", Icons.Filled.DateRange),
    Tab(Routes.PRESETS, "Presets", Icons.AutoMirrored.Filled.List),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

private fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenFitNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showTabs = tabs.any { tab -> destination?.route == tab.route }

    Scaffold(
        // Screens handle their own insets; this scaffold only reserves room for the tab bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = { navController.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WORKOUT,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    onAddExercise = { date -> navController.navigate(Routes.pickForWorkout(date)) },
                    onOpenEntry = { id -> navController.navigate(Routes.log(id)) },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(onOpenDay = { navController.openTab(Routes.WORKOUT) })
            }
            composable(Routes.PRESETS) {
                PresetsScreen(
                    onOpenPreset = { id -> navController.navigate(Routes.presetEdit(id)) },
                    onLoadedIntoToday = { navController.openTab(Routes.WORKOUT) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                Routes.LOG,
                arguments = listOf(navArgument("workoutExerciseId") { type = NavType.LongType }),
            ) {
                ExerciseLogScreen(onBack = { navController.navigateUp() })
            }
            composable(
                Routes.PRESET_EDIT,
                arguments = listOf(navArgument("presetId") { type = NavType.LongType }),
            ) {
                PresetEditScreen(
                    onBack = { navController.navigateUp() },
                    onAddExercise = { presetId -> navController.navigate(Routes.pickForPreset(presetId)) },
                )
            }
            composable(
                Routes.PICK_FOR_WORKOUT,
                arguments = listOf(navArgument("date") { type = NavType.LongType }),
            ) {
                ExercisePickerScreen(
                    onBack = { navController.navigateUp() },
                    onAddedToWorkout = { id ->
                        navController.navigate(Routes.log(id)) {
                            popUpTo(Routes.PICK_FOR_WORKOUT) { inclusive = true }
                        }
                    },
                    onAddedToPreset = { navController.navigateUp() },
                )
            }
            composable(
                Routes.PICK_FOR_PRESET,
                arguments = listOf(navArgument("presetId") { type = NavType.LongType }),
            ) {
                ExercisePickerScreen(
                    onBack = { navController.navigateUp() },
                    onAddedToWorkout = { },
                    onAddedToPreset = { navController.navigateUp() },
                )
            }
        }
    }
}
