package com.arrow3d.game.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.game.presentation.game.GameScreenRoot
import com.arrow3d.game.presentation.select.ModeSelectScreenRoot

private object Routes {
    const val SELECT = "select"
    const val GAME = "game/{mode}"
    fun game(difficulty: Difficulty) = "game/${difficulty.name}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SELECT) {
        composable(Routes.SELECT) {
            ModeSelectScreenRoot(onOpenGame = { navController.navigate(Routes.game(it)) })
        }
        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val difficulty = Difficulty.valueOf(entry.arguments?.getString("mode") ?: Difficulty.EASY.name)
            GameScreenRoot(difficulty = difficulty, onBack = { navController.popBackStack() })
        }
    }
}
