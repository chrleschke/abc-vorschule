package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ScaffoldLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaffoldMappingTest {
    @Test
    fun beginnerShowsSilhouetteAndNoExtraTiles() {
        val gaps = ScaffoldMapping.gaps(
            atomIds = listOf("haus"),
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
            atomIds = listOf("mama", "haus"),
            displays = mapOf("mama" to "Mama", "haus" to "Haus"),
            emojis = mapOf("mama" to "👩", "haus" to "🏠"),
            scaffolds = mapOf(
                "mama" to ScaffoldLevel.Beginner,
                "haus" to ScaffoldLevel.Advanced,
            ),
        )
        assertTrue(ScaffoldMapping.showsSilhouette(gaps[0].scaffold))
        assertFalse(ScaffoldMapping.showsSilhouette(gaps[1].scaffold))
        assertTrue(mixedScaffoldExample(gaps.associate { it.atomId to it.scaffold }))
    }
}
