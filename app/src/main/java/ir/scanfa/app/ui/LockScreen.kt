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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.delay

private enum class LockMode { UNLOCK, SETUP1, SETUP2, MASTER, NEW1, NEW2 }

/**
 * صفحهٔ قفل به سبک MyC: رمز ۶ رقمی، کد مادر برای بازیابی و اثر انگشت.
 * کد مادر پیش‌فرض: ۱۳۷۵۱۳
 */
@Composable
fun LockScreen(vm: ScannerViewModel, initialSetup: Boolean) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
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
        LockMode.UNLOCK -> "برای باز کردن MyScan"
        LockMode.SETUP1, LockMode.NEW1 -> "مرحلهٔ ۱ از ۲"
        LockMode.SETUP2, LockMode.NEW2 -> "مرحلهٔ ۲ از ۲"
        LockMode.MASTER -> "کد بازیابی پیش‌فرض: ۱۳۷۵۱۳"
    }

    fun finishSuccess() {
        buffer = ""
        error = ""
    }

    fun submit(digits: String) {
        when (mode) {
            LockMode.UNLOCK -> {
                if (vm.pinMatches(digits)) {
                    finishSuccess()
                    vm.unlock()
                } else {
                    error = "رمز اشتباه است"
                    buffer = ""
                }
            }
            LockMode.SETUP1, LockMode.NEW1 -> {
                firstPin = digits
                buffer = ""
                error = ""
                mode = if (mode == LockMode.SETUP1) LockMode.SETUP2 else LockMode.NEW2
            }
            LockMode.SETUP2, LockMode.NEW2 -> {
                if (digits == firstPin) {
                    finishSuccess()
                    vm.savePin(digits)
                } else {
                    error = "رمزها یکسان نیستند؛ دوباره تلاش کن"
                    buffer = ""
                    firstPin = ""
                    mode = LockMode.SETUP1
                }
            }
            LockMode.MASTER -> {
                if (digits == "137513") {
                    buffer = ""
                    error = ""
                    mode = LockMode.NEW1
                } else {
                    error = "کد مادر نادرست است"
                    buffer = ""
                }
            }
        }
    }

    fun press(key: String) {
        when (key) {
            "del" -> { if (buffer.isNotEmpty()) buffer = buffer.dropLast(1) }
            "clear" -> { buffer = ""; error = "" }
            else -> {
                if (buffer.length >= 6) return
                buffer += key
                if (buffer.length == 6) {
                    val digits = buffer
                    submit(digits)
                }
            }
        }
    }

    fun biometric() {
        if (activity == null) return
        Biometric.prompt(
            activity,
            onSuccess = { vm.unlock() },
            onError = { error = it }
        )
    }

    LaunchedEffect(mode) {
        if (mode == LockMode.UNLOCK && !bioTried && vm.prefs.biometricEnabled && Biometric.available(context)) {
            bioTried = true
            delay(400)
            biometric()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F3B33), Color(0xFF08120F))))
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(44.dp))
            MyScanLogo(size = 86.dp)
            Spacer(Modifier.height(14.dp))
            Text("MyScan", color = Color(0xFFF3FFFA), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("علی دادمان", color = Color(0xFFB9E6D6), fontSize = 14.sp)
            Spacer(Modifier.height(26.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color(0xFF9FC9BC), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(6) { index ->
                    val filled = index < buffer.length
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(
                                if (filled) Color(0xFF5EEAD4) else Color.White.copy(alpha = 0.12f),
                                CircleShape
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    )
                }
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(error, color = Color(0xFFFCA5A5), fontSize = 13.sp)
            }

            Spacer(Modifier.height(18.dp))
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("clear", "0", "del")
            )
            keys.forEach { row ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        val label = when (key) {
                            "clear" -> "پاک"
                            "del" -> ""
                            else -> key
                        }
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .pressClick { press(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "del") {
                                Icon(Icons.Filled.Backspace, contentDescription = "حذف", tint = Color.White)
                            } else {
                                Text(
                                    label,
                                    color = Color.White,
                                    fontSize = if (key == "clear") 15.sp else 24.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mode == LockMode.UNLOCK && vm.prefs.biometricEnabled && Biometric.available(context)) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .background(Color.White.copy(alpha = 0.10f), CircleShape)
                            .pressClick { biometric() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = "اثر انگشت", tint = Color(0xFF5EEAD4))
                    }
                    Spacer(Modifier.width(16.dp))
                }
                if (mode == LockMode.UNLOCK) {
                    TextButton(onClick = {
                        buffer = ""
                        error = ""
                        mode = LockMode.MASTER
                    }) {
                        Text("رمز را فراموش کردی؟", color = Color(0xFFB9E6D6))
                    }
                }
                if (mode == LockMode.SETUP1 && vm.pinSetup.value) {
                    TextButton(onClick = { vm.cancelPinSetup() }) {
                        Text("انصراف", color = Color(0xFFB9E6D6))
                    }
                }
            }
        }
    }
}
