package ir.scanfa.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            NavHost(navController = nav, startDestination = "home") {
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
            }
        }
    }

    BusyOverlay(busy, progress)

    if (message != null) {
        androidx.compose.runtime.LaunchedEffect(message) {
            snackbar.showSnackbar(message ?: "")
            vm.takeMessage()
        }
    }
}
