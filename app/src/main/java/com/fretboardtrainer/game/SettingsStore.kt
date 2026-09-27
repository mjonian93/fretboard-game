package com.fretboardtrainer.game

import android.content.Context
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.HandPosition

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("practice_settings", Context.MODE_PRIVATE)

    fun loadNameNote(): NameNoteSettings {
        val d = NameNoteSettings()
        return NameNoteSettings(
            maxFret = prefs.getInt("name.maxFret", d.maxFret).coerceIn(1, FRET_COUNT),
            strings = prefs.getStringSet("name.strings", null)
                ?.mapNotNull { it.toIntOrNull() }?.toSet()?.ifEmpty { null } ?: d.strings,
            naturalsOnly = prefs.getBoolean("name.naturalsOnly", d.naturalsOnly),
            secondsPerNote = prefs.getInt("name.secondsPerNote", d.secondsPerNote),
        )
    }

    fun saveNameNote(s: NameNoteSettings) {
        prefs.edit()
            .putInt("name.maxFret", s.maxFret)
            .putStringSet("name.strings", s.strings.map { it.toString() }.toSet())
            .putBoolean("name.naturalsOnly", s.naturalsOnly)
            .putInt("name.secondsPerNote", s.secondsPerNote)
            .apply()
    }

    fun loadFindNote(): FindNoteSettings {
        val d = FindNoteSettings()
        return FindNoteSettings(
            position = prefs.getInt("find.position", d.position).coerceIn(HandPosition.MIN, HandPosition.MAX),
            naturalsOnly = prefs.getBoolean("find.naturalsOnly", d.naturalsOnly),
            exactOctave = prefs.getBoolean("find.exactOctave", d.exactOctave),
            secondsPerNote = prefs.getInt("find.secondsPerNote", d.secondsPerNote),
        )
    }

    fun saveFindNote(s: FindNoteSettings) {
        prefs.edit()
            .putInt("find.position", s.position)
            .putBoolean("find.naturalsOnly", s.naturalsOnly)
            .putBoolean("find.exactOctave", s.exactOctave)
            .putInt("find.secondsPerNote", s.secondsPerNote)
            .apply()
    }
}
