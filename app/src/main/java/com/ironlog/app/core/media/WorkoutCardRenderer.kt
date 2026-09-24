package com.ironlog.app.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * A workout as a picture: the card people post after a session.
 *
 * Drawn straight onto a bitmap with the platform canvas rather than by
 * capturing a composable, so it looks the same whatever the screen and
 * costs nothing on the UI thread. Portrait, 4:5, the aspect the feeds use.
 * Only what the user already sees on the summary goes in: the name, the
 * date, the totals and the sets. No body data, no location.
 */
object WorkoutCardRenderer {

    data class Card(
        val title: String,
        val subtitle: String,
        val stats: List<Pair<String, String>>,
        val lines: List<String>,
        val footer: String = "Logged with Liftosaur"
    )

    private const val W = 1080
    private const val H = 1350
    private const val MARGIN = 72f
    private const val MAX_LINES = 14

    fun render(card: Card): ByteArray {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF0E1216.toInt())

        val chalk = 0xFFF2F5F7.toInt()
        val dim = 0xFFA7B4C0.toInt()
        val faint = 0xFF6B7987.toInt()
        val slate = 0xFF151A20.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Brand line, small and quiet at the top.
        paint.color = faint
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 30f
        paint.letterSpacing = 0.12f
        canvas.drawText("LIFTOSAUR", MARGIN, MARGIN + 30f, paint)
        paint.letterSpacing = 0f

        // Title and date.
        paint.color = chalk
        paint.textSize = 76f
        var y = MARGIN + 130f
        canvas.drawText(ellipsize(card.title, paint, W - 2 * MARGIN), MARGIN, y, paint)
        paint.color = dim
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 36f
        y += 58f
        canvas.drawText(card.subtitle, MARGIN, y, paint)

        // Stats in a card: value large, label small, evenly spaced.
        y += 50f
        val statsTop = y
        val statsHeight = 200f
        paint.color = slate
        canvas.drawRoundRect(RectF(MARGIN, statsTop, W - MARGIN, statsTop + statsHeight), 28f, 28f, paint)
        val slot = (W - 2 * MARGIN) / card.stats.size.coerceAtLeast(1)
        card.stats.forEachIndexed { i, (value, label) ->
            val x = MARGIN + slot * i + 36f
            paint.color = chalk
            paint.typeface = Typeface.DEFAULT_BOLD
            // A big volume like "12,480 kg" is wider than its slot at the full
            // size; it shrinks to fit rather than running into the next stat.
            paint.textSize = 60f
            val room = slot - 48f
            while (paint.measureText(value) > room && paint.textSize > 30f) paint.textSize -= 2f
            canvas.drawText(value, x, statsTop + 92f, paint)
            paint.color = dim
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 30f
            canvas.drawText(label, x, statsTop + 146f, paint)
        }

        // The sets, one line each; a long session is cut with a count rather than shrunk.
        y = statsTop + statsHeight + 70f
        paint.textSize = 36f
        val shown = card.lines.take(MAX_LINES)
        shown.forEach { line ->
            val isHeading = !line.startsWith(" ")
            paint.color = if (isHeading) chalk else dim
            paint.typeface = if (isHeading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(ellipsize(line.trimStart(), paint, W - 2 * MARGIN - (if (isHeading) 0f else 40f)),
                MARGIN + (if (isHeading) 0f else 40f), y, paint)
            y += if (isHeading) 56f else 48f
        }
        if (card.lines.size > MAX_LINES) {
            paint.color = faint
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("+ ${card.lines.size - MAX_LINES} more", MARGIN, y, paint)
        }

        // Footer.
        paint.color = faint
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 30f
        canvas.drawText(card.footer, MARGIN, H - MARGIN, paint)

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && paint.measureText("$cut…") > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }
}
