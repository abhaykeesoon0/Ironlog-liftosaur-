package com.ironlog.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.padding
import com.ironlog.app.ui.screens.auth.RegisterScreen
import com.ironlog.app.ui.screens.auth.SignInScreen
import com.ironlog.app.ui.screens.body.BodyScreen
import com.ironlog.app.ui.screens.body.PhotosScreen
import com.ironlog.app.ui.screens.cardio.CardioScreen
import com.ironlog.app.ui.screens.cardio.CardioSessionScreen
import com.ironlog.app.ui.screens.cardio.CardioTrackScreen
import com.ironlog.app.ui.screens.exercises.ExerciseDetailScreen
import com.ironlog.app.ui.screens.exercises.ExerciseLibraryScreen
import com.ironlog.app.ui.screens.history.HistoryScreen
import com.ironlog.app.ui.screens.history.WorkoutDetailScreen
import com.ironlog.app.ui.screens.goals.StrengthGoalsScreen
import com.ironlog.app.ui.screens.coach.CoachScreen
import com.ironlog.app.ui.screens.exercises.OnlineLibraryScreen
import com.ironlog.app.ui.screens.coach.GenerateRoutineScreen
import com.ironlog.app.ui.screens.home.HomeScreen
import com.ironlog.app.ui.screens.rewards.QuestsScreen
import com.ironlog.app.ui.screens.rewards.StoreScreen
import com.ironlog.app.ui.screens.profile.ChangePasswordScreen
import com.ironlog.app.ui.screens.profile.ProfileScreen
import com.ironlog.app.ui.screens.profile.SettingsScreen
import com.ironlog.app.ui.screens.progress.ProgressScreen
import com.ironlog.app.ui.screens.routines.RoutineEditorScreen
import com.ironlog.app.ui.screens.routines.RoutinesScreen
import com.ironlog.app.ui.screens.workout.ActiveWorkoutScreen
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.screens.nutrition.NutritionScreen
import com.ironlog.app.ui.screens.nutrition.PlanScreen

object Routes {
    const val SIGN_IN = "auth/sign-in"
    const val REGISTER = "auth/register"

    const val HOME = "home"
    const val ROUTINES = "routines"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"

    const val ACTIVE_WORKOUT = "workout/active"
    const val ROUTINE_EDIT = "routines/edit?id={id}"
    const val EXERCISE_LIBRARY = "exercises?pick={pick}"
    const val EXERCISE_DETAIL = "exercises/{id}"
    const val WORKOUT_DETAIL = "history/{id}?justFinished={justFinished}"
    const val BODY = "body"
    const val STRENGTH_GOALS = "goals"
    const val QUESTS = "quests"
    const val COACH = "coach"
    const val ONLINE_LIBRARY = "exercises/online"
    const val GENERATE_ROUTINE = "routines/generate"
    const val STORE = "store"
    const val PHOTOS = "photos"
    const val CARDIO = "cardio"
    const val CARDIO_TRACK = "cardio/track"
    const val CARDIO_SESSION = "cardio/session/{id}"
    const val SETTINGS = "settings"
    const val NUTRITION = "nutrition"
    const val NUTRITION_PLAN = "nutrition/plan"
    const val CHANGE_PASSWORD = "settings/password"

    fun routineEdit(id: String?) = if (id == null) "routines/edit" else "routines/edit?id=$id"
    fun exerciseLibrary(pick: Boolean) = "exercises?pick=$pick"
    fun exerciseDetail(id: String) = "exercises/$id"
    fun workoutDetail(id: String, justFinished: Boolean = false) = "history/$id?justFinished=$justFinished"
    fun cardioSession(id: String) = "cardio/session/$id"

    /** Saved-state key the exercise picker writes its selection into. */
    const val PICKED_EXERCISES = "picked_exercises"
}

enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Default.Home),
    ROUTINES(Routes.ROUTINES, "Routines", Icons.Default.FitnessCenter),
    HISTORY(Routes.HISTORY, "History", Icons.Default.History),
    PROGRESS(Routes.PROGRESS, "Progress", Icons.Default.BarChart),
    PROFILE(Routes.PROFILE, "Profile", Icons.Default.Person)
}

@Composable
fun AuthNavHost(
    onAuthenticated: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.SIGN_IN) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignedIn = onAuthenticated,
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = onAuthenticated,
                onNavigateToSignIn = { navController.popBackStack() }
            )
        }
    }
}

/**
 * @param deepLinkRoute a route handed over from a notification tap; navigated to
 *   once and then reported back through [onDeepLinkConsumed].
 */
