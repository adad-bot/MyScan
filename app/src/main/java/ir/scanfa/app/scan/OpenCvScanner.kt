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

    /** برش + صاف‌سازی + حذف سایه + CLAHE + رنگ و تیزی. */
    fun scan(src: Bitmap, quad: Quad?, maxSide: Int = 2200): Bitmap? {
        if (!isReady()) return null
        return try {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)
            var warped = mat
            if (quad != null) {
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
                if (warped !== mat) warped.release()
                warped = out
            }

            // حذف سایه: تقسیم بر پس‌زمینهٔ محو
            val shadowFree = removeShadow(warped)
            if (shadowFree !== warped) warped.release()

            // CLAHE روی روشنایی
            val lab = Mat()
            Imgproc.cvtColor(shadowFree, lab, Imgproc.COLOR_RGBA2RGB)
            val labSpace = Mat()
            Imgproc.cvtColor(lab, labSpace, Imgproc.COLOR_RGB2Lab)
            lab.release()
            val channels = ArrayList<Mat>()
            Core.split(labSpace, channels)
            val clahe = Imgproc.createCLAHE(1.6, Size(8.0, 8.0))
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, labSpace)
            val result = Mat()
            Imgproc.cvtColor(labSpace, result, Imgproc.COLOR_Lab2RGB)
            labSpace.release()
            channels.forEach { it.release() }
            shadowFree.release()

            // اشباع رنگ کمی بیشتر
            val hsv = Mat()
            Imgproc.cvtColor(result, hsv, Imgproc.COLOR_RGB2HSV)
            result.release()
            val hsvChannels = ArrayList<Mat>()
            Core.split(hsv, hsvChannels)
            Core.multiply(hsvChannels[1], Scalar(1.12), hsvChannels[1])
            Core.merge(hsvChannels, hsv)
            val colored = Mat()
            Imgproc.cvtColor(hsv, colored, Imgproc.COLOR_HSV2RGB)
            hsv.release()
            hsvChannels.forEach { it.release() }

            // تیزکردن ملایم
            val blurred = Mat()
            Imgproc.GaussianBlur(colored, blurred, Size(0.0, 0.0), 3.0)
            val sharpened = Mat()
            Core.addWeighted(colored, 1.35, blurred, -0.35, 0.0, sharpened)
            blurred.release()
            colored.release()

            val bitmap = Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(sharpened, bitmap)
            sharpened.release()
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    /** فقط بهبود (بدون برش) برای صفحاتی که از قبل برش خورده‌اند. */
    fun enhanceOnly(src: Bitmap): Bitmap? {
        if (!isReady()) return null
        return try {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)
            val shadowFree = removeShadow(mat)
            mat.release()
            val lab = Mat()
            Imgproc.cvtColor(shadowFree, lab, Imgproc.COLOR_RGBA2RGB)
            shadowFree.release()
            val labSpace = Mat()
            Imgproc.cvtColor(lab, labSpace, Imgproc.COLOR_RGB2Lab)
            lab.release()
            val channels = ArrayList<Mat>()
            Core.split(labSpace, channels)
            Imgproc.createCLAHE(1.6, Size(8.0, 8.0)).apply(channels[0], channels[0])
            Core.merge(channels, labSpace)
            val result = Mat()
            Imgproc.cvtColor(labSpace, result, Imgproc.COLOR_Lab2RGB)
            labSpace.release()
            channels.forEach { it.release() }
            val hsv = Mat()
            Imgproc.cvtColor(result, hsv, Imgproc.COLOR_RGB2HSV)
            result.release()
            val hsvChannels = ArrayList<Mat>()
            Core.split(hsv, hsvChannels)
            Core.multiply(hsvChannels[1], Scalar(1.12), hsvChannels[1])
            Core.merge(hsvChannels, hsv)
            val colored = Mat()
            Imgproc.cvtColor(hsv, colored, Imgproc.COLOR_HSV2RGB)
            hsv.release()
            hsvChannels.forEach { it.release() }
            val blurred = Mat()
            Imgproc.GaussianBlur(colored, blurred, Size(0.0, 0.0), 3.0)
            val sharpened = Mat()
            Core.addWeighted(colored, 1.35, blurred, -0.35, 0.0, sharpened)
            blurred.release()
            colored.release()
            val bitmap = Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(sharpened, bitmap)
            sharpened.release()
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    private fun removeShadow(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
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
