package ir.scanfa.app.ui

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.biometric.BiometricManager

/** دسترسی به اثر انگشت دستگاه (بدون نیاز به FragmentActivity). */
object Biometric {

    fun available(context: Context): Boolean {
        return try {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (t: Throwable) {
            false
        }
    }

    fun prompt(activity: Activity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("اثر انگشت روی این نسخهٔ اندروید پشتیبانی نمی‌شود")
            return
        }
        try {
            val executor = activity.mainExecutor
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("ورود به MyScan")
                .setSubtitle("انگشت را روی حسگر بگذار")
                .setNegativeButton("رمز", executor) { _, _ -> }
                .build()
            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                            errorCode != 13
                        ) {
                            onError(errString.toString())
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            onError("اثر انگشت در دسترس نیست")
        }
    }
}
