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
        assertEquals(SymbolHuntDerivation.PromptLetter, round?.promptTts)
        assertEquals(listOf("letter-m"), round?.distractorPool)
    }

    @Test
    fun multiLetterTargetUsesLautPrompt() {
        val round = SymbolHuntDerivation.buildRound(
            pack, currentLessonIndex = 10, mode = SymbolHuntMode.letter, targetAtomId = "letter-sch",
        )
        assertEquals(SymbolHuntDerivation.PromptDigraph, round?.promptTts)
    }

    @Test
    fun syllableTargetUsesSilbenPrompt() {
        val round = SymbolHuntDerivation.buildRound(
            pack, currentLessonIndex = 2, mode = SymbolHuntMode.syllable, targetAtomId = "ma",
        )
        assertEquals(SymbolHuntDerivation.PromptSyllable, round?.promptTts)
    }

    @Test
    fun promptKindNamingMatchesPedagogy() {
        val letterA = pack.atom("letter-a")
        val sch = pack.atom("letter-sch")
        val ma = pack.atom("ma")
        assertEquals(SymbolHuntPromptKind.Buchstabe, SymbolHuntDerivation.promptKind(SymbolHuntMode.letter, letterA))
        assertEquals(SymbolHuntPromptKind.Laut, SymbolHuntDerivation.promptKind(SymbolHuntMode.letter, sch))
        assertEquals(SymbolHuntPromptKind.Silbe, SymbolHuntDerivation.promptKind(SymbolHuntMode.syllable, ma))
    }

    @Test
    fun everyBuildableRoundAcrossTheWholePackHasANonEmptyPool() {
        // Gate: no round that insertSymbolHunts would keep ever has an empty pool
        // (buildRound already filters those out by returning null).
        //
        // The `if (hunt != null)` checks only assert *inside* a conditional — if a
        // regression made buildRound return null for every single round in the
        // whole pack, this test would still report green with nothing actually
        // checked. The counts below, computed directly against the real pack:
        // - Letter traces: l01-l18 (36 single + 3 for l18) + l19-l26 review (16)
        //   + l27-l34 Phase 8 (16) = 71 total, all build hunts.
        // - Syllable merges: 56 rounds across all lessons (some lessons have 2).
        //   Only 38 build hunts: l01's "ma" is degenerate (its pool, excluding
        //   itself, is empty), and the 16 merges of Phase 8 join two whole *words*
        //   into a compound — their result is no syllable atom, so there is nothing
        //   to hunt for.
        var letterRoundsChecked = 0
        var letterHuntsBuilt = 0
        var syllableRoundsChecked = 0
        var syllableHuntsBuilt = 0
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<LetterTraceSpec>().flatMap { it.rounds }.forEach { round ->
                letterRoundsChecked++
                val hunt = SymbolHuntDerivation.buildRound(pack, lesson.index, SymbolHuntMode.letter, round.atomId)
                if (hunt != null) {
                    letterHuntsBuilt++
                    assertTrue(hunt.distractorPool.isNotEmpty())
                }
            }
            pack.tasksOf(lesson).filterIsInstance<SyllableMergeSpec>().flatMap { it.rounds }.forEach { round ->
                syllableRoundsChecked++
                val hunt = SymbolHuntDerivation.buildRound(pack, lesson.index, SymbolHuntMode.syllable, round.resultAtomId)
                if (hunt != null) {
                    syllableHuntsBuilt++
                    assertTrue(hunt.distractorPool.isNotEmpty())
                    assertEquals(AtomKind.syllable, pack.atom(round.resultAtomId).kind)
                }
            }
        }
        assertEquals(71, letterRoundsChecked)
        assertEquals(71, letterHuntsBuilt)
        assertEquals(56, syllableRoundsChecked)
        assertEquals(38, syllableHuntsBuilt)
    }
}
