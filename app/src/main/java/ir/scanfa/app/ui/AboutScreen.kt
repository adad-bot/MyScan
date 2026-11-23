package ir.scanfa.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.vm.ScannerViewModel

@Composable
fun AboutScreen(vm: ScannerViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    Scaffold(topBar = { ScreenBar("درباره", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            MyScanLogo(size = 96.dp)
            Spacer(Modifier.height(10.dp))
            Text("MyScan", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("اسکنر اسناد و متن‌خوان فارسی", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "گروه فناوری کرانه ماندگار",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "علی دادمان",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surface,
                        modifier = Modifier.pressClick {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:09394711049")))
                            } catch (t: Throwable) {
                                // dialer در دسترس نیست
                            }
                        }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "تماس", tint = scheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "۰۹۳۹۴۷۱۱۰۴۹",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("دربارهٔ MyScan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "MyScan اسناد کاغذی را به فایل تمیز و قابل جست‌وجو تبدیل می‌کند: " +
                            "قاب سند را زنده تشخیص می‌دهد، پرسپکتیو را اصلاح می‌کند، متن فارسی را " +
                            "روی دستگاه می‌خواند و خروجی PDF، Word و تصویر می‌سازد. " +
                            "همهٔ پردازش‌ها محلی است و هیچ سندی به اینترنت فرستاده نمی‌شود.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = scheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("امکانات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Feature("تشخیص خودکار قاب سند و ثبت هوشمند")
                    Feature("تنظیم دستی چهار گوشهٔ سند")
                    Feature("اصلاح پرسپکتیو، چرخش و فیلترهای رنگی")
                    Feature("پاک‌کردن هوشمند بخش‌های ناخواسته")
                    Feature("متن‌خوانی فارسی روی دستگاه و ویرایش متن")
                    Feature("یادداشت صفحه و افزودن به سند")
                    Feature("خروجی PDF، PDF جست‌وجوپذیر، Word، JPG و TXT")
                    Feature("تبدیل PDF به Word و جست‌وجوی جامع در اسناد")
                    Feature("تقویم شمسی/میلادی/قمری با مناسبت‌ها")
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyScanLogo(size = 20.dp)
                Spacer(Modifier.width(6.dp))
                Text("Developed by Ali Dadman", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
