package vn.dichtructiep.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.dichtructiep.app
import vn.dichtructiep.ui.history.HistoryDetailScreen
import vn.dichtructiep.ui.history.HistoryScreen
import vn.dichtructiep.ui.home.HomeScreen
import vn.dichtructiep.ui.live.ConversationScreen
import vn.dichtructiep.ui.live.ListenScreen
import vn.dichtructiep.ui.settings.ModelsScreen
import vn.dichtructiep.ui.settings.SettingsScreen
import vn.dichtructiep.ui.text.TextTranslateScreen

object Routes {
    const val HOME = "home"
    const val LISTEN = "listen"
    const val CONVERSATION = "conversation"
    const val TEXT = "text"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val SETTINGS = "settings"
    const val MODELS = "models"
    fun historyDetail(id: String) = "history/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val app = LocalContext.current.app
    val shared by app.sharedText.collectAsState()

    // Văn bản chia sẻ từ app khác → mở màn hình Dịch văn bản
    LaunchedEffect(shared) {
        if (shared != null) {
            nav.navigate(Routes.TEXT) {
                launchSingleTop = true
            }
        }
    }

    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { nav.navigate(it) })
        }
        composable(Routes.LISTEN) {
            ListenScreen(onBack = back, onSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.CONVERSATION) {
            ConversationScreen(onBack = back, onSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.TEXT) {
            TextTranslateScreen(onBack = back)
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = back, onOpen = { nav.navigate(Routes.historyDetail(it)) })
        }
        composable(
            Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            HistoryDetailScreen(id = entry.arguments?.getString("id").orEmpty(), onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onModels = { nav.navigate(Routes.MODELS) })
        }
        composable(Routes.MODELS) {
            ModelsScreen(onBack = back)
        }
    }
}
