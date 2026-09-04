package com.xdown.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xdown.app.data.model.MediaItem
import com.xdown.app.ui.screens.home.HomeScreen
import com.xdown.app.ui.screens.home.HomeViewModel
import com.xdown.app.ui.screens.mediadetail.MediaDetailScreen
import com.xdown.app.ui.screens.settings.SettingsScreen
import com.xdown.app.ui.screens.splash.SplashScreen
import com.google.gson.Gson

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object MediaDetail : Screen("media_detail/{mediaJson}") {
        fun createRoute(mediaJson: String): String = "media_detail/$mediaJson"
    }
    object Settings : Screen("settings")
}

@Composable
fun XDownNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val gson = remember { Gson() }
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(
            onSplashComplete = {
                showSplash = false
            }
        )
    } else {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                    targetOffsetX = { it / 4 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { mediaItem ->
                        val mediaJson = gson.toJson(mediaItem)
                        navController.navigate(
                            Screen.MediaDetail.createRoute(
                                java.net.URLEncoder.encode(mediaJson, "UTF-8")
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.MediaDetail.route,
                arguments = listOf(
                    navArgument("mediaJson") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mediaJson = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("mediaJson") ?: "",
                    "UTF-8"
                )
                val mediaItem = gson.fromJson(mediaJson, MediaItem::class.java)

                MediaDetailScreen(
                    mediaItem = mediaItem,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
