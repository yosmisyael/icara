package com.example.icara.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.icara.ui.screens.dictionary.DictionaryScreen
import com.example.icara.ui.screens.home.HomeScreen
import com.example.icara.ui.screens.talk.TalkScreen

@Composable
fun MyAppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController = navController)
        }

        // Navigation for talk screen
        composable(
            route = "talk/{lang}",
            arguments = listOf(navArgument("lang") { type = NavType.StringType })
        ) { backStackEntry ->
            val lang = backStackEntry.arguments?.getString("lang") ?: ""
            TalkScreen(
                lang = lang,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Navigation for dictionary screen
        composable("dictionary") {
            DictionaryScreen(
                onNavigateBack = { navController.popBackStack() },
                onEntryClick = { word ->
                    navController.navigate("dictionary_detail/$word")
                }
            )
        }

        // Navigation for dictionary entry detail
        composable(
            route = "dictionary_detail/{word}",
            arguments = listOf(navArgument("word") { type = NavType.StringType })
        ) { backStackEntry ->
            val word = backStackEntry.arguments?.getString("word") ?: ""
        }
    }
}