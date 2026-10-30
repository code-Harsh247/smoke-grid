package com.smokegrid.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val BarMaxHeight = 140.dp
private const val MAX_WEEK_BARS = 12

/** Totals for this week and month, and a week-by-week / month-by-month bar chart. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version by remember { SmokeLog.changes(context) }.collectAsState(initial = 0)
    val today = SmokeLog.today()
    val counts = remember(version, today) { SmokeLog.allCounts(context) }
    val start = remember(version, today) { SmokeLog.startDate(context) }
    var showWeeks by rememberSaveable { mutableStateOf(true) }

    val locale = Locale.getDefault()
    val thisWeek = Stats.thisWeek(counts, start, today)
    val thisMonth = Stats.thisMonth(counts, start, today)
    val lastMonthName = today.minusMonths(1).month.getDisplayName(DateTextStyle.FULL, locale)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
    ) {
        Row(
            Modifier.offset(x = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary),
            ) {
                Icon(BackIcon, contentDescription = "Back")
            }
            Text("Stats", style = Label.copy(fontSize = 20.sp, color = TextPrimary))
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                modifier = Modifier.weight(1f),
                title = "This week",
                total = thisWeek.total,
                range = thisWeek.from.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) + " – today",
                previous = "Last week · ${Stats.lastWeek(counts, start, today).total}",
            )
            Tile(
                modifier = Modifier.weight(1f),
                title = "This month",
                total = thisMonth.total,
                range = thisMonth.from.format(DateTimeFormatter.ofPattern("d MMM", locale)) + " – today",
                previous = "$lastMonthName · ${Stats.lastMonth(counts, start, today).total}",
            )
        }

        Spacer(Modifier.height(24.dp))
        PeriodSwitch(showWeeks) { showWeeks = it }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardFill)
                .padding(16.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (showWeeks) {
                    val n = (maxWidth / 24.dp).toInt().coerceIn(1, MAX_WEEK_BARS)
                    val weeks = Stats.weeks(counts, start, today, n)
                    var prevMonth: java.time.Month? = null
                    val labels = weeks.map { week ->
                        val month = week.from.month
                        val sub = if (month != prevMonth) month.getDisplayName(DateTextStyle.SHORT, locale) else ""
                        prevMonth = month
                        "${week.from.dayOfMonth}" to sub
                    }
                    BarChart(
                        title = "Last $n weeks",
                        averageText = "This week " + perDay(thisWeek, locale),
                        periods = weeks,
                        labels = labels,
                        columnWidth = 24.dp,
                        barWidth = 18.dp,
                    )
                } else {
                    val max = (maxWidth / 44.dp).toInt().coerceAtLeast(1)
                    val months = Stats.months(counts, start, today, max)
                    val allShown = months.first().from <= start
                    BarChart(
                        title = if (allShown) "Since tracking began" else "Last ${months.size} months",
                        averageText = today.month.getDisplayName(DateTextStyle.FULL, locale) + " " + perDay(thisMonth, locale),
                        periods = months,
                        labels = months.map { it.from.month.getDisplayName(DateTextStyle.SHORT, locale) to "" },
                        columnWidth = 44.dp,
                        barWidth = 36.dp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Bar colour = average per day", Modifier.padding(end = 6.dp), style = Small)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GridPalette.DARK.shades.forEach { Swatch(Color(it), 11) }
            }
            Text("4+", Modifier.padding(start = 6.dp), style = Small)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(11.dp)
                    .border(1.5.dp, TextPrimary, RoundedCornerShape(3.dp))
            )
            Text("so far", Modifier.padding(start = 6.dp), style = Small)
        }
    }
}

private fun perDay(period: Period, locale: Locale) = String.format(locale, "%.1f / day", period.average)

@Composable
private fun Tile(modifier: Modifier, title: String, total: Int, range: String, previous: String) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(CardFill)
            .padding(16.dp)
    ) {
        Text(title, style = Small.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = Label.fontWeight))
        Spacer(Modifier.height(6.dp))
        Text(
            "$total",
            style = Label.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em, color = TextPrimary),
        )
        Spacer(Modifier.height(4.dp))
        Text(range, style = Small.copy(fontSize = 12.sp, lineHeight = 16.sp))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ButtonBorder))
        Spacer(Modifier.height(10.dp))
        Text(previous, style = Small.copy(fontSize = 12.sp, lineHeight = 16.sp))
    }
}

/** Weeks / Months segmented control. */
@Composable
private fun PeriodSwitch(showWeeks: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CardFill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((label, weeks) in listOf("Weeks" to true, "Months" to false)) {
            val selected = showWeeks == weeks
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) ButtonBorder else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(weeks) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Label.copy(fontSize = 14.sp, color = if (selected) TextPrimary else TextSecondary))
            }
        }
    }
}

/**
 * Bars scaled to the largest total, coloured by average per day.
 * The current period is outlined like today's square, because it is still filling.
 */
@Composable
private fun BarChart(
    title: String,
    averageText: String,
    periods: List<Period>,
    labels: List<Pair<String, String>>,
    columnWidth: Dp,
    barWidth: Dp,
) {
    val max = maxOf(1, periods.maxOf { it.total })
    val description = title + ". " + periods.indices.joinToString(", ") { i ->
        "${labels[i].first} ${labels[i].second}".trim() + ": ${periods[i].total}"
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, Modifier.alignByBaseline(), style = Label.copy(fontSize = 14.sp, color = TextPrimary))
            Text(averageText, Modifier.alignByBaseline(), style = Small.copy(fontSize = 12.sp, lineHeight = 16.sp))
        }
        Column(Modifier.padding(top = 16.dp).clearAndSetSemantics { contentDescription = description }) {
            Row(
                Modifier.fillMaxWidth().height(164.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                periods.forEach { period ->
                    val tracked = period.trackedDays > 0
                    Column(
                        Modifier.width(columnWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (tracked) "${period.total}" else "",
                            style = Small.copy(fontWeight = Label.fontWeight, color = TextPrimary, fontFeatureSettings = "tnum"),
                        )
                        val shape = RoundedCornerShape(4.dp)
                        Box(
                            Modifier
                                .width(barWidth)
                                .height(maxOf(3.dp, BarMaxHeight * (period.total.toFloat() / max)))
                                .clip(shape)
                                .background(Color(Stats.shadeFor(period, GridPalette.DARK)))
                                .then(if (period.current) Modifier.border(1.5.dp, TextPrimary, shape) else Modifier)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEach { (top, sub) ->
                    Column(Modifier.width(columnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        val axis = Small.copy(fontSize = 10.sp)
                        Text(top, style = axis, maxLines = 1, softWrap = false)
                        Box(Modifier.height(14.dp)) { Text(sub, style = axis, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
    }
}
