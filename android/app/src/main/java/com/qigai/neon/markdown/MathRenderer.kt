package com.qigai.neon.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

/** Renders TeX to bitmaps with JLaTeXMath. Returns null when the source does not parse, so the caller can show it raw. */
object MathRenderer {
    @Volatile private var ready = false
    private val cache = LruCache<String, Bitmap>(64)
    private val failed = HashSet<String>()

    fun init(context: Context) {
        if (ready) return
        runCatching { JLatexMathAndroid.init(context.applicationContext) }.onSuccess { ready = true }
    }

    fun render(tex: String, textSizePx: Float, color: Int, block: Boolean): Bitmap? {
        if (!ready) return null
        val key = "$block|$textSizePx|$color|$tex"
        cache.get(key)?.let { return it }
        if (key in failed) return null
        return runCatching {
            val d = JLatexMathDrawable.builder(tex)
                .textSize(textSizePx)
                .color(color)
                .align(if (block) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
                .build()
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(bmp))
            bmp
        }.onSuccess { cache.put(key, it) }.onFailure { synchronized(failed) { failed += key } }.getOrNull()
    }
}
