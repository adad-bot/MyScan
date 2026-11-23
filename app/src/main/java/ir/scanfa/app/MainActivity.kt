package ir.scanfa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.scanfa.app.ui.AppNav
import ir.scanfa.app.ui.LocalHapticsEnabled
import ir.scanfa.app.ui.ScannerTheme
import ir.scanfa.app.vm.ScannerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ScannerViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()
            val haptics by vm.haptics.collectAsState()
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                val window = window ?: return@SideEffect
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = !dark
                }
            }
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalHapticsEnabled provides haptics
            ) {
                ScannerTheme(mode = themeMode) {
                    AppNav(vm)
                }
            }
        }
    }
}
