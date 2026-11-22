package ir.scanfa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.scanfa.app.ui.AppNav
import ir.scanfa.app.ui.ScannerTheme
import ir.scanfa.app.vm.ScannerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ScannerViewModel = viewModel()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ScannerTheme {
                    AppNav(vm)
                }
            }
        }
    }
}
