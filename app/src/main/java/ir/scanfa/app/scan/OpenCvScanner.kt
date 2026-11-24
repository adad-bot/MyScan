package ir.scanfa.app.scan

import android.graphics.Bitmap
import android.graphics.PointF
import ir.scanfa.app.image.Quad
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * پردازش سند با OpenCV: تشخیص کانتور و چهارضلعی، اصلاح پرسپکتیو،
 * حذف سایه با تقسیم بر پس‌زمینهٔ محو، CLAHE، اشباع رنگ و تیزکردن.
 */
object OpenCvScanner {

    private var ready = false
    private var checked = false

    fun isReady(): Boolean {
        if (!checked) {
            checked = true
            ready = try {
                OpenCVLoader.initLocal()
            } catch (t: Throwable) {
                false
            }
        }
        return ready
    }

    data class Detection(val quad: Quad?, val confidence: Float)

    fun detect(src: Bitmap, workMax: Int = 900): Detection {
        if (!isReady()) return Detection(null, 0f)
        return try {
            val full = Mat()
            Utils.bitmapToMat(src, full)
            val scale = workMax.toFloat() / max(src.width, src.height)
            val work = Mat()
            Imgproc.resize(full, work, Size((src.width * scale).toDouble(), (src.height * scale).toDouble()))
            full.release()
            val workWidth = work.cols()
            val workHeight = work.rows()

            val gray = Mat()
            Imgproc.cvtColor(work, gray, Imgproc.COLOR_RGBA2GRAY)
            work.release()

            val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
            val enhanced = Mat()
            clahe.apply(gray, enhanced)
            gray.release()

            val blur = Mat()
            Imgproc.GaussianBlur(enhanced, blur, Size(5.0, 5.0), 0.0)
            enhanced.release()

            var best: Quad? = null
            var bestArea = 0.0
            val thresholds = listOf(20 to 60, 40 to 120, 60 to 160)
            for ((low, high) in thresholds) {
                val edges = Mat()
                Imgproc.Canny(blur, edges, low.toDouble(), high.toDouble())
                val border = 4
                Imgproc.rectangle(edges, org.opencv.core.Rect(0, 0, edges.cols(), border), Scalar(0.0), -1)
                Imgproc.rectangle(
                    edges,
                    org.opencv.core.Rect(0, edges.rows() - border, edges.cols(), border),
                    Scalar(0.0),
                    -1
                )
                Imgproc.rectangle(edges, org.opencv.core.Rect(0, 0, border, edges.rows()), Scalar(0.0), -1)
                Imgproc.rectangle(
                    edges,
                    org.opencv.core.Rect(edges.cols() - border, 0, border, edges.rows()),
                    Scalar(0.0),
                    -1
                )
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
                val contours = ArrayList<MatOfPoint>()
                Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                edges.release()
                contours.sortByDescending { Imgproc.contourArea(it) }
                for (c in contours.take(8)) {
                    val c2f = MatOfPoint2f(*c.toArray())
                    val peri = Imgproc.arcLength(c2f, true)
                    for (eps in listOf(0.02, 0.03, 0.05, 0.08)) {
                        val approx = MatOfPoint2f()
                        Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
                        if (approx.total() == 4L) {
                            val pts = approx.toArray()
                            val area = abs(Imgproc.contourArea(approx))
                            val ratio = area / (workWidth * workHeight)
                            if (ratio in 0.15..0.92 && !touchesBorders(pts, workWidth, workHeight)) {
                                val sides = sideLengths(pts)
                                if (sides.min() > min(workWidth, workHeight) * 0.15 && area > bestArea) {
                                    bestArea = area
                                    best = order(pts)
                                }
                            }
                        }
                        approx.release()
                    }
                    c2f.release()
                }
                contours.forEach { it.release() }
            }
            blur.release()
            val found = best ?: return Detection(null, 0f)

            val sx = src.width.toFloat() / workWidth
            val sy = src.height.toFloat() / workHeight
            val scaled = Quad(
                PointF(found.tl.x * sx, found.tl.y * sy),
                PointF(found.tr.x * sx, found.tr.y * sy),
                PointF(found.br.x * sx, found.br.y * sy),
                PointF(found.bl.x * sx, found.bl.y * sy)
            )
            Detection(scaled, 0.95f)
        } catch (t: Throwable) {
            Detection(null, 0f)
        }
    }

