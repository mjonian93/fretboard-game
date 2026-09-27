package com.fretboardtrainer.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Small typed values stored as JSON in SharedPreferences. Unreadable values fall back to null. */
class JsonPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("fretboard_trainer", Context.MODE_PRIVATE)

    fun <T> load(key: String, serializer: KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() }

    fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, AppJson.encodeToString(serializer, value)).apply()
    }

    fun getFlag(key: String): Boolean = prefs.getBoolean(key, false)

    fun setFlag(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }
}
