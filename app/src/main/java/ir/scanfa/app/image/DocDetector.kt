package ir.scanfa.app.image

import android.graphics.Bitmap

/**
 * تشخیص قاب سند؛ پیاده‌سازی اصلی در [SmartScan] است
 * (نمونه‌گیری رنگ پس‌زمینه، ماسک اختلاف رنگ و برازش خطوط مرزی).
 */
object DocDetector {

    fun detect(src: Bitmap): Quad =
        SmartScan.detectQuad(src) ?: Quad.inset(src.width, src.height, 0.04f)

    fun confidence(src: Bitmap): Float = SmartScan.detect(src).confidence
}
