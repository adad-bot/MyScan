package ir.scanfa.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.vm.ScannerViewModel

@Composable
fun AboutScreen(vm: ScannerViewModel, nav: NavHostController) {
    val context = LocalContext.current
    Scaffold(topBar = { ScreenBar("دربارهٔ MyScan", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MyScanLogo(size = 96.dp)
            Spacer(Modifier.height(12.dp))
            Text("MyScan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "اسکنر اسناد اندروید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "گروه فناوری کرانه ماندگار",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "علی دادمان",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "۰۹۳۹۴۷۱۱۰۴۹",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:09394711049"))
                                )
                            } catch (t: Throwable) {
                                // dialer در دسترس نیست
                            }
                        }) {
                            Icon(
                                Icons.Filled.Phone,
                                contentDescription = "تماس",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("این برنامه چه کار می‌کند؟", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Feature("اسکن خودکار با تشخیص زندهٔ قاب سند و ثبت هوشمند")
                    Feature("اصلاح پرسپکتیو، چرخش و چهار فیلتر تصویری")
                    Feature("پاک کردن هوشمند بخش‌های ناخواسته با انگشت")
                    Feature("متن‌خوانی فارسی روی دستگاه و ویرایش متن")
                    Feature("یادداشت صفحه و افزودن آن به سند")
                    Feature("خروجی PDF، PDF قابل جست‌وجو، Word، JPG و TXT با سقف حجم")
                    Feature("تبدیل PDF به Word و الگوهای مدارک فارسی")
                    Feature("تم روشن/تیره/سیستم، پنج پوستهٔ رنگی و بزرگ‌نمایی متن و آیکن‌ها")
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "نسخهٔ ۱.۰ — پردازش محلی، بدون حساب کاربری",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(Modifier.width(6.dp))
        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
