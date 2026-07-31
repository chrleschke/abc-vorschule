package app.abcvorschule.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.ttsDebugDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_debug")

@Serializable
data class TtsDebugExportEntry(
    val id: String,
    val sourceFile: String,
    val originalText: String,
    val editedText: String,
)

class TtsDebugRepository(
    private val dataStore: DataStore<Preferences>,
    private val exportFile: File,
) {
    private val json = Json { encodeDefaults = true }
    private val key = stringPreferencesKey("tts_debug_overrides_v1")
    private val mutex = Mutex()

    val overridesFlow: Flow<Map<String, String>> = dataStore.data.map { prefs -> currentFrom(prefs) }

    suspend fun current(): Map<String, String> = overridesFlow.first()

    suspend fun setOverride(id: String, text: String, entries: List<TtsDebugEntry>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = updateOverrides { it + (id to text) }
                writeExportFile(entries, updated)
            }
        }
    }

    suspend fun clearOverride(id: String, entries: List<TtsDebugEntry>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = updateOverrides { it - id }
                writeExportFile(entries, updated)
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                updateOverrides { emptyMap() }
                exportFile.writeText("[]")
            }
        }
    }

    private suspend fun updateOverrides(
        transform: (Map<String, String>) -> Map<String, String>,
    ): Map<String, String> {
        var result = emptyMap<String, String>()
        dataStore.edit { prefs ->
            result = transform(currentFrom(prefs))
            prefs[key] = json.encodeToString(result)
        }
        return result
    }

    private fun writeExportFile(entries: List<TtsDebugEntry>, overrides: Map<String, String>) {
        val byId = entries.associateBy { it.id }
        val export = overrides.mapNotNull { (id, editedText) ->
            byId[id]?.let { entry ->
                TtsDebugExportEntry(
                    id = id,
                    sourceFile = entry.sourceFile,
                    originalText = entry.originalText,
                    editedText = editedText,
                )
            }
        }
        exportFile.writeText(json.encodeToString(export))
    }

    private fun currentFrom(prefs: Preferences): Map<String, String> =
        prefs[key]?.let { decode(it) } ?: emptyMap()

    private fun decode(raw: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    companion object {
        fun fromContext(context: Context): TtsDebugRepository = TtsDebugRepository(
            dataStore = context.ttsDebugDataStore,
            exportFile = File(context.filesDir, "tts_debug_export.json"),
        )
    }
}
