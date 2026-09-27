package com.fretboardtrainer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fretboardtrainer.data.AppSettings
import com.fretboardtrainer.game.FindAllViewModel
import com.fretboardtrainer.game.FindNoteViewModel
import com.fretboardtrainer.game.GameViewModel
import com.fretboardtrainer.game.IntervalViewModel
import com.fretboardtrainer.game.ModeSettings
import com.fretboardtrainer.game.NameNoteViewModel
import com.fretboardtrainer.game.SessionState
import com.fretboardtrainer.game.TunerViewModel
import com.fretboardtrainer.ui.FindAllContent
import com.fretboardtrainer.ui.FindAllSettingsContent
import com.fretboardtrainer.ui.FindNoteContent
import com.fretboardtrainer.ui.FindNoteSettingsContent
import com.fretboardtrainer.ui.GameScaffold
import com.fretboardtrainer.ui.HomeScreen
import com.fretboardtrainer.ui.IntervalContent
import com.fretboardtrainer.ui.IntervalSettingsContent
import com.fretboardtrainer.ui.Mode
import com.fretboardtrainer.ui.NameNoteContent
import com.fretboardtrainer.ui.NameNoteSettingsContent
import com.fretboardtrainer.ui.StatsScreen
import com.fretboardtrainer.ui.TargetGreen
import com.fretboardtrainer.ui.TunerScreen

private sealed interface Screen {
    data object Home : Screen
    data class Game(val mode: Mode) : Screen
    data object Tuner : Screen
    data object Stats : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FretboardApp
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = TargetGreen, onPrimary = Color.Black, background = Color(0xFF151515))) {
                Surface(color = MaterialTheme.colorScheme.background) { App(app) }
            }
        }
    }
}

@Composable
private fun App(app: FretboardApp) {
    var screenName by rememberSaveable { mutableStateOf("home") }
    val screen = when (screenName) {
        "tuner" -> Screen.Tuner
        "stats" -> Screen.Stats
        "home" -> Screen.Home
        else -> Screen.Game(Mode.valueOf(screenName))
    }
    val home = { screenName = "home" }
    val appSettings by app.appSettings.settings.collectAsStateWithLifecycle()

    when (screen) {
        Screen.Home -> HomeScreen(
            appSettings = appSettings,
            onAppSettings = app.appSettings::update,
            onMode = { screenName = it.name },
            onTuner = { screenName = "tuner" },
            onStats = { screenName = "stats" },
        )
        is Screen.Game -> GameRoute(screen.mode, app, appSettings, home)
        Screen.Tuner -> TunerRoute(appSettings, home)
        Screen.Stats -> {
            BackHandler(onBack = home)
            val stats by app.stats.stats.collectAsStateWithLifecycle()
            StatsScreen(stats, appSettings.tuning, appSettings.leftHanded, onBack = home, onReset = app.stats::reset)
        }
    }
}

@Composable
private fun GameRoute(mode: Mode, app: FretboardApp, appSettings: AppSettings, onBack: () -> Unit) {
    val tuning = appSettings.tuning
    val mirrored = appSettings.leftHanded
    val gate = appSettings.micGateDb
    when (mode) {
        Mode.NAME_NOTE -> {
            val vm: NameNoteViewModel = viewModel()
            val round by vm.round.collectAsStateWithLifecycle()
            ModeRoute(mode, vm, app, needsMic = false, onBack = onBack,
                settingsContent = { s -> NameNoteSettingsContent(s, tuning, vm::updateSettings) },
            ) { session, s -> NameNoteContent(round, session, s, tuning, mirrored, vm::answer, Modifier.weight(1f)) }
        }
        Mode.FIND_NOTE -> {
            val vm: FindNoteViewModel = viewModel()
            val round by vm.round.collectAsStateWithLifecycle()
            ModeRoute(mode, vm, app, needsMic = true, onBack = onBack,
                settingsContent = { s -> FindNoteSettingsContent(s, vm::updateSettings) },
            ) { session, s -> FindNoteContent(round, session, s, tuning, mirrored, gate, Modifier.weight(1f)) }
        }
        Mode.FIND_ALL -> {
            val vm: FindAllViewModel = viewModel()
            val round by vm.round.collectAsStateWithLifecycle()
            ModeRoute(mode, vm, app, needsMic = true, onBack = onBack,
                settingsContent = { s -> FindAllSettingsContent(s, vm::updateSettings) },
            ) { session, s -> FindAllContent(round, session, s, tuning, mirrored, gate, Modifier.weight(1f)) }
        }
        Mode.INTERVALS -> {
            val vm: IntervalViewModel = viewModel()
            val round by vm.round.collectAsStateWithLifecycle()
            ModeRoute(mode, vm, app, needsMic = true, onBack = onBack,
                settingsContent = { s -> IntervalSettingsContent(s, vm::updateSettings) },
            ) { session, s -> IntervalContent(round, session, s, tuning, mirrored, gate, Modifier.weight(1f)) }
        }
    }
}

/** Wires any game mode to the shared scaffold: back, lifecycle, mic permission, first-time help. */
@Composable
private fun <S : ModeSettings> ModeRoute(
    mode: Mode,
    vm: GameViewModel<S>,
    app: FretboardApp,
    needsMic: Boolean,
    onBack: () -> Unit,
    settingsContent: @Composable ColumnScope.(S) -> Unit,
    content: @Composable ColumnScope.(SessionState, S) -> Unit,
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val back = {
        vm.stop()
        onBack()
    }
    BackHandler(onBack = back)
    // Release the microphone whenever the app leaves the screen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stop() }
    KeepScreenOn(session.running)
    val start = rememberMicGate(needsMic, onGranted = vm::start, onDenied = vm::onMicPermissionDenied)
    val helpKey = "help.${mode.name}"

    GameScaffold(
        title = mode.title,
        help = mode.help,
        showHelpFirst = !app.prefs.getFlag(helpKey),
        onHelpShown = { app.prefs.setFlag(helpKey) },
        session = session,
        config = settings.session,
        onBack = back,
        onStart = start,
        onStop = { vm.stop(showSummary = true) },
        onBpm = vm::changeBpm,
        onDismissSummary = vm::dismissSummary,
        settingsContent = { settingsContent(settings) },
    ) { content(session, settings) }
}

@Composable
private fun TunerRoute(appSettings: AppSettings, onBack: () -> Unit) {
    val vm: TunerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val back = {
        vm.stop()
        onBack()
    }
    BackHandler(onBack = back)
    val start = rememberMicGate(true, onGranted = vm::start, onDenied = vm::onMicPermissionDenied)
    val calibrate = rememberMicGate(true, onGranted = vm::calibrate, onDenied = vm::onMicPermissionDenied)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { start() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stop() }
    KeepScreenOn(true)
    TunerScreen(state, appSettings.tuning, appSettings.micGateDb, back, calibrate, vm::dismissCalibration)
}

/** Returns an action that runs [onGranted], first asking for the mic permission when [needsMic]. */
@Composable
private fun rememberMicGate(needsMic: Boolean, onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else onDenied()
    }
    return {
        val granted = !needsMic || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
