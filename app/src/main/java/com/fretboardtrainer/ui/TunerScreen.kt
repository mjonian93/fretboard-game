package com.fretboardtrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fretboardtrainer.game.Calibration
import com.fretboardtrainer.game.TunerState
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.STRING_COUNT
import com.fretboardtrainer.music.Tuning
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TunerScreen(
    state: TunerState,
    tuning: Tuning,
    gateDb: Float,
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    onDismissCalibration: () -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    val reading = state.reading
    val inTune = reading != null && abs(state.cents) < 5

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Tuner · ${tuning.name} (${tuning.letters})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showHelp = true }) { Icon(Icons.Filled.Info, contentDescription = "How to use") }
            Spacer(Modifier.weight(1f))
            MicLevelMeter(state.micLevel, gateDb)
            OutlinedButton(onClick = onCalibrate) { Text("Calibrate mic") }
        }

        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                reading?.let { Notes.name(it.midi) } ?: "–",
                fontSize = 80.sp, fontWeight = FontWeight.Bold,
                color = if (inTune) TargetGreen else Color.White,
            )
            Text(
                reading?.let { "%.1f Hz  ·  %+d¢".format(it.frequency, state.cents.roundToInt()) } ?: (state.error ?: "Play a string"),
                color = if (state.error != null) WrongRed else MutedGray,
            )
            CentsNeedle(if (reading != null) state.cents else null, Modifier.fillMaxWidth(0.7f).height(48.dp).padding(top = 8.dp))
        }

        // Open strings, low to high; the one you're playing is highlighted.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (string in STRING_COUNT downTo 1) {
                val active = reading != null && state.nearestString == string
                val off = state.stringCents.roundToInt()
                Column(
                    Modifier.weight(1f)
                        .background(if (active) Color(0xFF263B2E) else Color(0xFF1F1F1F), RoundedCornerShape(10.dp))
                        .border(2.dp, if (active) (if (abs(off) < 5) TargetGreen else MissOrange) else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("String $string", style = MaterialTheme.typography.labelSmall, color = MutedGray)
                    Text(Notes.name(tuning.openMidi(string)), fontWeight = FontWeight.Bold)
                    Text(if (active) "%+d¢".format(off) else " ", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showHelp) HelpDialog("Tuner", Help.TUNER) { showHelp = false }
    state.calibration?.let { CalibrationDialog(it, onRetry = onCalibrate, onDismiss = onDismissCalibration) }
}

/** −50…+50 cents scale with a needle; green zone in the middle. */
@Composable
private fun CentsNeedle(cents: Double?, modifier: Modifier) {
    Canvas(modifier) {
        val mid = size.width / 2
        val y = size.height / 2
        drawLine(Color(0xFF444444), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
        drawLine(TargetGreen.copy(alpha = 0.5f), Offset(mid - size.width * 0.05f, y), Offset(mid + size.width * 0.05f, y), 10.dp.toPx())
        for (c in -50..50 step 10) {
            val x = mid + c / 50f * mid
            drawLine(MutedGray, Offset(x, y - 8.dp.toPx()), Offset(x, y + 8.dp.toPx()), 2.dp.toPx())
        }
        if (cents != null) {
            val x = mid + (cents.toFloat().coerceIn(-50f, 50f) / 50f) * mid
            drawLine(if (abs(cents) < 5) TargetGreen else MissOrange, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
        }
    }
}

@Composable
private fun CalibrationDialog(calibration: Calibration, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Calibrate microphone") },
        confirmButton = {
            when (calibration) {
                is Calibration.Done -> Button(onClick = onDismiss) { Text("Done") }
                is Calibration.Failed -> Button(onClick = onRetry) { Text("Try again") }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (calibration is Calibration.Done) "Close" else "Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (calibration) {
                    is Calibration.Quiet -> {
                        Text("1. Stay quiet: measuring the room noise…", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { calibration.progress }, modifier = Modifier.fillMaxWidth())
                        Text("2. Then play a note and let it ring.", color = MutedGray)
                    }
                    is Calibration.Play -> {
                        Text("1. Room noise measured ✓", color = MutedGray)
                        Text("2. Play a note now and let it ring!", fontWeight = FontWeight.Bold, color = TargetGreen)
                        LinearProgressIndicator(progress = { calibration.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is Calibration.Done -> {
                        Text("All set!", fontWeight = FontWeight.Bold, color = TargetGreen)
                        Text("Room noise: %.0f dB · Guitar: %.0f dB".format(calibration.noiseDb, calibration.noteDb))
                        Text("Sensitivity set to %.0f dB. You can fine-tune it in Settings on the home screen.".format(calibration.gateDb), color = MutedGray)
                    }
                    is Calibration.Failed -> Text(calibration.message, color = WrongRed)
                }
            }
        },
    )
}
