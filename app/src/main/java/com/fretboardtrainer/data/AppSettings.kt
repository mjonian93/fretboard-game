package com.fretboardtrainer.data

import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class AppSettings(
    val tuningId: String = Tuning.STANDARD.id,
    val leftHanded: Boolean = false,
    /** Sounds quieter than this are ignored by the pitch detector. */
    val micGateDb: Float = DEFAULT_GATE_DB,
) {
    val tuning: Tuning get() = Tuning.byId(tuningId)

    val micGateRms: Double get() = 10.0.pow(micGateDb / 20.0)

    companion object {
        const val DEFAULT_GATE_DB = -48f
        const val MIN_GATE_DB = -75f
        const val MAX_GATE_DB = -20f
    }
}

class AppSettingsRepository(private val prefs: JsonPrefs) {
    private val _settings = MutableStateFlow(prefs.load(KEY, AppSettings.serializer()) ?: AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val value: AppSettings get() = _settings.value

    fun update(settings: AppSettings) {
        prefs.save(KEY, AppSettings.serializer(), settings)
        _settings.value = settings
    }

    private companion object {
        const val KEY = "app_settings"
    }
}
