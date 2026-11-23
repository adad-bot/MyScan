package ir.scanfa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.scanfa.app.vm.ScannerViewModel

/** پیام خوش‌آمد یک‌باره: تعیین رمز و کد مادر در تنظیمات. */
@Composable
fun OnboardingScreen(vm: ScannerViewModel) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MyScanLogo(size = 92.dp)
            Spacer(Modifier.height(18.dp))
            Text("به MyScan خوش آمدی", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "برای امنیت اسناد، در تنظیمات یک رمز ۶ رقمی و کد مادر تعیین کن. " +
                    "کد بازیابی را نیز همان‌جا ببین و جایی امن نگه دار.",
                fontSize = 14.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(22.dp))
            Surface(
                color = scheme.primaryContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "تنظیمات ← قفل و امنیت",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(22.dp))
            Surface(
                color = scheme.primary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().pressClick { vm.dismissOnboarding() }
            ) {
                Text(
                    "باشه، متوجه شدم",
                    modifier = Modifier.padding(vertical = 14.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
