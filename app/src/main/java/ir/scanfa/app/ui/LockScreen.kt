package ir.scanfa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.delay

private enum class LockMode { UNLOCK, SETUP1, SETUP2, MASTER, NEW1, NEW2 }

/**
 * صفحهٔ قفل: دایره‌های رمز، کیبورد مربعی، فراموشی رمز،
 * اثر انگشت و «Developed by Ali Dadman» در پایین صفحه.
 */
@Composable
fun LockScreen(vm: ScannerViewModel, initialSetup: Boolean) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scheme = MaterialTheme.colorScheme
    val biometricReady = vm.prefs.biometricEnabled && Biometric.available(context)

    var mode by remember { mutableStateOf(if (initialSetup || !vm.hasPin()) LockMode.SETUP1 else LockMode.UNLOCK) }
    var buffer by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var bioTried by remember { mutableStateOf(false) }

    val title = when (mode) {
        LockMode.UNLOCK -> "رمز ورود را وارد کن"
        LockMode.SETUP1 -> "یک رمز ۶ رقمی تعیین کن"
        LockMode.SETUP2 -> "رمز را دوباره وارد کن"
        LockMode.MASTER -> "کد مادر را وارد کن"
        LockMode.NEW1 -> "رمز جدید را وارد کن"
        LockMode.NEW2 -> "رمز جدید را تکرار کن"
    }
    val subtitle = when (mode) {
        LockMode.UNLOCK -> "رمز ۶ رقمی"
        LockMode.SETUP1, LockMode.NEW1 -> "مرحلهٔ ۱ از ۲"
        LockMode.SETUP2, LockMode.NEW2 -> "مرحلهٔ ۲ از ۲"
        LockMode.MASTER -> "کد مادر یا کد بازیابی"
    }

    fun submit(digits: String) {
        when (mode) {
            LockMode.UNLOCK -> if (vm.pinMatches(digits)) {
                buffer = ""
                error = ""
                vm.unlock()
            } else {
                error = "رمز اشتباه است"
                buffer = ""
            }
            LockMode.SETUP1, LockMode.NEW1 -> {
                firstPin = digits
                buffer = ""
                error = ""
                mode = if (mode == LockMode.SETUP1) LockMode.SETUP2 else LockMode.NEW2
            }
            LockMode.SETUP2, LockMode.NEW2 -> if (digits == firstPin) {
                buffer = ""
                error = ""
                vm.savePin(digits)
            } else {
                error = "رمزها یکسان نیستند؛ دوباره تلاش کن"
                buffer = ""
                firstPin = ""
                mode = LockMode.SETUP1
            }
            LockMode.MASTER -> if (vm.masterMatches(digits)) {
                buffer = ""
                error = ""
                mode = LockMode.NEW1
            } else {
                error = "کد مادر نادرست است"
                buffer = ""
            }
        }
    }

    fun press(key: String) {
        when (key) {
            "del" -> if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
            "clear" -> {
                buffer = ""
                error = ""
            }
            else -> {
                if (buffer.length >= 6) return
                buffer += key
                if (buffer.length == 6) submit(buffer)
            }
        }
    }

    fun biometric() {
        if (activity == null) return
        Biometric.prompt(activity, onSuccess = { vm.unlock() }, onError = { error = it })
    }

    LaunchedEffect(mode) {
        if (mode == LockMode.UNLOCK && !bioTried && biometricReady) {
            bioTried = true
            delay(450)
            biometric()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.background),
                    0f,
                    1400f
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))
            MyScanLogo(size = 92.dp)
            Spacer(Modifier.height(10.dp))
            Text("MyScan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text("علی دادمان", fontSize = 14.sp, color = scheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(6) { index ->
                    val filled = index < buffer.length
                    Box(
                        Modifier
                            .size(16.dp)
                            .then(
                                if (filled) Modifier.background(scheme.primary, CircleShape)
                                else Modifier.border(1.5.dp, scheme.outline, CircleShape)
                            )
                    )
                }
            }
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = scheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("clear", "0", "del")
            )
            keys.forEach { row ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Surface(
                                modifier = Modifier.size(66.dp).pressClick(requireDoubleTap = false) { press(key) },
                                shape = RoundedCornerShape(18.dp),
                                color = scheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
                                shadowElevation = 1.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when (key) {
                                        "del" -> Icon(Icons.Filled.Backspace, contentDescription = "حذف", tint = scheme.onSurfaceVariant)
                                        "clear" -> Text("پاک", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                                        else -> Text(
                                            ir.scanfa.app.util.Calendars.fa(key.toInt()),
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = scheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == LockMode.UNLOCK) {
                if (biometricReady) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().pressClick { biometric() },
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = scheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("ورود با اثر انگشت", fontSize = 13.sp, color = scheme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(onClick = {
                    buffer = ""
                    error = ""
                    mode = LockMode.MASTER
                }) {
                    Text("فراموشی رمز", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (mode == LockMode.SETUP1 && vm.pinSetup.value) {
                TextButton(onClick = { vm.cancelPinSetup() }) {
                    Text("انصراف", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MyScanLogo(size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Developed by Ali Dadman", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
