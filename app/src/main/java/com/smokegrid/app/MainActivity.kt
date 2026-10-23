package com.smokegrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Red = Color(0xFFE5534B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0D1117), surface = Color(0xFF0D1117))) {
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
    val smokedToday = (counts[today] ?: 0) > 0
    val grid = remember(version, today) { HeatmapRenderer.render(counts, today, start, 1200, 360) }
    val days = remember(today) { (0L until 60L).map { today.minusDays(it) } }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }

    fun toggle(date: LocalDate) {
        SmokeLog.toggle(context, date)
        scope.launch { SmokeWidget().updateAll(context) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item { Column {
            Text(
                text = if (smokedToday) "Smoked today" else "🚭 $streak day${if (streak == 1L) "" else "s"} clean",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(16.dp))
            Image(
                bitmap = grid.asImageBitmap(),
                contentDescription = "Smoking history grid",
                modifier = Modifier.fillMaxWidth().aspectRatio(1200f / 360f),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { toggle(today) },
                colors = ButtonDefaults.buttonColors(containerColor = if (smokedToday) Color(0xFF373E47) else Red),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (smokedToday) "Undo today" else "I smoked today", color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text("Fix a past day", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        } }
        items(days) { date ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (date == today) "Today" else date.format(dateFormat),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = (counts[date] ?: 0) > 0,
                    onCheckedChange = { toggle(date) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Red),
                )
            }
        }
    }
}