    private fun sideLengths(points: Array<Point>): List<Double> {
        val list = ArrayList<Double>(4)
        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            list.add(kotlin.math.hypot(a.x - b.x, a.y - b.y))
        }
        return list
    }

    private fun order(points: Array<Point>): Quad {
        val cx = points.sumOf { it.x } / points.size
        val cy = points.sumOf { it.y } / points.size
        val sorted = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        var startIndex = 0
        var best = Double.MAX_VALUE
        sorted.forEachIndexed { index, p ->
            val sum = p.x + p.y
            if (sum < best) {
                best = sum
                startIndex = index
            }
        }
        val rotated = List(4) { sorted[(startIndex + it) % 4] }
        val area2 = (rotated[0].x * rotated[1].y - rotated[1].x * rotated[0].y) +
            (rotated[1].x * rotated[2].y - rotated[2].x * rotated[1].y) +
            (rotated[2].x * rotated[3].y - rotated[3].x * rotated[2].y) +
            (rotated[3].x * rotated[0].y - rotated[0].x * rotated[3].y)
        val ordered = if (area2 < 0) listOf(rotated[0], rotated[3], rotated[2], rotated[1]) else rotated
        return Quad(
            PointF(ordered[0].x.toFloat(), ordered[0].y.toFloat()),
            PointF(ordered[1].x.toFloat(), ordered[1].y.toFloat()),
            PointF(ordered[2].x.toFloat(), ordered[2].y.toFloat()),
            PointF(ordered[3].x.toFloat(), ordered[3].y.toFloat())
        )
    }

    private fun touchesBorders(points: Array<Point>, w: Int, h: Int): Boolean {
        var touching = 0
        for (p in points) {
            val nearLeft = p.x <= w * 0.01
            val nearRight = p.x >= w * 0.99
            val nearTop = p.y <= h * 0.01
            val nearBottom = p.y >= h * 0.99
            if (nearLeft || nearRight || nearTop || nearBottom) touching++
        }
        return touching >= 3
    }


    /**
     * صاف‌کردن صفحهٔ خمیده و چرخیده:
     *  ۱) ماسک کاغذ و استخراج منحنی لبهٔ بالا/پایین
     *  ۲) برآورد زاویهٔ چرخش از شیب لبهٔ بالا و چرخش تصویر
     *  ۳) محاسبهٔ مجدد منحنی‌ها روی تصویر صاف‌شده
     *  ۴) remap ستونی برای یکسان‌سازی ارتفاع هر ستون (صاف‌کردن قوس)
     */
    fun unwarp(src: Bitmap, maxSide: Int = 2200): Bitmap? {
        if (!isReady()) return null
        return try {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)
            val rgb = Mat()
            Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)
            mat.release()

            var profile = columnProfile(rgb) ?: return null
            val angle = estimateSkew(rgb)
            if (abs(angle) > 1.0) {
                val rotated = rotate(rgb, -angle)
                rgb.release()
                val newProfile = columnProfile(rotated)
                if (newProfile != null) {
                    profile = newProfile
                } else {
                    return null
                }
                val result = remapColumns(rotated, profile, maxSide)
                rotated.release()
                return result
            }

            val result = remapColumns(rgb, profile, maxSide)
            rgb.release()
            result
        } catch (t: Throwable) {
            null
        }
    }


    /** برآورد زاویهٔ چرخش صفحه از خطوط متن (HoughLinesP). */
    fun estimateSkew(rgb: Mat): Float {
        val scale = (700.0 / max(rgb.cols(), rgb.rows())).coerceAtMost(1.0)
        val small = Mat()
        Imgproc.resize(rgb, small, Size(rgb.cols() * scale, rgb.rows() * scale))
        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGB2GRAY)
        small.release()
        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
        gray.release()
        val edges = Mat()
        Imgproc.Canny(blur, edges, 30.0, 90.0)
        blur.release()
        val lines = Mat()
        Imgproc.HoughLinesP(
            edges,
            lines,
            1.0,
            Math.PI / 180.0,
            60,
            (edges.cols() * 0.3),
            12.0
        )
        edges.release()
        if (lines.empty()) return 0f

        val angles = ArrayList<Float>()
        for (i in 0 until lines.rows()) {
            val v = lines.get(i, 0) ?: continue
            if (v.size < 4) continue
            val dx = v[2] - v[0]
            val dy = v[3] - v[1]
            if (abs(dx) < 1e-3) continue
            val angle = Math.toDegrees(kotlin.math.atan(dy / dx)).toFloat()
            if (abs(angle) < 25f) angles.add(angle)
        }
        lines.release()
        if (angles.size < 5) return 0f
        angles.sort()
        return angles[angles.size / 2]
    }

    private data class ColumnProfile(
        val width: Int,
        val height: Int,
        val top: FloatArray,
        val bottom: FloatArray,
        val valid: List<Int>
    )

    private fun columnProfile(rgb: Mat, workMax: Int = 900): ColumnProfile? {
        val scale = (workMax.toDouble() / max(rgb.cols(), rgb.rows())).coerceAtMost(1.0)
        val small = Mat()
        Imgproc.resize(rgb, small, Size(rgb.cols() * scale, rgb.rows() * scale))
        val hsv = Mat()
        Imgproc.cvtColor(small, hsv, Imgproc.COLOR_RGB2HSV)
        val mask = Mat()
        Core.inRange(hsv, Scalar(0.0, 0.0, 110.0), Scalar(180.0, 85.0, 255.0), mask)
        hsv.release()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        mask.release()
        if (contours.isEmpty()) return null
        val cx = small.cols() / 2.0
        val cy = small.rows() / 2.0
        val largest = contours
            .filter { Imgproc.pointPolygonTest(MatOfPoint2f(*it.toArray()), Point(cx, cy), false) >= 0 }
            .maxByOrNull { Imgproc.contourArea(it) }
            ?: contours.maxByOrNull { Imgproc.contourArea(it) }
            ?: return null
        if (Imgproc.contourArea(largest) < small.cols() * small.rows() * 0.12) return null

        val filled = Mat.zeros(small.rows(), small.cols(), CvType.CV_8U)
        Imgproc.drawContours(filled, listOf(largest), -1, Scalar(255.0), -1)
        val data = ByteArray(small.cols() * small.rows())
        filled.get(0, 0, data)
        filled.release()

        val top = FloatArray(small.cols()) { -1f }
        val bottom = FloatArray(small.cols()) { -1f }
        for (x in 0 until small.cols()) {
            var first = -1
            var last = -1
            for (y in 0 until small.rows()) {
                if ((data[y * small.cols() + x].toInt() and 0xFF) > 0) {
                    if (first < 0) first = y
                    last = y
                }
            }
            if (first >= 0) {
                top[x] = first.toFloat()
                bottom[x] = last.toFloat()
            }
        }

        val valid = (0 until small.cols()).filter {
            top[it] >= 0 && bottom[it] - top[it] > small.rows() * 0.3
        }
        if (valid.size < small.cols() * 0.3) return null

        // نرم‌کردن منحنی‌ها
        val window = 21
        val smoothTop = FloatArray(small.cols())
        val smoothBottom = FloatArray(small.cols())
        for (x in 0 until small.cols()) {
            var sumT = 0f
            var sumB = 0f
            var count = 0
            for (k in -window..window) {
                val xx = (x + k).coerceIn(0, small.cols() - 1)
                if (top[xx] >= 0) {
                    sumT += top[xx]
                    sumB += bottom[xx]
                    count++
                }
            }
            smoothTop[x] = if (count > 0) sumT / count else -1f
            smoothBottom[x] = if (count > 0) sumB / count else -1f
        }
        val sw = small.cols()
        val sh = small.rows()
        small.release()
        return ColumnProfile(sw, sh, smoothTop, smoothBottom, valid)
    }

    private fun rotationAngle(profile: ColumnProfile): Float {
        // شیب خط لبهٔ بالا
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        var n = 0
        for (x in profile.valid) {
            val y = profile.top[x]
            if (y < 0) continue
            sumX += x
            sumY += y
            sumXX += x.toDouble() * x
            sumXY += x.toDouble() * y
            n++
        }
        if (n < 20) return 0f
        val den = n * sumXX - sumX * sumX
        if (abs(den) < 1e-6) return 0f
        val slope = (n * sumXY - sumX * sumY) / den
        return Math.toDegrees(kotlin.math.atan(slope)).toFloat()
    }

    private fun rotate(src: Mat, angleDeg: Float): Mat {
        val center = Point(src.cols() / 2.0, src.rows() / 2.0)
        val matrix = Imgproc.getRotationMatrix2D(center, angleDeg.toDouble(), 1.0)
        val out = Mat()
        Imgproc.warpAffine(
            src,
            out,
            matrix,
            Size(src.cols().toDouble(), src.rows().toDouble()),
            Imgproc.INTER_CUBIC,
            Core.BORDER_REPLICATE
        )
        matrix.release()
        return out
    }

    private fun remapColumns(rgb: Mat, profile: ColumnProfile, maxSide: Int): Bitmap? {
        val scaleX = rgb.cols().toDouble() / profile.width
        val scaleY = rgb.rows().toDouble() / profile.height
        val valid = profile.valid
        if (valid.size < 20) return null

        val outW = (valid.size * scaleX).toInt().coerceAtLeast(64)
        val heights = valid.map { profile.bottom[it] - profile.top[it] }.sorted()
        val medianHeight = heights[heights.size / 2]
        var outH = (medianHeight * scaleY).toInt().coerceAtLeast(64)
        if (outW > maxSide || outH > maxSide) {
            val k = maxSide.toFloat() / max(outW, outH)
            outH = (outH * k).toInt()
        }

        val mapX = Mat(outH, outW, CvType.CV_32FC1)
        val mapY = Mat(outH, outW, CvType.CV_32FC1)
        val mapXData = FloatArray(outW * outH)
        val mapYData = FloatArray(outW * outH)

        for (u in 0 until outW) {
            val pos = u.toFloat() / (outW - 1) * (valid.size - 1)
            val i0 = pos.toInt().coerceIn(0, valid.size - 1)
            val i1 = (i0 + 1).coerceAtMost(valid.size - 1)
            val t = pos - i0
            val colA = valid[i0]
            val colB = valid[i1]
            val topA = profile.top[colA]
            val topB = profile.top[colB]
            val botA = profile.bottom[colA]
            val botB = profile.bottom[colB]
            val tA = topA + (topB - topA) * t
            val bA = botA + (botB - botA) * t
            val srcX = ((colA + (colB - colA) * t) * scaleX).toFloat()
            for (v in 0 until outH) {
                val srcY = ((tA + (bA - tA) * v.toFloat() / (outH - 1)) * scaleY).toFloat()
                mapXData[v * outW + u] = srcX
                mapYData[v * outW + u] = srcY
            }
        }
        mapX.put(0, 0, mapXData)
        mapY.put(0, 0, mapYData)

        val out = Mat()
        Imgproc.remap(rgb, out, mapX, mapY, Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        mapX.release()
        mapY.release()

        val rgba = Mat()
        Imgproc.cvtColor(out, rgba, Imgproc.COLOR_RGB2RGBA)
        out.release()
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    /** فقط برش و اصلاح پرسپکتیو (بدون بهبود). */
    fun warp(src: Bitmap, quad: Quad?, maxSide: Int = 2200): Bitmap? {
        if (!isReady()) return null
        if (quad == null) return null
        return try {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)
            val wA = dist(quad.tl, quad.tr)
            val wB = dist(quad.bl, quad.br)
            val hA = dist(quad.tl, quad.bl)
            val hB = dist(quad.tr, quad.br)
            var outW = max(wA, wB).toInt().coerceAtLeast(32)
            var outH = max(hA, hB).toInt().coerceAtLeast(32)
            val longEdge = max(outW, outH)
            if (longEdge > maxSide) {
                val k = maxSide.toFloat() / longEdge
                outW = (outW * k).toInt()
                outH = (outH * k).toInt()
            }
            val srcPts = org.opencv.core.MatOfPoint2f(
                Point(quad.tl.x.toDouble(), quad.tl.y.toDouble()),
                Point(quad.tr.x.toDouble(), quad.tr.y.toDouble()),
                Point(quad.br.x.toDouble(), quad.br.y.toDouble()),
                Point(quad.bl.x.toDouble(), quad.bl.y.toDouble())
            )
            val dstPts = org.opencv.core.MatOfPoint2f(
                Point(0.0, 0.0),
                Point(outW.toDouble(), 0.0),
                Point(outW.toDouble(), outH.toDouble()),
                Point(0.0, outH.toDouble())
            )
            val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            val out = Mat()
            Imgproc.warpPerspective(mat, out, transform, Size(outW.toDouble(), outH.toDouble()))
            transform.release()
            srcPts.release()
            dstPts.release()
            mat.release()
            val bitmap = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, bitmap)
            out.release()
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    /** برش + بهبود کامل. */
    fun scan(src: Bitmap, quad: Quad?, maxSide: Int = 2200): Bitmap? {
        val warped = warp(src, quad, maxSide) ?: return null
        val enhanced = enhanceOnly(warped)
        if (enhanced != null) {
            warped.recycle()
            return enhanced
        }
        return warped
    }

    /** فقط بهبود (بدون برش): سایه‌زدایی، تعادل سفید، سفیدکردن کاغذ، CLAHE و تیزکردن تطبیقی. */
    fun enhanceOnly(src: Bitmap): Bitmap? {
        if (!isReady()) return null
        return try {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)
            val rgb = Mat()
            Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)
            mat.release()

            val shadowFree = removeShadow(rgb)
            rgb.release()

            // کاهش نویز دوربین بدون نرم‌کردن متن
            val denoised = Mat()
            Imgproc.bilateralFilter(shadowFree, denoised, 5, 28.0, 28.0)
            shadowFree.release()

            // تعادل رنگ سفید ملایم بر اساس رنگ کاغذ
            val balanced0 = whiteBalance(denoised)
            denoised.release()

            // کاهش نویز رنگی قبل از سفیدکردن (مثل اسکنرهای حرفه‌ای)
            val balanced = Mat()
            try {
                Photo.fastNlMeansDenoisingColored(balanced0, balanced, 3f, 3f, 7, 21)
            } catch (t: Throwable) {
                balanced0.copyTo(balanced)
            }
            balanced0.release()

            val labSpace = Mat()
            Imgproc.cvtColor(balanced, labSpace, Imgproc.COLOR_RGB2Lab)
            balanced.release()
            val channels = ArrayList<Mat>()
            Core.split(labSpace, channels)
            Imgproc.createCLAHE(1.15, Size(8.0, 8.0)).apply(channels[0], channels[0])
            // سفیدکردن کاغذ (مثل اسکنرهای حرفه‌ای) با LUT ملایم روی روشنایی
            val l = channels[0]
            val black = percentile(l, 0.02)
            val white = percentile(l, 0.75)
            val span = (white - black).coerceAtLeast(30.0)
            val lut = Mat(256, 1, CvType.CV_8U)
            val data = ByteArray(256)
            for (i in 0 until 256) {
                val v = ((i - black) * 255.0 / span).coerceIn(0.0, 255.0)
                data[i] = v.toInt().toByte()
            }
            lut.put(0, 0, data)
            val mapped = Mat()
            Core.LUT(l, lut, mapped)
            lut.release()
            val blended = Mat()
            Core.addWeighted(l, 0.2, mapped, 0.8, 0.0, blended)
            mapped.release()
            blended.copyTo(l)
            blended.release()
            Core.merge(channels, labSpace)
            val result = Mat()
            Imgproc.cvtColor(labSpace, result, Imgproc.COLOR_Lab2RGB)
            labSpace.release()
            channels.forEach { it.release() }

            // تیزکردن فقط اگر تصویر نرم باشد
            val sharpened = adaptiveSharpen(result)
            result.release()

            val rgba = Mat()
            Imgproc.cvtColor(sharpened, rgba, Imgproc.COLOR_RGB2RGBA)
            sharpened.release()
            val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bitmap)
            rgba.release()
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    /** هر کانال را طوری مقیاس می‌کند که کاغذ به سفید نزدیک شود (حذف رنگ‌زدگی چراغ اتاق). */
    private fun whiteBalance(src: Mat): Mat {
        val channels = ArrayList<Mat>()
        Core.split(src, channels)
        val out = ArrayList<Mat>()
        for (channel in channels) {
            val p = percentile(channel, 0.90)
            val scale = if (p < 60.0) 1.0 else (252.0 / p).coerceIn(0.95, 2.2)
            val dst = Mat()
            Core.multiply(channel, Scalar(scale), dst)
            out.add(dst)
            channel.release()
        }
        val merged = Mat()
        Core.merge(out, merged)
        out.forEach { it.release() }
        return merged
    }

    /** بردن کاغذ به سفید و سایه‌های روشن به سفید، بدون خردکردن متن. */
    private fun levels(src: Mat): Mat {
        val rgb = Mat()
        Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
        val lab = Mat()
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        rgb.release()
        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        val l = channels[0]

        val low = percentile(l, 0.03)
        val high = percentile(l, 0.86)
        val span = (high - low).coerceAtLeast(25.0)
        val lut = Mat(256, 1, CvType.CV_8U)
        val data = ByteArray(256)
        for (i in 0 until 256) {
            val v = ((i - low) * 255.0 / span).coerceIn(0.0, 255.0)
            val white = if (v > 232.0) 255.0 else v
            data[i] = white.toInt().toByte()
        }
        lut.put(0, 0, data)
        val mapped = Mat()
        Core.LUT(l, lut, mapped)
        lut.release()
        mapped.copyTo(l)
        mapped.release()
        Core.merge(channels, lab)
        channels.forEach { it.release() }

        val out = Mat()
        Imgproc.cvtColor(lab, out, Imgproc.COLOR_Lab2RGB)
        lab.release()
        return out
    }

    private fun adaptiveSharpen(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        val small = Mat()
        Imgproc.resize(gray, small, Size(320.0, (320.0 * gray.rows() / gray.cols()).coerceAtLeast(1.0)))
        val lap = Mat()
        Imgproc.Laplacian(small, lap, CvType.CV_64F)
        val mean = MatOfDoubleWrapper(lap)
        val variance = mean.variance
        gray.release()
        small.release()
        lap.release()

        if (variance > 260.0) return src.clone()

        val blurred = Mat()
        Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 2.2)
        val sharpened = Mat()
        Core.addWeighted(src, 1.14, blurred, -0.14, 0.0, sharpened)
        blurred.release()
        return sharpened
    }

    private class MatOfDoubleWrapper(mat: Mat) {
        val variance: Double

        init {
            val meanMat = org.opencv.core.MatOfDouble()
            val stdMat = org.opencv.core.MatOfDouble()
            Core.meanStdDev(mat, meanMat, stdMat)
            val std = stdMat.toArray().firstOrNull() ?: 0.0
            variance = std * std
            meanMat.release()
            stdMat.release()
        }
    }

    private fun percentile(channel: Mat, fraction: Double): Double {
        val small = Mat()
        val targetW = 240.0
        val targetH = (targetW * channel.rows() / channel.cols()).coerceAtLeast(1.0)
        Imgproc.resize(channel, small, Size(targetW, targetH))
        val total = small.total().toInt().coerceAtLeast(1)
        val data = ByteArray(total)
        small.get(0, 0, data)
        small.release()
        val histogram = IntArray(256)
        for (b in data) histogram[b.toInt() and 0xFF]++
        val target = (total * fraction).toInt().coerceIn(1, total)
        var acc = 0
        for (i in 0 until 256) {
            acc += histogram[i]
            if (acc >= target) return i.toDouble()
        }
        return 255.0
    }

    /** بریدن حاشیهٔ تیره/رنگی اطراف کاغذ صاف‌شده با استفاده از ماسک روشنایی. */
    fun cropToPaper(src: Bitmap): Bitmap? {
        if (!isReady()) return null
        return try {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)
            val rgb = Mat()
            Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)
            mat.release()
            val hsv = Mat()
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val mask = Mat()
            Core.inRange(hsv, Scalar(0.0, 0.0, 120.0), Scalar(180.0, 90.0, 255.0), mask)
            hsv.release()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(11.0, 11.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            mask.release()
            val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
            if (Imgproc.contourArea(largest) < rgb.cols() * rgb.rows() * 0.2) return null
            val rect = Imgproc.boundingRect(largest)
            val insetX = (rect.width * 0.015).toInt()
            val insetY = (rect.height * 0.015).toInt()
            val cropRect = org.opencv.core.Rect(
                (rect.x + insetX).coerceIn(0, rgb.cols() - 2),
                (rect.y + insetY).coerceIn(0, rgb.rows() - 2),
                (rect.width - 2 * insetX).coerceAtLeast(16),
                (rect.height - 2 * insetY).coerceAtLeast(16)
            )
            val cropped = Mat(rgb, cropRect)
            val rgba = Mat()
            Imgproc.cvtColor(cropped, rgba, Imgproc.COLOR_RGB2RGBA)
            cropped.release()
            rgb.release()
            val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bitmap)
            rgba.release()
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    private fun removeShadow(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        val bg = Mat()
        val k = max(15, (max(src.width(), src.height()) / 22) or 1)
        Imgproc.GaussianBlur(gray, bg, Size(k.toDouble(), k.toDouble()), 0.0)
        gray.release()

        val mean = Core.mean(bg).`val`[0].coerceAtLeast(20.0)
        val channels = ArrayList<Mat>()
        Core.split(src, channels)
        val out = ArrayList<Mat>()
        for (channel in channels) {
            val dst = Mat()
            Core.divide(channel, bg, dst, mean)
            out.add(dst)
            channel.release()
        }
        bg.release()
        val merged = Mat()
        Core.merge(out, merged)
        out.forEach { it.release() }
        return merged
    }

    private fun dist(a: PointF, b: PointF): Double =
        kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}
