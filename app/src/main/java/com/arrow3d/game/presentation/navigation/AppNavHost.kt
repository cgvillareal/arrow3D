package com.arrow3d.game.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.presentation.achievements.AchievementsScreenRoot
import com.arrow3d.game.presentation.game.GameScreenRoot
import com.arrow3d.game.presentation.records.RecordsScreenRoot
import com.arrow3d.game.presentation.select.ModeSelectScreenRoot

private object Routes {
    const val SELECT = "select"
    const val GAME = "game/{mode}"
    const val RECORDS = "records"
    const val ACHIEVEMENTS = "achievements"
    fun game(mode: PlayMode) = "game/${mode.key}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SELECT) {
        composable(Routes.SELECT) {
            ModeSelectScreenRoot(
                onOpenGame = { navController.navigate(Routes.game(it)) },
                onOpenRecords = { navController.navigate(Routes.RECORDS) },
                onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
            )
        }
        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val mode = PlayMode.fromKey(entry.arguments?.getString("mode").orEmpty())
            GameScreenRoot(mode = mode, onBack = { navController.popBackStack() })
        }
        composable(Routes.RECORDS) {
            RecordsScreenRoot(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACHIEVEMENTS) {
            AchievementsScreenRoot(onBack = { navController.popBackStack() })
        }
    }
}
