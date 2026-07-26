package app.abcvorschule.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Minimal in-memory [DataStore] so [ProgressRepository] can be exercised without
 * Android's real Preferences DataStore (which needs an instrumented environment).
 */
private class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class ProgressRepositoryTest {

    /** Same key ProgressRepository uses internally — a separate factory call still
     * matches on lookup since Preferences.Key equality is name-based. */
    private val rawKey = stringPreferencesKey("learner_progress_v1")

    @Test
    fun fullProgressRoundTripsThroughUpdateAndCurrent() = runTest {
        val repository = ProgressRepository(FakePreferencesDataStore())
        val snapshot = SessionSnapshot(
            lessonId = "lesson_2",
            trainerIndex = 3,
            roundIndex = 1,
            pointsEarned = 4,
            packId = "pack_v1",
        )
        repository.update {
            LearnerProgress(
                parentMode = ParentMode.Advanced,
                points = 42,
                atomStats = mapOf(
                    "a" to SkillStats(attempts = 5, correct = 4, resolves = 1, consecutiveCorrect = 2),
                ),
                mathStats = mapOf(
                    "1+1" to SkillStats(attempts = 3, correct = 3, autoScaffold = ScaffoldLevel.Advanced),
                ),
                taskStats = mapOf(
                    "task_1" to SkillStats(attempts = 6, correct = 6),
                ),
                unfinishedSession = snapshot,
            )
        }

        val loaded = repository.current()
        assertEquals(ParentMode.Advanced, loaded.parentMode)
        assertEquals(42, loaded.points)
        assertEquals(
            SkillStats(attempts = 5, correct = 4, resolves = 1, consecutiveCorrect = 2),
            loaded.atomStats["a"],
        )
        assertEquals(
            SkillStats(attempts = 3, correct = 3, autoScaffold = ScaffoldLevel.Advanced),
            loaded.mathStats["1+1"],
        )
        assertEquals(SkillStats(attempts = 6, correct = 6), loaded.taskStats["task_1"])
        assertEquals(snapshot, loaded.unfinishedSession)
    }

    @Test
    fun unknownJsonKeyStillDecodes() = runTest {
        // A future app version may add a field this build doesn't know about yet;
        // ignoreUnknownKeys must let a downgrade still read the rest of the payload.
        val payload = """
            {"parentMode":"Beginner","points":7,"atomStats":{},"mathStats":{},
             "taskStats":{},"unfinishedSession":null,"futureField":"???"}
        """.trimIndent()
        val repository = ProgressRepository(FakePreferencesDataStore(preferencesOf(rawKey to payload)))

        val loaded = repository.current()
        assertEquals(ParentMode.Beginner, loaded.parentMode)
        assertEquals(7, loaded.points)
    }

    @Test
    fun corruptPayloadYieldsDefaultsInsteadOfThrowing() = runTest {
        val repository = ProgressRepository(
            FakePreferencesDataStore(preferencesOf(rawKey to "not valid json at all {{{")),
        )

        val loaded = repository.current()
        assertEquals(LearnerProgress(), loaded)
    }

    @Test
    fun setParentModePersists() = runTest {
        val repository = ProgressRepository(FakePreferencesDataStore())

        repository.setParentMode(ParentMode.Beginner)

        assertEquals(ParentMode.Beginner, repository.current().parentMode)
    }

    @Test
    fun saveSessionPersistsAndClears() = runTest {
        val repository = ProgressRepository(FakePreferencesDataStore())
        val snapshot = SessionSnapshot(lessonId = "lesson_1", packId = "pack_v1")

        repository.saveSession(snapshot)
        assertEquals(snapshot, repository.current().unfinishedSession)

        repository.saveSession(null)
        assertNull(repository.current().unfinishedSession)
    }

    @Test
    fun defaultProgressHasNoUnfinishedSession() = runTest {
        val repository = ProgressRepository(FakePreferencesDataStore())
        assertTrue(repository.current().atomStats.isEmpty())
        assertNull(repository.current().unfinishedSession)
    }
}
