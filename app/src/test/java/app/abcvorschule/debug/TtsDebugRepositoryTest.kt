package app.abcvorschule.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same in-memory fake used by ProgressRepositoryTest, duplicated locally. */
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

class TtsDebugRepositoryTest {
    private val sampleEntries = listOf(
        TtsDebugEntry(
            id = "atom:letter-m:lemma",
            group = TtsDebugGroup.Atom,
            label = "M (letter)",
            originalText = "M",
            sourceFile = "atoms.json",
        ),
    )

    private fun newRepository(exportFile: File) =
        TtsDebugRepository(FakePreferencesDataStore(), exportFile)

    private fun tempExportFile(): File {
        val file = File.createTempFile("tts_debug_export", ".json")
        file.deleteOnExit()
        return file
    }

    @Test
    fun setOverridePersistsAndWritesExportFile() = runTest {
        val exportFile = tempExportFile()
        val repository = newRepository(exportFile)

        repository.setOverride("atom:letter-m:lemma", "Emm", sampleEntries)

        assertEquals(mapOf("atom:letter-m:lemma" to "Emm"), repository.current())
        val exported = exportFile.readText()
        assertTrue(exported.contains("\"editedText\":\"Emm\""))
        assertTrue(exported.contains("\"originalText\":\"M\""))
        assertTrue(exported.contains("\"sourceFile\":\"atoms.json\""))
    }

    @Test
    fun clearOverrideRemovesEntryAndUpdatesExportFile() = runTest {
        val exportFile = tempExportFile()
        val repository = newRepository(exportFile)
        repository.setOverride("atom:letter-m:lemma", "Emm", sampleEntries)

        repository.clearOverride("atom:letter-m:lemma", sampleEntries)

        assertTrue(repository.current().isEmpty())
        assertEquals("[]", exportFile.readText())
    }

    @Test
    fun clearAllEmptiesOverridesAndExportFile() = runTest {
        val exportFile = tempExportFile()
        val repository = newRepository(exportFile)
        repository.setOverride("atom:letter-m:lemma", "Emm", sampleEntries)

        repository.clearAll()

        assertTrue(repository.current().isEmpty())
        assertEquals("[]", exportFile.readText())
    }
}
