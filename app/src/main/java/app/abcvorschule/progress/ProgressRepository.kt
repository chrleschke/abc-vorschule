package app.abcvorschule.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "learner_progress")

class ProgressRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val key = stringPreferencesKey("learner_progress_v1")

    val progressFlow: Flow<LearnerProgress> = dataStore.data.map { prefs ->
        currentFrom(prefs)
    }

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
