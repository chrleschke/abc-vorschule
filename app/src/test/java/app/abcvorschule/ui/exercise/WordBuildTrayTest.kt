package app.abcvorschule.ui.exercise

import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBuildTrayTest {
    private val mama = WordBuildRound(
        promptTts = "Baue Mama.",
        targetAtomId = "mama",
        blocks = listOf(WordBlock("ma", "Ma"), WordBlock("ma", "ma")),
    )
    private val seed = mama.targetAtomId.hashCode()

    @Test
    fun freshTrayHoldsEverySolutionBlock() {
        // Order is no longer the point once a shuffle is in play — only the multiset
        // of displays matters here.
        assertEquals(
            listOf("Ma", "ma").sorted(),
            WordBuildTray.tiles(mama, emptyList(), seed).map { it.display }.sorted(),
        )
    }

    @Test
    fun placedBlocksLeaveTheTray() {
        assertEquals(listOf("ma"), WordBuildTray.tiles(mama, listOf("Ma"), seed).map { it.display })
        assertTrue(WordBuildTray.tiles(mama, listOf("Ma", "ma"), seed).isEmpty())
    }

    @Test
    fun repeatedBlockIsRemovedOnlyOncePerPlacement() {
        val mimi = mama.copy(
            targetAtomId = "mimi",
            blocks = listOf(WordBlock("mi", "Mi"), WordBlock("mi", "mi")),
        )
        assertEquals(
            listOf("mi"),
            WordBuildTray.tiles(mimi, listOf("Mi"), mimi.targetAtomId.hashCode()).map { it.display },
        )
    }

    @Test
    fun identicalDisplaysLeaveTheTrayOneAtATime() {
        // The Mama/Mimi fixtures differ in case ("Ma" vs "ma"), so they cannot tell a
        // remove-one implementation from a remove-every-match one. Two blocks spelling
        // the *same* display can: placing one must leave exactly one behind.
        val doubled = mama.copy(
            blocks = listOf(WordBlock("ba", "ba"), WordBlock("ba", "ba")),
        )
        assertEquals(listOf("ba"), WordBuildTray.tiles(doubled, listOf("ba"), seed).map { it.display })
        assertTrue(WordBuildTray.tiles(doubled, listOf("ba", "ba"), seed).isEmpty())
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
        val tiles = WordBuildTray.tiles(withDistractors, emptyList(), seed)
        assertTrue("tray must stay scannable", tiles.size <= WordBuildTray.MaxTrayTiles)
        assertEquals(setOf("Ma", "ma", "Mi", "O", "A"), tiles.map { it.display }.toSet())
    }

    @Test
    fun trayCarriesExactlyTheRightMultisetSoTheRoundStaysSolvable() {
        val nest = mama.copy(
            targetAtomId = "nest",
            blocks = listOf(WordBlock("n", "N"), WordBlock("e", "e"), WordBlock("s", "s"), WordBlock("t", "t")),
        )
        val tiles = WordBuildTray.tiles(nest, listOf("N"), nest.targetAtomId.hashCode())
        assertEquals(listOf("e", "s", "t"), tiles.map { it.display }.sorted())
    }

    @Test
    fun offeredOrderIsNotSolutionOrderForAMultiBlockWord() {
        val nest = mama.copy(
            targetAtomId = "nest",
            blocks = listOf(WordBlock("n", "N"), WordBlock("e", "e"), WordBlock("s", "s"), WordBlock("t", "t")),
        )
        val tiles = WordBuildTray.tiles(nest, emptyList(), nest.targetAtomId.hashCode())
        assertNotEquals(nest.blocks.map { it.display }, tiles.map { it.display })
    }

    @Test
    fun sameSeedAndPlacementsProduceTheSameOrderEveryTime() {
        val first = WordBuildTray.tiles(mama, emptyList(), seed)
        val second = WordBuildTray.tiles(mama, emptyList(), seed)
        assertEquals(first.map { it.display }, second.map { it.display })
    }

    @Test
    fun frameKeysRoundTrip() {
        assertEquals(0, WordBuildTray.frameIndex(WordBuildTray.frameKey(0)))
        assertEquals(3, WordBuildTray.frameIndex(WordBuildTray.frameKey(3)))
        assertNull(WordBuildTray.frameIndex("wagon-start"))
    }

    @Test
    fun tileKeysStayUniqueForIdenticalBlocks() {
        // "Hallo" offers two blocks with the same atomId AND display ("letter-l"/"l").
        // If their keys collided, dragging one tile would drag both (shared drag
        // state), so the index must disambiguate them.
        val hallo = WordBlock("letter-l", "l")
        assertNotEquals(
            WordBuildTray.tileKey(1, hallo),
            WordBuildTray.tileKey(2, hallo),
        )
    }
}
