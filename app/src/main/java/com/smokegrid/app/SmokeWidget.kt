package com.smokegrid.app

import android.content.Context
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
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider

private val PAD = 12.dp

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

/** Just the grid. Tapping it opens the app, where you log days. */
@Composable
private fun WidgetContent(context: Context) {
    val version by remember { SmokeLog.changes(context) }.collectAsState(initial = 0)
    val size = LocalSize.current
    val today = SmokeLog.today()

    val density = context.resources.displayMetrics.density
    val gridW = ((size.width - PAD * 2).value * density).toInt()
    val gridH = ((size.height - PAD * 2).value * density).toInt()
    val grid = remember(version, today, gridW, gridH) {
        HeatmapRenderer.render(
            SmokeLog.allCounts(context), today, SmokeLog.startDate(context), gridW, gridH,
        )
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF161B22)))
            .cornerRadius(16.dp)
            .padding(PAD)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Image(
            provider = ImageProvider(grid),
            contentDescription = "Smoking history grid",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}
