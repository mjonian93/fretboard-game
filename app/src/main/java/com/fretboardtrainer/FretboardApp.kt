package com.fretboardtrainer

import android.app.Application
import com.fretboardtrainer.audio.SoundPlayer
import com.fretboardtrainer.data.AppSettingsRepository
import com.fretboardtrainer.data.JsonPrefs
import com.fretboardtrainer.data.StatsRepository
import java.io.File

/** App-wide singletons. */
class FretboardApp : Application() {
    val prefs by lazy { JsonPrefs(this) }
    val appSettings by lazy { AppSettingsRepository(prefs) }
    val stats by lazy { StatsRepository(File(filesDir, "stats.json")) }
    val sound by lazy { SoundPlayer() }
}
