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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fretboardtrainer.game.FindNoteViewModel
import com.fretboardtrainer.game.NameNoteViewModel
import com.fretboardtrainer.ui.FindNoteScreen
import com.fretboardtrainer.ui.FindNoteSettingsDialog
import com.fretboardtrainer.ui.HomeScreen
import com.fretboardtrainer.ui.NameNoteScreen
import com.fretboardtrainer.ui.NameNoteSettingsDialog
import com.fretboardtrainer.ui.TargetGreen

private enum class Screen { Home, NameNote, FindNote }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = TargetGreen, onPrimary = Color.Black, background = Color(0xFF151515))) {
                Surface(color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val home = { screen = Screen.Home }
    when (screen) {
        Screen.Home -> HomeScreen(onNameNote = { screen = Screen.NameNote }, onFindNote = { screen = Screen.FindNote })
        Screen.NameNote -> NameNoteRoute(home)
        Screen.FindNote -> FindNoteRoute(home)
    }
}

@Composable
private fun NameNoteRoute(onBack: () -> Unit, vm: NameNoteViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val back = {
        vm.stop()
        onBack()
    }
    BackHandler(onBack = back)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stop() }
    KeepScreenOn(state.running)

    NameNoteScreen(
        state = state,
        onBack = back,
        onStart = vm::start,
        onStop = vm::stop,
        onOpenSettings = {
            vm.stop()
            showSettings = true
        },
        onAnswer = vm::answer,
    )
    if (showSettings) {
        NameNoteSettingsDialog(state.settings, onChange = vm::updateSettings, onDismiss = { showSettings = false })
    }
}

@Composable
private fun FindNoteRoute(onBack: () -> Unit, vm: FindNoteViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalView.current.context
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.onMicPermissionDenied()
    }
    val back = {
        vm.stop()
        onBack()
    }
    BackHandler(onBack = back)
    // Release the microphone whenever the app leaves the screen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stop() }
    KeepScreenOn(state.running)

    FindNoteScreen(
        state = state,
        onBack = back,
        onStart = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) vm.start() else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onStop = vm::stop,
        onOpenSettings = {
            vm.stop()
            showSettings = true
        },
    )
    if (showSettings) {
        FindNoteSettingsDialog(state.settings, onChange = vm::updateSettings, onDismiss = { showSettings = false })
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
