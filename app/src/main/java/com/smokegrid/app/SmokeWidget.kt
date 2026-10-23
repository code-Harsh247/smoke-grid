package com.smokegrid.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import kotlin.math.floor
import kotlin.math.roundToInt

private const val PAD = 14f

class SmokeWidget : GlanceAppWidget() {
    // Recompose with the real size so the grid shows as many weeks as fit.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(context) }
    }
}

class SmokeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmokeWidget()
}

/** Just the grid, right-aligned and vertically centred. Tapping it opens the app, where you log days. */
@Composable
private fun WidgetContent(context: Context) {
    val version by remember { SmokeLog.changes(context) }.collectAsState(initial = 0)
    val size = LocalSize.current
    val today = SmokeLog.today()
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val palette = if (night) GridPalette.DARK else GridPalette.LIGHT

    // Geometry in dp: cell from the height (clamped 10..20), gap a quarter of the cell.
    val innerW = size.width.value - PAD * 2
    val innerH = size.height.value - PAD * 2
    val cell = floor(innerH / 8.5f).coerceIn(10f, 20f)
    val gap = (cell / 4f).roundToInt().toFloat()
    val weeks = floor((innerW + gap) / (cell + gap)).toInt().coerceAtLeast(1)
    val gridW = weeks * (cell + gap) - gap
    val gridH = 7 * (cell + gap) - gap

    val density = context.resources.displayMetrics.density
    val grid = remember(version, today, weeks, cell, palette) {
        HeatmapRenderer.render(
            SmokeLog.allCounts(context), today, SmokeLog.startDate(context),
            weeks, cell * density, gap * density, 1.5f * density, palette,
        )
    }
    val streak = remember(version, today) { SmokeLog.cleanStreak(context) }

    val corners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.cornerRadius(22.dp)
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(palette.background)))
            .then(corners)
            .padding(PAD.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Image(
            provider = ImageProvider(grid),
            contentDescription = if (streak > 0) "$streak ${daysWord(streak)} clean" else "Smoked today",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.size(gridW.dp, gridH.dp),
        )
    }
}

fun daysWord(n: Long) = if (n == 1L) "day" else "days"
