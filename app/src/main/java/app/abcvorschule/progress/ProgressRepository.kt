package app.abcvorschule.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Eine beschädigte Preferences-Datei (abgewürgter Schreibvorgang, voller Speicher)
 * darf das Kind nicht dauerhaft aussperren: ohne Handler wirft `dataStore.data`
 * eine CorruptionException, `bootstrap()` fängt sie in den Fehlerschirm — und der
 * Fehlerschirm hat keinen Weg zurück, weil bootstrap nur im init läuft. Lieber
 * einmal von vorn anfangen als eine App, die sich nicht mehr öffnen lässt.
 */
private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "learner_progress",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class ProgressRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val key = stringPreferencesKey("learner_progress_v1")

    /**
     * `catch` als zweite Reihe hinter dem CorruptionHandler: der greift nur bei
     * beschädigten Dateien, ein IO-Fehler beim Lesen (Speicher voll, Datei
     * gesperrt) kommt weiterhin als Exception aus dem Flow und würde den
     * Sammler mitreißen.
     */
    val progressFlow: Flow<LearnerProgress> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> currentFrom(prefs) }

    suspend fun current(): LearnerProgress = progressFlow.first()

    suspend fun update(transform: (LearnerProgress) -> LearnerProgress): LearnerProgress {
        var result = LearnerProgress()
        dataStore.edit { prefs ->
            result = transform(currentFrom(prefs))
            prefs[key] = json.encodeToString(result)
        }
        return result
    }

    suspend fun setParentMode(mode: ParentMode): LearnerProgress =
        update { it.copy(parentMode = mode) }

    suspend fun setUnlockAllLessons(enabled: Boolean): LearnerProgress =
        update { it.copy(unlockAllLessons = enabled) }

    suspend fun saveSession(snapshot: SessionSnapshot?): LearnerProgress =
        update { it.copy(unfinishedSession = snapshot) }

    private fun currentFrom(prefs: Preferences): LearnerProgress =
        prefs[key]?.let { decode(it) } ?: LearnerProgress()

    private fun decode(raw: String): LearnerProgress =
        runCatching { json.decodeFromString<LearnerProgress>(raw) }.getOrDefault(LearnerProgress())

    companion object {
        fun fromContext(context: Context): ProgressRepository =
            ProgressRepository(context.progressDataStore)
    }
}
