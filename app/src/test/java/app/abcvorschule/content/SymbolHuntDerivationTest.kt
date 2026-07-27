package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolHuntDerivationTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun tileCountsDegenerationTable() {
        assertNull(SymbolHuntDerivation.tileCounts(0))
        assertEquals(3 to 2, SymbolHuntDerivation.tileCounts(1))
        assertEquals(3 to 4, SymbolHuntDerivation.tileCounts(2))
        assertEquals(5 to 6, SymbolHuntDerivation.tileCounts(3))
        assertEquals(5 to 6, SymbolHuntDerivation.tileCounts(10))
    }

    @Test
    fun letterPoolForLessonOneHasExactlyTheOtherFocusLetter() {
        // Lesson 1 (M & A): hunting "letter-a" should offer "letter-m" as the only
        // known distractor letter, and vice versa.
        val poolForA = SymbolHuntDerivation.distractorPool(
            pack, currentLessonIndex = 1, mode = SymbolHuntMode.letter, targetAtomId = "letter-a",
        )
        assertEquals(listOf("letter-m"), poolForA)
    }

    @Test
    fun syllablePoolForLessonOneIsEmptyBecauseNoOtherSyllableExistsYet() {
        // "ma" is l01's only syllable_merge result — its own pool (excluding
        // itself) has nothing else to offer yet, so the round must be skipped.
        val pool = SymbolHuntDerivation.distractorPool(
            pack, currentLessonIndex = 1, mode = SymbolHuntMode.syllable, targetAtomId = "ma",
        )
        assertTrue(pool.isEmpty())
        assertNull(SymbolHuntDerivation.buildRound(pack, currentLessonIndex = 1, mode = SymbolHuntMode.syllable, targetAtomId = "ma"))
    }

    @Test
    fun syllablePoolGrowsByLessonTwo() {
        val secondLessonIndex = pack.lesson("l02").index
        val pool = SymbolHuntDerivation.distractorPool(
            pack, currentLessonIndex = secondLessonIndex, mode = SymbolHuntMode.syllable, targetAtomId = "ma",
        )
        assertTrue("l02 must introduce at least one new syllable", pool.isNotEmpty())
    }

    @Test
    fun nonSyllableKindTargetIsSkippedForSyllableMode() {
        // "ameise" is picture-only vocabulary (AtomKind.other), never a valid
        // syllable-hunt target even if something tried to pass it in.
        assertNull(
            SymbolHuntDerivation.buildRound(pack, currentLessonIndex = 1, mode = SymbolHuntMode.syllable, targetAtomId = "ameise"),
        )
    }

    @Test
    fun buildRoundProducesATemplatedPromptAndEmbedsThePool() {
        val round = SymbolHuntDerivation.buildRound(
            pack, currentLessonIndex = 1, mode = SymbolHuntMode.letter, targetAtomId = "letter-a",
        )
        assertEquals("Finde alle Buchstaben A!", round?.promptTts)
        assertEquals(listOf("letter-m"), round?.distractorPool)
    }

    @Test
    fun everyBuildableRoundAcrossTheWholePackHasANonEmptyPool() {
        // Gate: no round that insertSymbolHunts would keep ever has an empty pool
        // (buildRound already filters those out by returning null).
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<LetterTraceSpec>().flatMap { it.rounds }.forEach { round ->
                val hunt = SymbolHuntDerivation.buildRound(pack, lesson.index, SymbolHuntMode.letter, round.atomId)
                if (hunt != null) assertTrue(hunt.distractorPool.isNotEmpty())
            }
            pack.tasksOf(lesson).filterIsInstance<SyllableMergeSpec>().flatMap { it.rounds }.forEach { round ->
                val hunt = SymbolHuntDerivation.buildRound(pack, lesson.index, SymbolHuntMode.syllable, round.resultAtomId)
                if (hunt != null) {
                    assertTrue(hunt.distractorPool.isNotEmpty())
                    assertEquals(AtomKind.syllable, pack.atom(round.resultAtomId).kind)
                }
            }
        }
    }
}
