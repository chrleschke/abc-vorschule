package app.abcvorschule.ui.exercise

import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBuildTrayTest {
    private val mama = WordBuildRound(
        promptTts = "Baue Mama.",
        targetAtomId = "mama",
        blocks = listOf(WordBlock("ma", "Ma"), WordBlock("ma", "ma")),
    )

    @Test
    fun freshTrayHoldsEverySolutionBlock() {
        assertEquals(listOf("Ma", "ma"), WordBuildTray.tiles(mama, emptyList()).map { it.display })
    }

    @Test
    fun placedBlocksLeaveTheTray() {
        assertEquals(listOf("ma"), WordBuildTray.tiles(mama, listOf("Ma")).map { it.display })
        assertTrue(WordBuildTray.tiles(mama, listOf("Ma", "ma")).isEmpty())
    }

    @Test
    fun repeatedBlockIsRemovedOnlyOncePerPlacement() {
        val mimi = mama.copy(
            targetAtomId = "mimi",
            blocks = listOf(WordBlock("mi", "Mi"), WordBlock("mi", "mi")),
        )
        assertEquals(listOf("mi"), WordBuildTray.tiles(mimi, listOf("Mi")).map { it.display })
    }

    @Test
    fun identicalDisplaysLeaveTheTrayOneAtATime() {
        // The Mama/Mimi fixtures differ in case ("Ma" vs "ma"), so they cannot tell a
        // remove-one implementation from a remove-every-match one. Two blocks spelling
        // the *same* display can: placing one must leave exactly one behind.
        val doubled = mama.copy(
            blocks = listOf(WordBlock("ba", "ba"), WordBlock("ba", "ba")),
        )
        assertEquals(listOf("ba"), WordBuildTray.tiles(doubled, listOf("ba")).map { it.display })
        assertTrue(WordBuildTray.tiles(doubled, listOf("ba", "ba")).isEmpty())
    }

    @Test
    fun distractorsAreAppendedAndTrayStaysSmall() {
        val withDistractors = mama.copy(
            distractors = listOf(
                WordBlock("mi", "Mi"),
                WordBlock("letter-o", "O"),
                WordBlock("letter-a", "A"),
            ),
        )
        val tiles = WordBuildTray.tiles(withDistractors, emptyList())
        assertTrue("tray must stay scannable", tiles.size <= WordBuildTray.MaxTrayTiles)
        assertEquals(listOf("Ma", "ma", "Mi", "O", "A"), tiles.map { it.display })
    }

    @Test
    fun frameKeysRoundTrip() {
        assertEquals(0, WordBuildTray.frameIndex(WordBuildTray.frameKey(0)))
        assertEquals(3, WordBuildTray.frameIndex(WordBuildTray.frameKey(3)))
        assertNull(WordBuildTray.frameIndex("wagon-start"))
    }
}
