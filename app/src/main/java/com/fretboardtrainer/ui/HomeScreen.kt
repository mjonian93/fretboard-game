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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onNameNote: () -> Unit, onFindNote: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Fretboard Trainer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModeCard(
                title = "1 · Name the note",
                needs = "No guitar needed",
                steps = listOf(
                    "A note lights up in green on the fretboard.",
                    "Tap its name on the buttons before time runs out.",
                    "Trains reading the neck: fret → note name.",
                    "Choose frets, strings and speed in ⚙ settings.",
                ),
                onPlay = onNameNote,
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                title = "2 · Find the note",
                needs = "Guitar + microphone",
                steps = listOf(
                    "Pick a position: position 2 = index finger on fret 2 (frets 2–5, stretching to 1 and 6).",
                    "A note name appears (e.g. Eb). Find it in the position and play it; the app listens.",
                    "Specific-octave option: ●○○ lowest, ○●○ middle, ○○● highest.",
                    "Afterwards you see every place that note lives in the position.",
                    "The mic hears pitch, not strings, so stay in position!",
                ),
                onPlay = onFindNote,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeCard(title: String, needs: String, steps: List<String>, onPlay: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(needs, color = TargetGreen, style = MaterialTheme.typography.labelLarge)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                steps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.padding(2.dp))
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Play") }
        }
    }
}
