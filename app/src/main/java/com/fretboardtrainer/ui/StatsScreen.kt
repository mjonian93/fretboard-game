package com.fretboardtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fretboardtrainer.data.Stat
import com.fretboardtrainer.data.StatKeys
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.FretPosition
import com.fretboardtrainer.music.Interval
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.STRING_COUNT
import com.fretboardtrainer.music.Tuning

private enum class StatsTab { FRETBOARD, NOTES, INTERVALS }

/** Red (0%) → orange (50%) → green (100%). */
fun heatColor(accuracy: Float): Color =
    if (accuracy < 0.5f) lerp(WrongRed, MissOrange, accuracy * 2) else lerp(MissOrange, TargetGreen, (accuracy - 0.5f) * 2)

@Composable
fun StatsScreen(stats: Map<String, Stat>, tuning: Tuning, mirrored: Boolean, onBack: () -> Unit, onReset: () -> Unit) {
    var tab by remember { mutableStateOf(StatsTab.FRETBOARD) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChipRow(
                listOf(StatsTab.FRETBOARD to "Fretboard", StatsTab.NOTES to "Notes", StatsTab.INTERVALS to "Intervals"),
                tab,
            ) { tab = it }
            Spacer(Modifier.weight(1f))
            if (confirmReset) {
                Text("Erase all progress?", color = WrongRed)
                TextButton(onClick = { onReset(); confirmReset = false }, colors = ButtonDefaults.textButtonColors(contentColor = WrongRed)) { Text("Erase") }
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            } else {
                TextButton(onClick = { confirmReset = true }) { Text("Reset") }
            }
        }
        when (tab) {
            StatsTab.FRETBOARD -> FretboardHeatmap(stats, tuning, mirrored, Modifier.weight(1f))
            StatsTab.NOTES -> StatList(
                "From Find the note and Find them all",
                (0 until 12).map { Notes.fullName(it, withOctave = false) to (stats[StatKeys.note(it)] ?: Stat()) },
                Modifier.weight(1f),
            )
            StatsTab.INTERVALS -> StatList(
                "From Intervals",
                Interval.entries.map { "${it.shortName} · ${it.longName}" to (stats[StatKeys.interval(it.semitones)] ?: Stat()) },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FretboardHeatmap(stats: Map<String, Stat>, tuning: Tuning, mirrored: Boolean, modifier: Modifier) {
    val markers = (1..STRING_COUNT).flatMap { s -> (0..FRET_COUNT).map { FretPosition(s, it) } }.mapNotNull { pos ->
        val stat = stats[StatKeys.fret(tuning.id, pos.string, pos.fret)] ?: return@mapNotNull null
        FretMarker(pos, heatColor(stat.accuracy), Notes.name(tuning.midiAt(pos), withOctave = false))
    }
    Column(modifier) {
        Text(
            if (markers.isEmpty()) "Play \"Name the note\" to fill in your fretboard map (${tuning.name} tuning)."
            else "Your accuracy per fret in \"Name the note\" (${tuning.name}). Red = needs work, green = known, empty = not practised yet.",
            color = MutedGray,
        )
        Fretboard(FRET_COUNT, 0..FRET_COUNT, markers, Modifier.weight(1f).fillMaxWidth(), mirrored)
    }
}

@Composable
private fun StatList(source: String, rows: List<Pair<String, Stat>>, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$source. Lifetime accuracy (first try) and average answer time.", color = MutedGray)
        for ((label, stat) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.width(170.dp), fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f).height(14.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(7.dp))) {
                    if (stat.attempts > 0) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(stat.accuracy.coerceAtLeast(0.02f))
                                .background(heatColor(stat.accuracy), RoundedCornerShape(7.dp)),
                        )
                    }
                }
                Text(
                    if (stat.attempts == 0) "not practised" else
                        "${(stat.accuracy * 100).toInt()}% of ${stat.attempts}" + (stat.averageMillis?.let { " · ${formatSeconds(it)}" } ?: ""),
                    color = MutedGray, modifier = Modifier.width(170.dp).padding(start = 12.dp),
                )
            }
        }
    }
}
