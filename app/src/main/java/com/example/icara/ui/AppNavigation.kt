package com.example.icara.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.icara.ui.screens.dictionary.DictionaryDetailScreen
import com.example.icara.ui.screens.dictionary.DictionaryScreen
import com.example.icara.ui.screens.dictionary.SharedDictionaryViewModel
import com.example.icara.ui.screens.home.HomeScreen
import com.example.icara.ui.screens.onboarding.WelcomeScreen
import com.example.icara.ui.screens.splash.SplashScreen
import com.example.icara.ui.screens.talk.TalkScreen
import com.example.icara.utils.PreferencesManager

@Composable
fun MyAppNavHost(preferencesManager: PreferencesManager) {
    val navController = rememberNavController()
    val sharedViewModel: SharedDictionaryViewModel = viewModel()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            Log.d("NavHost", "Navigating to splash screen")
            SplashScreen(
                onNavigateNext = {
                    val isFirstLaunch = preferencesManager.isFirstLaunch()

                    val destination = if (isFirstLaunch) {
                        "onboarding"
                    } else {
                        "home"
                    }
                    navController.navigate(destination) {
                        // remove the splash screen from the back stack
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // Navigation to welcome screen
        composable("onboarding") {
            Log.d("NavHost", "Navigating to onboarding screen")
            WelcomeScreen(
                onComplete = {
                    Log.d("NavHost", "WelcomeScreen onComplete called")
                    preferencesManager.markOnboardingComplete()
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        // Navigation to home screen
        composable("home") {
            Log.d("NavHost", "Navigating to home screen")
            HomeScreen(navController = navController)
        }

        // Navigation to talk screen
        composable(
            route = "talk/{lang}",
            arguments = listOf(navArgument("lang") { type = NavType.StringType })
        ) { backStackEntry ->
            val lang = backStackEntry.arguments?.getString("lang") ?: ""
            TalkScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Navigation to dictionary screen
        composable("dictionary") {
            DictionaryScreen(
                onNavigateBack = { navController.popBackStack() },
                navController = navController,
                sharedViewModel = sharedViewModel,
            )
        }

        // Navigation to dictionary entry detail
        composable(
            route = "dictionary_detail",
        ) { backStackEntry ->
            DictionaryDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                sharedViewModel = sharedViewModel,
            )
        }
    }
}