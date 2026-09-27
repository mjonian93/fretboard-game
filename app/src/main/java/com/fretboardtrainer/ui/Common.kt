package com.fretboardtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fretboardtrainer.game.SessionConfig
import com.fretboardtrainer.game.SessionState
import com.fretboardtrainer.game.SessionSummary
import com.fretboardtrainer.game.TimingMode
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.PitchReading
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Frame shared by all practice modes: header with score and controls, the countdown,
 * and the settings, help and end-of-session dialogs.
 */
@Composable
fun GameScaffold(
    title: String,
    help: List<String>,
    showHelpFirst: Boolean,
    onHelpShown: () -> Unit,
    session: SessionState,
    config: SessionConfig,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBpm: (Int) -> Unit,
    onDismissSummary: () -> Unit,
    settingsContent: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(showHelpFirst) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showHelp = true }) { Icon(Icons.Filled.Info, contentDescription = "How to play") }
            Spacer(Modifier.weight(1f))
            if (config.timing == TimingMode.TEMPO) BpmControl(session.bpm, onBpm)
            Stat("Note", if (config.length == 0) "${session.round}" else "${session.round}/${config.length}")
            Stat("Score", "${session.score.hits}/${session.score.rounds}")
            Stat("Streak", session.score.streak.toString())
            IconButton(onClick = {
                onStop()
                showSettings = true
            }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            if (session.running) {
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = WrongRed, contentColor = Color.White)) {
                    Text("Stop")
                }
            } else {
                Button(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Start")
                }
            }
        }
        val timeLeft = session.timeLeft
        if (session.running && timeLeft == null) {
            Text("Untimed: take your time", color = MutedGray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.height(18.dp))
        } else {
            LinearProgressIndicator(
                progress = { if (session.running) timeLeft ?: 0f else 0f },
                color = if ((timeLeft ?: 1f) > 0.3f) TargetGreen else MissOrange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp),
            )
        }
        content()
    }

    if (showSettings) {
        LargeDialog("$title: settings", onDismiss = { showSettings = false }, content = settingsContent)
    }
    if (showHelp) {
        HelpDialog(title, help) {
            showHelp = false
            onHelpShown()
        }
    }
    session.summary?.let { SummaryDialog(it, onDismiss = onDismissSummary, onAgain = { onDismissSummary(); onStart() }) }
}

@Composable
private fun BpmControl(bpm: Int, onBpm: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onBpm(-5) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Stat("BPM", bpm.toString())
        TextButton(onClick = { onBpm(5) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedGray)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** A dialog using most of the screen, for long scrolling content in landscape. */
@Composable
fun LargeDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.92f),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun HelpDialog(title: String, lines: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("How to play: $title") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { Text("• $it") }
            }
        },
    )
}

@Composable
private fun SummaryDialog(summary: SessionSummary, onDismiss: () -> Unit, onAgain: () -> Unit) {
    val percent = if (summary.rounds == 0) 0 else summary.correct * 100 / summary.rounds
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onAgain) { Text("Play again") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(if (summary.completed) "Session complete" else "Session results") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$percent% correct (${summary.correct} of ${summary.rounds})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                summary.averageMillis?.let { Text("Average answer time: ${formatSeconds(it)}") }
                Text("Best streak: ${summary.bestStreak}")
                summary.finalBpm?.let { Text("Tempo reached: $it BPM") }
                if (summary.weakest.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Practise more: ${summary.weakest.joinToString(", ")}", color = MissOrange)
                } else if (summary.correct == summary.rounds) {
                    Text("Perfect session!", color = TargetGreen)
                }
            }
        },
    )
}

/** Timing and session-length controls shared by every mode's settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionSettings(config: SessionConfig, onChange: (SessionConfig) -> Unit, perWhat: String = "note") {
    HorizontalDivider()
    Text("Timing", style = MaterialTheme.typography.titleSmall)
    ChipRow(
        options = listOf(TimingMode.UNTIMED to "Untimed", TimingMode.SECONDS to "Seconds", TimingMode.TEMPO to "Tempo (BPM)"),
        selected = config.timing,
    ) { onChange(config.copy(timing = it)) }
    when (config.timing) {
        TimingMode.UNTIMED -> Text("No clock: every note waits for you.", color = MutedGray)
        TimingMode.SECONDS -> SettingSlider("Time per $perWhat: ${config.seconds} s", config.seconds, 2..20) {
            onChange(config.copy(seconds = it))
        }
        TimingMode.TEMPO -> {
            SettingSlider("Tempo: ${config.bpm} BPM", config.bpm / 5, SessionConfig.MIN_BPM / 5..SessionConfig.MAX_BPM / 5) {
                onChange(config.copy(bpm = it * 5))
            }
            Text("Beats per $perWhat", style = MaterialTheme.typography.titleSmall)
            ChipRow(listOf(1, 2, 4, 8).map { it to "$it" }, config.beatsPerNote) { onChange(config.copy(beatsPerNote = it)) }
            val seconds = config.noteMillis(config.bpm)!! / 1000.0
            Text("= %.1f s per $perWhat. Use −/+ during play to change the tempo.".format(seconds), color = MutedGray)
            SettingSwitch("Metronome", "Click on every beat", config.metronome) { onChange(config.copy(metronome = it)) }
            SettingSwitch(
                "Auto speed-up",
                "+${SessionConfig.SPEED_UP_BPM} BPM after every ${SessionConfig.SPEED_UP_EVERY} correct in a row",
                config.autoSpeedUp,
            ) { onChange(config.copy(autoSpeedUp = it)) }
        }
    }
    Text("Session length", style = MaterialTheme.typography.titleSmall)
    ChipRow(listOf(10 to "10", 20 to "20", 50 to "50", 0 to "Endless"), config.length) { onChange(config.copy(length = it)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
fun SettingSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedGray)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onCheckedChange = onChange)
    }
}

/** Mic loudness on a -60..0 dB scale, so you can see the app hears you. [gateDb] marks the sensitivity threshold. */
@Composable
fun MicLevelMeter(level: Float, gateDb: Float, modifier: Modifier = Modifier) {
    val db = if (level > 0f) 20 * log10(level) else -80f
    fun fraction(d: Float) = ((d + 80f) / 80f).coerceIn(0f, 1f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Mic ", color = MutedGray)
        Box(Modifier.width(90.dp).height(10.dp).background(Color(0xFF333333), RoundedCornerShape(5.dp))) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction(db))
                    .background(if (db > gateDb) TargetGreen else MutedGray, RoundedCornerShape(5.dp)),
            )
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction(gateDb)).padding(end = 0.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(Color.White))
            }
        }
    }
}

/** Bottom status line for listening modes: feedback message, mic meter and heard note. */
@Composable
fun ListeningStatus(message: String, color: Color, session: SessionState, gateDb: Float) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(session.error ?: message, color = if (session.error != null) WrongRed else color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (session.running) {
            MicLevelMeter(session.micLevel, gateDb, Modifier.padding(end = 12.dp))
            Text(heardText(session.heard), color = Color(0xFFB0B0B0))
        }
    }
}

fun heardText(reading: PitchReading?): String {
    if (reading == null) return "Hearing: …"
    val cents = reading.cents.roundToInt()
    return "Hearing: ${Notes.name(reading.midi)} (${if (cents >= 0) "+" else ""}$cents¢)"
}

fun formatSeconds(millis: Long): String = "%.1f s".format(millis / 1000.0)
