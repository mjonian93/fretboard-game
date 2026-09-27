package com.fretboardtrainer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Lifetime results for one practice item (a fret, a note name, an interval...). */
@Serializable
data class Stat(
    val attempts: Int = 0,
    val correct: Int = 0,
    /** Sum of answer times of correct, timed answers. */
    val totalMillis: Long = 0,
    val timedCount: Int = 0,
) {
    val accuracy: Float get() = if (attempts == 0) 0f else correct.toFloat() / attempts

    val averageMillis: Long? get() = if (timedCount == 0) null else totalMillis / timedCount

    fun record(correct: Boolean, millis: Long?): Stat = copy(
        attempts = attempts + 1,
        correct = this.correct + if (correct) 1 else 0,
        totalMillis = totalMillis + if (correct && millis != null) millis else 0,
        timedCount = timedCount + if (correct && millis != null) 1 else 0,
    )

    /**
     * How often to pick this item: unseen items get 3, items you always get right
     * approach 1, items you keep missing approach 5.
     */
    val pickWeight: Double get() = 1.0 + 4.0 * (attempts - correct + 1) / (attempts + 2)
}

/** Stat keys; one namespace per kind of question. */
object StatKeys {
    fun fret(tuningId: String, string: Int, fret: Int) = "fret:$tuningId:$string:$fret"
    fun note(pitchClass: Int) = "note:$pitchClass"
    fun interval(semitones: Int) = "interval:$semitones"
}

/** Persists stats to a JSON file so weak spots survive between sessions. */
class StatsRepository(private val file: File) {
    private val serializer = MapSerializer(String.serializer(), Stat.serializer())
    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Map<String, Stat>> = _stats.asStateFlow()

    operator fun get(key: String): Stat = _stats.value[key] ?: Stat()

    @Synchronized
    fun record(key: String, correct: Boolean, millis: Long?) {
        val updated = _stats.value + (key to get(key).record(correct, millis))
        _stats.value = updated
        save(updated)
    }

    @Synchronized
    fun reset() {
        _stats.value = emptyMap()
        file.delete()
    }

    private fun load(): Map<String, Stat> =
        runCatching { AppJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    private fun save(stats: Map<String, Stat>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(serializer, stats))
            tmp.renameTo(file)
        }
    }
}
