package com.smokegrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Ember palette (dark). Grid colours live in GridPalette.
private val Ground = Color(0xFF1A1714)
private val TextPrimary = Color(0xFFF3E6D3)
private val TextSecondary = Color(0xFFA89A88)
private val ButtonFill = Color(0xFF2A231E)
private val ButtonBorder = Color(0xFF3A3029)
private val Accent = Color(0xFFF59A52)

private val Cell = 20.dp
private val CellGap = 5.dp
private val DayLabelWidth = 16.dp
private val DayLabelGap = 6.dp
private const val MAX_WEEKS = 13

private val Caption = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
private val Small = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, color = TextSecondary)
private val Label = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, surface = Ground)) {
                Surface(Modifier.fillMaxSize()) { HomeScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by remember { SmokeLog.changes(context) }.collectAsState(initial = 0)
    val today = SmokeLog.today()

    val counts = remember(version, today) { SmokeLog.allCounts(context) }
    val start = remember(version, today) { SmokeLog.startDate(context) }
    val streak = remember(version, today) { SmokeLog.cleanStreak(context) }
    val streakBefore = remember(version, today) { SmokeLog.cleanStreak(context, today.minusDays(1)) }
    val count = counts[today] ?: 0

    val caption = when {
        count > 0 && streakBefore > 0 ->
            "Your $streakBefore clean ${daysWord(streakBefore)} still ${if (streakBefore == 1L) "counts" else "count"}."
        count > 0 -> "Tomorrow starts clean."
        start == today -> "Tracking started today"
        else -> "Since " + today.minusDays(streak - 1).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Streak block, bottom-aligned so the caption stays put between states.
        Box(Modifier.height(112.dp), contentAlignment = Alignment.BottomCenter) {
            if (count == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$streak",
                        color = TextPrimary,
                        style = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.SemiBold, lineHeight = 72.sp, letterSpacing = (-0.03).em),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${daysWord(streak)} clean",
                        color = TextPrimary,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
                    )
                }
            } else {
                Text(
                    text = "Smoked today",
                    color = TextPrimary,
                    style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp, letterSpacing = (-0.02).em),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(caption, style = Caption, textAlign = TextAlign.Center)
        Spacer(Modifier.height(56.dp))
        HistoryGrid(counts, today, start)
        Spacer(Modifier.weight(1f))
        LogControl(count) { delta ->
            SmokeLog.adjust(context, today, delta)
            scope.launch { SmokeWidget().updateAll(context) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(16.dp)) {
            if (count == 1) Text("Tap + for each one", style = Small.copy(fontSize = 12.sp, lineHeight = 16.sp))
        }
    }
}

/** Month labels, M/W/F day labels, up to 13 weeks of grid and the shade legend. */
@Composable
private fun HistoryGrid(counts: Map<LocalDate, Int>, today: LocalDate, start: LocalDate) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val labelsW = DayLabelWidth + DayLabelGap
        val weeks = ((maxWidth - labelsW + CellGap) / (Cell + CellGap)).toInt().coerceIn(1, MAX_WEEKS)
        val gridW = (Cell + CellGap) * weeks - CellGap
        val gridH = (Cell + CellGap) * 7 - CellGap
        val density = LocalDensity.current
        val bitmap = remember(counts, today, start, weeks, density) {
            with(density) {
                HeatmapRenderer.render(counts, today, start, weeks, Cell.toPx(), CellGap.toPx(), 2.dp.toPx(), GridPalette.DARK)
            }
        }
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        Column(Modifier.width(labelsW + gridW), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(start = labelsW)) {
                var prev: Month? = null
                for (col in 0 until weeks) {
                    val month = thisMonday.minusWeeks((weeks - 1 - col).toLong()).month
                    val label = if (prev != null && month != prev) {
                        month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                    } else ""
                    Text(label, Modifier.width(Cell + CellGap), style = Small, maxLines = 1, softWrap = false)
                    prev = month
                }
            }
            Row {
                Column(Modifier.width(DayLabelWidth), verticalArrangement = Arrangement.spacedBy(CellGap)) {
                    for (day in listOf("M", "", "W", "", "F", "", "")) {
                        Box(Modifier.height(Cell), contentAlignment = Alignment.CenterStart) {
                            Text(day, style = Small.copy(fontSize = 10.sp))
                        }
                    }
                }
                Spacer(Modifier.width(DayLabelGap))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Smoking history, last $weeks weeks",
                    modifier = Modifier.size(gridW, gridH),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Clean", Modifier.padding(end = 2.dp), style = Small)
                Swatch(Color(GridPalette.DARK.clean), 11)
                Text("1", Modifier.padding(start = 12.dp, end = 2.dp), style = Small)
                GridPalette.DARK.shades.forEach { Swatch(Color(it), 11) }
                Text("4+", Modifier.padding(start = 2.dp), style = Small)
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(if (sizeDp > 11) 4.dp else 3.dp))
            .background(color)
    )
}

/** "I smoked today" until the first tap, then a − / + stepper for today's count. */
@Composable
private fun LogControl(count: Int, onAdjust: (Int) -> Unit) {
    val pill = Modifier.height(56.dp).widthIn(min = 248.dp)
    if (count == 0) {
        OutlinedButton(
            onClick = { onAdjust(1) },
            modifier = pill,
            border = BorderStroke(1.dp, ButtonBorder),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = ButtonFill, contentColor = Accent),
            contentPadding = PaddingValues(horizontal = 32.dp),
        ) {
            Text("I smoked today", style = Label)
        }
        return
    }
    Row(
        modifier = pill
            .clip(CircleShape)
            .background(ButtonFill)
            .border(1.dp, ButtonBorder, CircleShape)
            .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onAdjust(-1) },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary),
        ) {
            Icon(
                imageVector = if (count == 1) UndoIcon else MinusIcon,
                contentDescription = if (count == 1) "Undo today" else "Remove one",
            )
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(Color(GridPalette.DARK.colorFor(count)), 14)
            Spacer(Modifier.width(10.dp))
            Text("$count today", style = Label, color = TextPrimary)
        }
        IconButton(
            onClick = { onAdjust(1) },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = ButtonBorder, contentColor = Accent),
        ) {
            Icon(PlusIcon, contentDescription = "Log one more")
        }
    }
}

/** 18dp line icons on a 24 grid, tinted by Icon. */
private fun lineIcon(name: String, strokeWidth: Float, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 18.dp, 18.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private val UndoIcon = lineIcon("undo", 2f, "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
private val MinusIcon = lineIcon("minus", 2.2f, "M5 12h14")
private val PlusIcon = lineIcon("plus", 2.2f, "M12 5v14", "M5 12h14")
