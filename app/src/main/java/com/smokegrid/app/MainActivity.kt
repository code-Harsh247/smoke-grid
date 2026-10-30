package com.smokegrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

private val Cell = 20.dp
private val CellGap = 5.dp
private val DayLabelWidth = 16.dp
private val DayLabelGap = 6.dp
private const val MAX_WEEKS = 13

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, surface = Ground)) {
                Surface(Modifier.fillMaxSize()) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                    val goHome = { screen = Screen.Home }
                    BackHandler(enabled = screen != Screen.Home, onBack = goHome)
                    when (screen) {
                        Screen.Home -> HomeScreen(onOpenStats = { screen = Screen.Stats }, onOpenSync = { screen = Screen.Sync })
                        Screen.Stats -> StatsScreen(onBack = goHome)
                        Screen.Sync -> SyncScreen(onBack = goHome)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Pick up other phones' changes whenever the app comes to the front.
        Sync.schedulePeriodic(this)
        Sync.requestSync(this, delaySeconds = 0)
    }
}

private enum class Screen { Home, Stats, Sync }

@Composable
private fun HomeScreen(onOpenStats: () -> Unit, onOpenSync: () -> Unit) {
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
                        style = TextStyle(fontFamily = Manrope, fontSize = 72.sp, fontWeight = FontWeight.SemiBold, lineHeight = 72.sp, letterSpacing = (-0.03).em),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${daysWord(streak)} clean",
                        color = TextPrimary,
                        style = TextStyle(fontFamily = Manrope, fontSize = 20.sp, fontWeight = FontWeight.Medium),
                    )
                }
            } else {
                Text(
                    text = "Smoked today",
                    color = TextPrimary,
                    style = TextStyle(fontFamily = Manrope, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp, letterSpacing = (-0.02).em),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(caption, style = Caption, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        HistoryGrid(counts, today, start)
        Spacer(Modifier.height(32.dp))
        SummaryCard(
            weekTotal = remember(version, today) { Stats.thisWeek(counts, start, today).total },
            monthTotal = remember(version, today) { Stats.thisMonth(counts, start, today).total },
            monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            onClick = onOpenStats,
        )
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
    // Drawn last so it sits above the column.
    if (Sync.isConfigured) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 8.dp, end = 8.dp), contentAlignment = Alignment.TopEnd) {
            IconButton(
                onClick = onOpenSync,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = TextSecondary),
            ) {
                Icon(CloudIcon, contentDescription = "Sync")
            }
        }
    }
}

/** Month labels, M/W/F day labels and up to 13 weeks of grid. */
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
        }
    }
}

/** This week · this month, tapping opens Stats. Same width as the grid. */
@Composable
private fun SummaryCard(weekTotal: Int, monthTotal: Int, monthName: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(max = DayLabelWidth + DayLabelGap + (Cell + CellGap) * MAX_WEEKS - CellGap).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardFill,
    ) {
        Row(
            Modifier.height(64.dp).padding(start = 18.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryValue("This week", weekTotal)
            Box(Modifier.width(1.dp).height(32.dp).background(ButtonBorder))
            SummaryValue(monthName, monthTotal)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Stats", style = Label.copy(fontSize = 14.sp, color = Accent))
                Icon(ChevronRightIcon, contentDescription = null, tint = Accent)
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Small)
        Text("$value", style = Label.copy(fontSize = 20.sp, lineHeight = 24.sp, color = TextPrimary))
    }
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