@Composable
fun MainNavHost(
    deepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar is hidden on full-screen flows: an active workout and a running
    // cardio session both need the whole screen and their own exit affordance.
    val showBottomBar = currentDestination?.route in BottomTab.entries.map { it.route }

    LaunchedEffect(deepLinkRoute) {
        val route = deepLinkRoute ?: return@LaunchedEffect
        runCatching { navController.navigate(route) { launchSingleTop = true } }
        onDeepLinkConsumed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Slate800, tonalElevation = 0.dp) {
                    BottomTab.entries.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Chalk,
                                selectedTextColor = Chalk,
                                unselectedIconColor = ChalkFaint,
                                unselectedTextColor = ChalkFaint,
                                indicatorColor = Slate700
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            // ------------------------------------------------------------ tabs
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenActiveWorkout = { navController.navigate(Routes.ACTIVE_WORKOUT) },
                    onOpenRoutines = { navController.navigateToTab(Routes.ROUTINES) },
                    onOpenCardio = { navController.navigate(Routes.CARDIO) },
                    onOpenWorkout = { navController.navigate(Routes.workoutDetail(it)) },
                    onOpenBody = { navController.navigate(Routes.BODY) },
                    onOpenGoals = { navController.navigate(Routes.STRENGTH_GOALS) },
                    onOpenQuests = { navController.navigate(Routes.QUESTS) },
                    onOpenCoach = { navController.navigate(Routes.COACH) },
                    onOpenNutrition = { navController.navigate(Routes.NUTRITION) }
                )
            }
            composable(Routes.ROUTINES) {
                RoutinesScreen(
                    onGenerateRoutine = { navController.navigate(Routes.GENERATE_ROUTINE) },
                    onEditRoutine = { navController.navigate(Routes.routineEdit(it)) },
                    onWorkoutStarted = { navController.navigate(Routes.ACTIVE_WORKOUT) },
                    onOpenLibrary = { navController.navigate(Routes.exerciseLibrary(pick = false)) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenWorkout = { navController.navigate(Routes.workoutDetail(it)) })
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) },
                    onOpenBody = { navController.navigate(Routes.BODY) },
                    onOpenCardio = { navController.navigate(Routes.CARDIO) }
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenBody = { navController.navigate(Routes.BODY) },
                    onOpenNutrition = { navController.navigate(Routes.NUTRITION) },
                    onOpenPhotos = { navController.navigate(Routes.PHOTOS) },
                    onOpenCardio = { navController.navigate(Routes.CARDIO) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenLibrary = { navController.navigate(Routes.exerciseLibrary(pick = false)) }
                )
            }

            // ------------------------------------------------------- training
            composable(Routes.ACTIVE_WORKOUT) { entry ->
                val picked by entry.pickedExercises()
                ActiveWorkoutScreen(
                    onClose = { navController.popBackStack() },
                    onFinished = { workoutId ->
                        navController.navigate(Routes.workoutDetail(workoutId, justFinished = true)) {
                            popUpTo(Routes.ACTIVE_WORKOUT) { inclusive = true }
                        }
                    },
                    onAddExercises = { navController.navigate(Routes.exerciseLibrary(pick = true)) },
                    onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) },
                    pickedExerciseIds = picked,
                    onPickedConsumed = { entry.savedStateHandle[Routes.PICKED_EXERCISES] = null }
                )
            }
            composable(
                Routes.ROUTINE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })
            ) { entry ->
                val picked by entry.pickedExercises()
                RoutineEditorScreen(
                    onBack = { navController.popBackStack() },
                    onAddExercises = { navController.navigate(Routes.exerciseLibrary(pick = true)) },
                    pickedExerciseIds = picked,
                    onPickedConsumed = { entry.savedStateHandle[Routes.PICKED_EXERCISES] = null }
                )
            }
            composable(
                Routes.EXERCISE_LIBRARY,
                arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })
            ) { entry ->
                val pickMode = entry.arguments?.getBoolean("pick") ?: false
                ExerciseLibraryScreen(
                    pickMode = pickMode,
                    onBack = { navController.popBackStack() },
                    onOpenOnline = { navController.navigate(Routes.ONLINE_LIBRARY) },
                    onPicked = { ids ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(Routes.PICKED_EXERCISES, ArrayList(ids))
                        navController.popBackStack()
                    },
                    onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) }
                )
            }
            composable(Routes.STRENGTH_GOALS) { entry ->
                val picked by entry.pickedExercises()
                StrengthGoalsScreen(
                    onBack = { navController.popBackStack() },
                    onPickExercise = { navController.navigate(Routes.exerciseLibrary(pick = true)) },
                    pickedExerciseIds = picked,
                    onPickedConsumed = { entry.savedStateHandle[Routes.PICKED_EXERCISES] = null }
                )
            }
            composable(Routes.ONLINE_LIBRARY) {
                OnlineLibraryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COACH) {
                CoachScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GENERATE_ROUTINE) {
                GenerateRoutineScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Routes.QUESTS) {
                QuestsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStore = { navController.navigate(Routes.STORE) }
                )
            }
            composable(Routes.STORE) {
                StoreScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EXERCISE_DETAIL) {
                ExerciseDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.WORKOUT_DETAIL,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("justFinished") { type = NavType.BoolType; defaultValue = false }
                )
            ) {
                WorkoutDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) }
                )
            }

            // --------------------------------------------------- body + cardio
            composable(Routes.BODY) { BodyScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.NUTRITION) {
                NutritionScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlan = { navController.navigate(Routes.NUTRITION_PLAN) }
                )
            }
            composable(Routes.NUTRITION_PLAN) { PlanScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PHOTOS) { PhotosScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.CARDIO) {
                CardioScreen(
                    onBack = { navController.popBackStack() },
                    onStartTracking = { navController.navigate(Routes.CARDIO_TRACK) },
                    onOpenSession = { navController.navigate(Routes.cardioSession(it)) }
                )
            }
            composable(Routes.CARDIO_TRACK) {
                CardioTrackScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.cardioSession(id)) {
                            popUpTo(Routes.CARDIO_TRACK) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.CARDIO_SESSION) {
                CardioSessionScreen(onBack = { navController.popBackStack() })
            }

            // ------------------------------------------------------- settings
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) }
                )
            }
            composable(Routes.CHANGE_PASSWORD) {
                ChangePasswordScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Tab navigation: single top, popping to the start, so the stack never grows per tap. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun NavBackStackEntry.pickedExercises() =
    savedStateHandle.getStateFlow<ArrayList<String>?>(Routes.PICKED_EXERCISES, null)
        .collectAsStateWithLifecycle()
