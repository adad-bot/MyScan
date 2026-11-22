package ir.scanfa.app.image

/** کادرهای شناسه‌دار: کوچک‌سازی و Otsu برای کمک به تشخیص قاب. */
internal object Blur {

    fun box(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)

        for (y in 0 until h) {
            var sum = 0f
            var count = 0
            val row = y * w
            for (i in 0..radius) {
                if (i < w) {
                    sum += src[row + i]
                    count++
                }
            }
            for (x in 0 until w) {
                tmp[row + x] = if (count > 0) sum / count else src[row + x]
                val add = x + radius + 1
                if (add < w) {
                    sum += src[row + add]
                    count++
                }
                val rem = x - radius
                if (rem >= 0) {
                    sum -= src[row + rem]
                    count--
                }
            }
        }

        for (x in 0 until w) {
            var sum = 0f
            var count = 0
            for (i in 0..radius) {
                if (i < h) {
                    sum += tmp[i * w + x]
                    count++
                }
            }
            for (y in 0 until h) {
                out[y * w + x] = if (count > 0) sum / count else tmp[y * w + x]
                val add = y + radius + 1
                if (add < h) {
                    sum += tmp[add * w + x]
                    count++
                }
                val rem = y - radius
                if (rem >= 0) {
                    sum -= tmp[rem * w + x]
                    count--
                }
            }
        }
        return out
    }
}
