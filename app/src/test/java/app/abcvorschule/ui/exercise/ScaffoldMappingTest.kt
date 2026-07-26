package app.abcvorschule.ui.exercise

import app.abcvorschule.content.ComposePart
import app.abcvorschule.progress.ScaffoldLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaffoldMappingTest {
    @Test
    fun beginnerShowsSilhouetteAndNoExtraTiles() {
        val gaps = ScaffoldMapping.gaps(
            parts = listOf(ComposePart("haus#0", "haus", null)),
            displays = mapOf("haus" to "Haus"),
            emojis = mapOf("haus" to "🏠"),
            scaffolds = mapOf("haus" to ScaffoldLevel.Beginner),
        )
        assertTrue(ScaffoldMapping.showsSilhouette(gaps.first().scaffold))
        assertEquals(listOf("Haus"), ScaffoldMapping.tileLabels(gaps))
    }

    @Test
    fun mixedScaffoldsPerAtom() {
        val gaps = ScaffoldMapping.gaps(
            parts = listOf(
                ComposePart("mama#0", "mama", null),
                ComposePart("haus#0", "haus", null),
            ),
            displays = mapOf("mama" to "Mama", "haus" to "Haus"),
            emojis = mapOf("mama" to "👩", "haus" to "🏠"),
            scaffolds = mapOf(
                "mama" to ScaffoldLevel.Beginner,
                "haus" to ScaffoldLevel.Advanced,
            ),
        )
        assertTrue(ScaffoldMapping.showsSilhouette(gaps[0].scaffold))
        assertFalse(ScaffoldMapping.showsSilhouette(gaps[1].scaffold))
        assertTrue(ScaffoldMapping.hasMixedScaffolds(gaps.associate { it.atomId to it.scaffold }))
    }

    @Test
    fun composePartsKeepDistinctSlotKeys() {
        val gaps = ScaffoldMapping.gaps(
            parts = listOf(
                ComposePart("ma#0", "ma", "Ma"),
                ComposePart("ma#1", "ma", "ma"),
            ),
            displays = mapOf("ma" to "ma"),
            emojis = mapOf("ma" to "🗣️"),
            scaffolds = mapOf("ma" to ScaffoldLevel.Beginner),
        )
        assertEquals(2, gaps.size)
        assertEquals("Ma", gaps[0].display)
        assertEquals("ma", gaps[1].display)
        assertEquals("ma#0", gaps[0].slotKey)
        assertEquals("ma#1", gaps[1].slotKey)
    }
}
