package ir.scanfa.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.scanfa.app.vm.ScannerViewModel

@Composable
fun AppNav(vm: ScannerViewModel) {
    val nav = rememberNavController()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val onboarded by vm.onboarding.collectAsState()

    if (onboarded) {
        OnboardingScreen(vm)
        return
    }

    val locked by vm.locked.collectAsState()
    val pinSetup by vm.pinSetup.collectAsState()

    if (locked) {
        LockScreen(vm, initialSetup = pinSetup)
        return
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "home",
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 }
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 10 }
            }
        ) {
            composable("home") { HomeScreen(vm, nav) }
            composable(
                route = "camera/{docId}",
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { entry ->
                CameraScreen(vm, nav, entry.arguments?.getString("docId").orEmpty())
            }
            composable(
                route = "editor/{docId}/{pageId}",
                arguments = listOf(
                    navArgument("docId") { type = NavType.StringType },
                    navArgument("pageId") { type = NavType.StringType }
                )
            ) { entry ->
                EditorScreen(
                    vm,
                    nav,
                    entry.arguments?.getString("docId").orEmpty(),
                    entry.arguments?.getString("pageId").orEmpty()
                )
            }
            composable(
                route = "doc/{docId}",
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { entry ->
                DocScreen(vm, nav, entry.arguments?.getString("docId").orEmpty())
            }
            composable(
                route = "ocr/{docId}/{pageId}",
                arguments = listOf(
                    navArgument("docId") { type = NavType.StringType },
                    navArgument("pageId") { type = NavType.StringType }
                )
            ) { entry ->
                OcrScreen(
                    vm,
                    nav,
                    entry.arguments?.getString("docId").orEmpty(),
                    entry.arguments?.getString("pageId").orEmpty()
                )
            }
            composable(
                route = "export/{docId}",
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { entry ->
                ExportScreen(vm, nav, entry.arguments?.getString("docId").orEmpty())
            }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("templates") { TemplatesScreen(vm, nav) }
            composable("tools") { ToolsScreen(vm, nav) }
            composable("docs") { DocsScreen(vm, nav) }
            composable("calendar") { CalendarScreen(vm, nav) }
            composable("about") { AboutScreen(vm, nav) }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )

        BusyOverlay(busy, progress)

        LaunchedEffect(message) {
            val text = message
            if (text != null) {
                snackbar.showSnackbar(text)
                vm.takeMessage()
            }
        }
    }
}
