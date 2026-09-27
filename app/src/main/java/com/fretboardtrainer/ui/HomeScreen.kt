package com.fretboardtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fretboardtrainer.data.AppSettings
import com.fretboardtrainer.music.Tuning
import kotlin.math.roundToInt

enum class Mode(val title: String, val needs: String, val summary: String, val help: List<String>) {
    NAME_NOTE("Name the note", "No guitar needed", "A fret lights up: tap its name.", Help.NAME_NOTE),
    FIND_NOTE("Find the note", "Guitar + mic", "A note name appears: play it in a hand position.", Help.FIND_NOTE),
    FIND_ALL("Find them all", "Guitar + mic", "Play every octave of a note, in a position or across the neck.", Help.FIND_ALL),
    INTERVALS("Intervals", "Guitar + mic", "A root lights up: play the 3rd, 5th, 7th… above it.", Help.INTERVALS),
}

@Composable
fun HomeScreen(
    appSettings: AppSettings,
    onAppSettings: (AppSettings) -> Unit,
    onMode: (Mode) -> Unit,
    onTuner: () -> Unit,
    onStats: () -> Unit,
) {
    var help by remember { mutableStateOf<Mode?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fretboard Trainer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${appSettings.tuning.name} tuning" + if (appSettings.leftHanded) " · left-handed" else "", color = MutedGray)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onTuner) { Text("Tuner") }
            OutlinedButton(onClick = onStats) { Text("Progress") }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
        for (row in Mode.entries.chunked(2)) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (mode in row) ModeCard(mode, onHelp = { help = mode }, onPlay = { onMode(mode) }, modifier = Modifier.weight(1f))
            }
        }
    }

    help?.let { HelpDialog(it.title, it.help) { help = null } }
    if (showSettings) AppSettingsDialog(appSettings, onAppSettings) { showSettings = false }
}

@Composable
private fun ModeCard(mode: Mode, onHelp: () -> Unit, onPlay: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${mode.ordinal + 1} · ${mode.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(mode.needs, color = TargetGreen, style = MaterialTheme.typography.labelMedium)
                Text(mode.summary, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCFCFCF))
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onPlay) { Text("Play") }
                TextButton(onClick = onHelp) { Text("How to play") }
            }
        }
    }
}

@Composable
private fun AppSettingsDialog(settings: AppSettings, onChange: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    LargeDialog("Settings", onDismiss) {
        Text("Tuning", style = MaterialTheme.typography.titleSmall)
        ChipRow(Tuning.ALL.map { it.id to "${it.name} (${it.letters})" }, settings.tuningId) {
            onChange(settings.copy(tuningId = it))
        }
        SettingSwitch("Left-handed", "Mirror the fretboard: nut on the right", settings.leftHanded) {
            onChange(settings.copy(leftHanded = it))
        }
        SettingSlider(
            "Mic sensitivity: ignore sounds below ${settings.micGateDb.roundToInt()} dB",
            settings.micGateDb.roundToInt(),
            AppSettings.MIN_GATE_DB.toInt()..AppSettings.MAX_GATE_DB.toInt(),
        ) { onChange(settings.copy(micGateDb = it.toFloat())) }
        Text(
            "Lower = more sensitive (quiet mics), higher = ignores more background noise. " +
                "Easiest: use \"Calibrate mic\" in the Tuner.",
            style = MaterialTheme.typography.bodySmall, color = MutedGray,
        )
        TextButton(onClick = { onChange(settings.copy(micGateDb = AppSettings.DEFAULT_GATE_DB)) }) { Text("Reset sensitivity") }
    }
}
