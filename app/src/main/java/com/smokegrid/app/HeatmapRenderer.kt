package com.smokegrid.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws the GitHub-style grid into a bitmap.
 * Columns are weeks (newest on the right), rows are Mon..Sun top to bottom.
 * As many weeks as fit the given width are shown, so the widget works at any size.
 */
object HeatmapRenderer {
    val BACKGROUND = Color.parseColor("#161B22")
    private val SMOKED = Color.parseColor("#E5534B")
    private val CLEAN = Color.parseColor("#2D333B")
    private val TODAY_RING = Color.parseColor("#ADBAC7")

    /** Gap between boxes as a fraction of box size. */
    private const val GAP = 0.25f

    fun render(
        counts: Map<LocalDate, Int>,
        today: LocalDate,
        start: LocalDate,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(8)
        val h = heightPx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 7 boxes + 6 gaps must fit the height.
        val cell = h / (7f + 6f * GAP)
        val gap = cell * GAP
        val step = cell + gap
        val weeks = floor((w + gap) / step).toInt().coerceAtLeast(1)

        // Right-align so the current week sits at the right edge.
        val gridWidth = weeks * step - gap
        val offsetX = w - gridWidth
        val radius = cell * 0.2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (cell * 0.12f).coerceAtLeast(2f)
            color = TODAY_RING
        }

        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        for (col in 0 until weeks) {
            val monday = thisMonday.minusWeeks((weeks - 1 - col).toLong())
            for (row in 0 until 7) {
                val date = monday.plusDays(row.toLong())
                if (date.isAfter(today)) continue

                val left = offsetX + col * step
                val top = row * step
                val rect = RectF(left, top, left + cell, top + cell)

                val count = counts[date] ?: 0
                fill.color = if (count > 0) SMOKED else CLEAN
                // Darker-to-brighter red by count, ready for v2 (1..4+ like GitHub's levels).
                fill.alpha = when {
                    count > 0 -> (140 + 115 * (min(count, 4) - 1) / 3)
                    date.isBefore(start) -> 90 // before tracking started
                    else -> 255
                }
                canvas.drawRoundRect(rect, radius, radius, fill)

                if (date == today) {
                    val inset = ring.strokeWidth / 2
                    rect.inset(inset, inset)
                    canvas.drawRoundRect(rect, radius, radius, ring)
                }
            }
        }
        return bitmap
    }
}
