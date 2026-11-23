package ir.scanfa.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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

    var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var count = 0
                    var lastTime = 0L
                    var wasDown = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.any { it.pressed }
                        if (down && !wasDown) {
                            val now = System.currentTimeMillis()
                            if (now - lastTime > 800L) count = 0
                            count += 1
                            lastTime = now
                            if (count >= 3) {
                                count = 0
                                val route = nav.currentBackStackEntry?.destination?.route
                                helpTopic = Help.forRoute(route)
                            }
                        }
                        wasDown = down
                    }
                }
            }
    ) {
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
            composable("viewer/{docId}") { entry ->
                val id = entry.arguments?.getString("docId") ?: ""
                ViewerScreen(vm, nav, id)
            }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("templates") { TemplatesScreen(vm, nav) }
            composable("tools") { ToolsScreen(vm, nav) }
            composable("import") { ImportScreen(vm, nav) }
            composable("docs") { DocsScreen(vm, nav) }
            composable("calendar") { CalendarScreen(vm, nav) }
            composable("about") { AboutScreen(vm, nav) }
        }

        helpTopic?.let { topic ->
            HelpDialog(topic) { helpTopic = null }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)
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

@Composable
fun HelpDialog(topic: HelpTopic, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(26.dp),
        containerColor = scheme.surface,
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(topic.icon, contentDescription = null, tint = scheme.onPrimaryContainer)
            }
        },
        title = {
            Text(
                topic.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column {
                Text(
                    topic.body,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                topic.tips.forEach { tip ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("•", color = scheme.primary, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(tip, fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("متوجه شدم") }
        }
    )
}
