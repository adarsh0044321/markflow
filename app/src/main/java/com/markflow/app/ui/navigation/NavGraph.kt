package com.markflow.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.markflow.app.ui.history.HistoryScreen
import com.markflow.app.ui.home.HomeScreen
import com.markflow.app.ui.pageview.PageViewScreen
import com.markflow.app.ui.reports.ReportsScreen
import com.markflow.app.ui.review.ReviewScreen
import com.markflow.app.ui.scan.ScanScreen
import com.markflow.app.ui.settings.SettingsScreen
import com.markflow.app.ui.statistics.StatisticsScreen
import com.markflow.app.ui.summary.CopySummaryScreen

/**
 * Navigation route definitions for MarkFlow.
 */
object Routes {
    const val HOME = "home"
    const val SCAN = "scan/{sessionId}/{copyId}"
    const val REVIEW = "review/{copyId}"
    const val SUMMARY = "summary/{copyId}"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val STATISTICS = "statistics"
    const val PAGE_VIEW = "page_view/{pageId}"
    const val SETTINGS = "settings"

    fun scan(sessionId: Long, copyId: Long) = "scan/$sessionId/$copyId"
    fun review(copyId: Long) = "review/$copyId"
    fun summary(copyId: Long) = "summary/$copyId"
    fun pageView(pageId: Long) = "page_view/$pageId"
}

@Composable
fun MarkFlowNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(200))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartScan = { sessionId, copyId ->
                    navController.navigate(Routes.scan(sessionId, copyId))
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToReports = {
                    navController.navigate(Routes.REPORTS)
                },
                onNavigateToStatistics = {
                    navController.navigate(Routes.STATISTICS)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToCopy = { copyId ->
                    navController.navigate(Routes.summary(copyId))
                }
            )
        }

        composable(
            route = Routes.SCAN,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("copyId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            val copyId = backStackEntry.arguments?.getLong("copyId") ?: return@composable
            ScanScreen(
                sessionId = sessionId,
                copyId = copyId,
                onFinish = { finishedCopyId, isSaved ->
                    if (isSaved) {
                        navController.navigate(Routes.summary(finishedCopyId)) {
                            popUpTo(Routes.HOME)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument("copyId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val copyId = backStackEntry.arguments?.getLong("copyId") ?: return@composable
            ReviewScreen(
                copyId = copyId,
                onBack = { navController.popBackStack() },
                onNavigateToPage = { pageId ->
                    navController.navigate(Routes.pageView(pageId))
                }
            )
        }

        composable(
            route = Routes.SUMMARY,
            arguments = listOf(
                navArgument("copyId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val copyId = backStackEntry.arguments?.getLong("copyId") ?: return@composable
            CopySummaryScreen(
                copyId = copyId,
                onBack = { navController.popBackStack() },
                onViewPages = { navController.navigate(Routes.review(copyId)) },
                onNavigateToPage = { pageId ->
                    navController.navigate(Routes.pageView(pageId))
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onCopyClick = { copyId ->
                    navController.navigate(Routes.summary(copyId))
                }
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STATISTICS) {
            StatisticsScreen(
                onBack = { navController.popBackStack() },
                onCopyClick = { copyId ->
                    navController.navigate(Routes.summary(copyId))
                }
            )
        }

        composable(
            route = Routes.PAGE_VIEW,
            arguments = listOf(
                navArgument("pageId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getLong("pageId") ?: return@composable
            PageViewScreen(
                pageId = pageId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
