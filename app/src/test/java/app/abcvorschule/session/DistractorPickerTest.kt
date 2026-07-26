package app.abcvorschule.session

import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.composePartsFor
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.SkillStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DistractorPickerTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun pick(taskId: String, progress: LearnerProgress): List<DistractorTile> {
        val template = pack.tasks.first { it.id == taskId }
        return pick(template, progress)
    }

    private fun pick(template: TaskTemplate, progress: LearnerProgress): List<DistractorTile> {
        val atom = template.atomId?.let { pack.atoms[it] }
        val parts = template.composePartsFor(atom)
        return DistractorPicker.pick(template, parts, pack, progress, Random(7))
    }

    private fun practiced(vararg atomIds: String) = LearnerProgress(
        atomStats = atomIds.associateWith { SkillStats(attempts = 1, correct = 1) },
    )

    @Test
    fun freshLearnerGetsNoDistractors() {
        assertEquals(emptyList<DistractorTile>(), pick("r-letter-m", LearnerProgress()))
    }

    @Test
    fun letterTaskDrawsOnlyKnownLettersWithoutGapCollision() {
        val progress = practiced("letter-a", "letter-o", "letter-s")
        val tiles = pick("r-letter-m", progress)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size <= DistractorPicker.MaxDistractors)
        tiles.forEach { tile ->
            assertEquals(AtomKind.letter, pack.atoms.getValue(tile.atomId).kind)
            assertTrue(tile.atomId in setOf("letter-a", "letter-o", "letter-s"))
            assertTrue(tile.display !in setOf("M", "m"))
        }
        assertEquals(tiles.size, tiles.map { it.display }.distinct().size)
    }

    @Test
    fun wordTaskDrawsOnlyKnownWords() {
        val progress = practiced("haus", "ist", "im", "letter-a")
        val tiles = pick("r-word-mama", progress)
        assertTrue(tiles.isNotEmpty())
        tiles.forEach { tile ->
            assertEquals(AtomKind.word, pack.atoms.getValue(tile.atomId).kind)
            assertTrue(tile.display != "Mama")
        }
    }

    @Test
    fun mathTaskNeverGetsDistractors() {
        val everything = practiced(*pack.atoms.keys.toTypedArray())
        assertEquals(emptyList<DistractorTile>(), pick("m-blumen-1-1", everything))
    }

    @Test
    fun trayBudgetCapsDistractorsForSpellTasks() {
        val everything = practiced(*pack.atoms.keys.toTypedArray())
        // sp-haus has four letter frames; tray max 5 leaves room for one distractor.
        val tiles = pick("sp-haus", everything)
        assertTrue(tiles.size <= 1)
    }

    @Test
    fun unpracticedAtomsAreNeverUsed() {
        val progress = practiced("letter-a")
        val tiles = pick("r-word-mama", progress)
        assertEquals(emptyList<DistractorTile>(), tiles)
    }
}
