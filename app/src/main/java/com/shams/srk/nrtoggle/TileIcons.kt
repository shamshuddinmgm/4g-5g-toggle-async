package com.shams.srk.nrtoggle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import com.shams.srk.nrtoggle.network.NetworkMode

/**
 * QS glyphs that read **4G** / **5G**.
 * HyperOS circular tiles inset icons heavily — fill ~92% of the bitmap so text stays bold.
 */
object TileIcons {

    @Volatile private var icon4g: Icon? = null
    @Volatile private var icon5g: Icon? = null

    fun forMode(context: Context, mode: NetworkMode?): Icon = when (mode) {
        NetworkMode.LTE -> fourG(context)
        NetworkMode.NR, null -> fiveG(context)
    }

    fun fourG(context: Context): Icon =
        icon4g ?: render(context, "4G").also { icon4g = it }

    fun fiveG(context: Context): Icon =
        icon5g ?: render(context, "5G").also { icon5g = it }

    private fun render(context: Context, text: String): Icon {
        // Fixed px canvas; system scales. Large canvas + edge-to-edge glyph = readable on HyperOS.
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            isFakeBoldText = true
            letterSpacing = -0.04f
        }

        val maxW = size * 0.94f
        val maxH = size * 0.78f
        var lo = 8f
        var hi = size * 1.35f
        val bounds = Rect()
        // Binary-search the largest textSize that still fits the circle-safe box.
        repeat(14) {
            val mid = (lo + hi) / 2f
            paint.textSize = mid
            paint.getTextBounds(text, 0, text.length, bounds)
            val w = paint.measureText(text)
            val h = bounds.height().toFloat()
            if (w <= maxW && h <= maxH) lo = mid else hi = mid
        }
        paint.textSize = lo

        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)
        return Icon.createWithBitmap(bmp)
    }
}
