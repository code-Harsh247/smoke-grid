package com.smokegrid.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.min

/** The "Ember" colours for one theme. [shades] are 1, 2, 3 and 4+ cigarettes. */
class GridPalette(
    val background: Int,
    val beforeTracking: Int,
    val clean: Int,
    val shades: List<Int>,
    val todayRing: Int,
) {
    fun colorFor(count: Int): Int = if (count > 0) shades[min(count, shades.size) - 1] else clean

    companion object {
        val DARK = GridPalette(
            background = Color.parseColor("#1A1714"),
            beforeTracking = Color.parseColor("#211D19"),
            clean = Color.parseColor("#2C2722"),
            shades = listOf("#5E3B22", "#93562A", "#CC7430", "#F59A52").map(Color::parseColor),
            todayRing = Color.parseColor("#F3E6D3"),
        )
        val LIGHT = GridPalette(
            background = Color.parseColor("#FBF7F1"),
            beforeTracking = Color.parseColor("#F3EEE6"),
            clean = Color.parseColor("#E9E1D5"),
            shades = listOf("#F5CBA3", "#EBA062", "#CF7330", "#94471A").map(Color::parseColor),
            todayRing = Color.parseColor("#3A2A1C"),
        )
    }
}

/**
 * Draws the GitHub-style grid into a bitmap sized exactly to the grid.
 * Columns are weeks (newest on the right), rows are Mon..Sun top to bottom.
 * Cell radius is a quarter of the cell; days after today are not drawn.
 */
object HeatmapRenderer {
    fun render(
        counts: Map<LocalDate, Int>,
        today: LocalDate,
        start: LocalDate,
        weeks: Int,
        cellPx: Float,
        gapPx: Float,
        ringPx: Float,
        palette: GridPalette,
    ): Bitmap {
        val step = cellPx + gapPx
        val w = ceil(weeks * step - gapPx).toInt().coerceAtLeast(1)
        val h = ceil(7 * step - gapPx).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = cellPx / 4f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringPx
            color = palette.todayRing
        }

        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        for (col in 0 until weeks) {
            val monday = thisMonday.minusWeeks((weeks - 1 - col).toLong())
            for (row in 0 until 7) {
                val date = monday.plusDays(row.toLong())
                if (date.isAfter(today)) continue

                val left = col * step
                val top = row * step
                val rect = RectF(left, top, left + cellPx, top + cellPx)

                val count = counts[date] ?: 0
                fill.color = if (count == 0 && date.isBefore(start)) palette.beforeTracking else palette.colorFor(count)
                canvas.drawRoundRect(rect, radius, radius, fill)

                // Inset ring, drawn over whatever shade today has.
                if (date == today) {
                    val inset = ringPx / 2
                    rect.inset(inset, inset)
                    canvas.drawRoundRect(rect, radius - inset, radius - inset, ring)
                }
            }
        }
        return bitmap
    }
}
