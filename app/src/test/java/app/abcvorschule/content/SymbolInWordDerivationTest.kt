package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolInWordDerivationTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun rounds(lessonId: String) =
        SymbolInWordDerivation.buildRounds(pack, pack.lesson(lessonId))

    // --- the gate that protects the whole feature ----------------------------

    @Test
    fun everyDerivedRoundOfEveryAuthoredLessonIsSolvable() {
        // A round whose target does not occur as a segment cannot be completed.
        // This is the test that would catch removing the lesson scoping from
        // WordGraphemes (l07 "Nest" would fuse to N·e·st and lose its S).
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                assertTrue(
                    "lesson ${lesson.id}: ${round.promptTts} has no hit in ${round.segments}",
                    round.targetIndices.isNotEmpty(),
                )
                round.targetIndices.forEach { index ->
                    assertTrue(
                        "lesson ${lesson.id}: hit index $index out of bounds for ${round.segments}",
                        index in round.segments.indices,
                    )
                }
            }
        }
    }

    @Test
    fun everyDerivedRoundReferencesRealAtoms() {
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                assertTrue("${round.wordAtomId} missing", pack.atoms.containsKey(round.wordAtomId))
                assertTrue("${round.targetAtomId} missing", pack.atoms.containsKey(round.targetAtomId))
            }
        }
    }

    @Test
    fun derivationIsDeterministic() {
        assertEquals(rounds("l03"), rounds("l03"))
    }

    // --- mode alternation ----------------------------------------------------

    @Test
    fun modesAlternateStartingWithLetter() {
        val l03 = rounds("l03")
        assertEquals(
            listOf(SymbolInWordMode.letter, SymbolInWordMode.syllable, SymbolInWordMode.letter),
            l03.map { it.mode },
        )
        assertEquals(listOf("letter-p", "pa", "letter-t"), l03.map { it.targetAtomId })
    }

    @Test
    fun anOddRoundFallsBackToLetterModeWhenTheWordHasNoSyllableBlock() {
        // l05: "Hut" (H·u·t) and "Ufo" (U·f·o) are built from single letters only.
        val l05 = rounds("l05")
        assertEquals(listOf(SymbolInWordMode.letter, SymbolInWordMode.letter), l05.map { it.mode })
        assertEquals(listOf("letter-u", "letter-f"), l05.map { it.targetAtomId })
    }

    // --- focus rotation ------------------------------------------------------

    @Test
    fun focusRotationAdvancesInsteadOfRepeatingTheSameGrapheme() {
        // l01 traces M then A: "Mama" takes M, "ma" must take A, not M again.
        assertEquals(listOf("letter-m", "letter-a"), rounds("l01").map { it.targetAtomId })
    }

    @Test
    fun rotationSkipsAFocusGraphemeTheWordDoesNotContain() {
        // l06 traces R then N. "Tor" holds no N, so it takes R again.
        assertEquals(listOf("letter-r", "letter-r"), rounds("l06").map { it.targetAtomId })
    }

    // --- multiple hits -------------------------------------------------------

    @Test
    fun allOccurrencesAreHitsAcrossCase() {
        val papa = rounds("l03").first()
        assertEquals(listOf("P", "a", "p", "a"), papa.segments)
        assertEquals(listOf(0, 2), papa.targetIndices)
        assertEquals("Finde alle Buchstaben - P - im Wort - Papa.", papa.promptTts)
    }

    @Test
    fun aRepeatedSyllableYieldsTwoHits() {
        val mimi = rounds("l02").last()
        assertEquals(SymbolInWordMode.syllable, mimi.mode)
        assertEquals(listOf("Mi", "mi"), mimi.segments)
        assertEquals(listOf(0, 1), mimi.targetIndices)
        assertEquals("Finde alle Silben - mi - im Wort - Mimi.", mimi.promptTts)
    }

    @Test
    fun aSingleHitUsesTheSingularPrompt() {
        val oma = rounds("l02").first()
        assertEquals(listOf("O", "m", "a"), oma.segments)
        assertEquals(listOf(0), oma.targetIndices)
        assertEquals("Finde den Buchstaben - O - im Wort - Oma.", oma.promptTts)
    }

    @Test
    fun theSyllableModeUsesTheAuthoredBlocksNotAGraphemeSplit() {
        val opa = rounds("l03")[1]
        // Corrected from the brief's literal "Pa": the authored word_build block
        // for "Opa" (l03-t8 in tasks.json) displays the second block as lowercase
        // "pa", matching both atoms.json (atom "pa" is lowercase-only) and the
        // spec appendix ("O·pa").
        assertEquals(listOf("O", "pa"), opa.segments)
        assertEquals("Finde die Silbe - pa - im Wort - Opa.", opa.promptTts)
    }

    // --- guards --------------------------------------------------------------

    @Test
    fun aSingleGraphemeWordProducesNoRound() {
        // l22 builds "Ei", one segment — the word would be the answer.
        val l22 = rounds("l22")
        assertTrue("Ei must not become a round", l22.none { it.wordAtomId == "ei" })
        assertEquals(listOf("letter-au"), l22.map { it.targetAtomId })
    }

    @Test
    fun aWordBuiltTwiceProducesOneRound() {
        // l05 authors "Hut" in two word_build tasks.
        assertEquals(1, rounds("l05").count { it.wordAtomId == "hut" })
    }

    @Test
    fun aLessonWithoutWordBuildProducesNoRounds() {
        val lesson = pack.lesson("l01").copy(
            taskIds = pack.lesson("l01").taskIds.filter { pack.tasks[it] !is WordBuildSpec },
        )
        assertTrue(SymbolInWordDerivation.buildRounds(pack, lesson).isEmpty())
    }

    @Test
    fun aLessonWithoutLetterTraceProducesNoLetterRounds() {
        // No focus grapheme means no scoreable target, so the round falls away
        // rather than inventing an atom-less target.
        val lesson = pack.lesson("l05").copy(
            taskIds = pack.lesson("l05").taskIds.filter { pack.tasks[it] !is LetterTraceSpec },
        )
        assertTrue(SymbolInWordDerivation.buildRounds(pack, lesson).isEmpty())
    }

    // --- target label --------------------------------------------------------

    @Test
    fun aLetterTargetShowsBothCaseFormsAsAPair() {
        assertEquals(
            SymbolInWordDerivation.TargetLabel("P", "p"),
            SymbolInWordDerivation.targetLabel(pack.atom("letter-p"), SymbolInWordMode.letter),
        )
        assertEquals(
            SymbolInWordDerivation.TargetLabel("Sch", "sch"),
            SymbolInWordDerivation.targetLabel(pack.atom("letter-sch"), SymbolInWordMode.letter),
        )
    }

    @Test
    fun aLowercaseOnlyGraphemeShowsOneForm() {
        // "ck" is authored lowercase because no German word starts with it — a
        // form "Ck" does not exist and must not be taught.
        assertEquals(
            SymbolInWordDerivation.TargetLabel("ck", null),
            SymbolInWordDerivation.targetLabel(pack.atom("letter-ck"), SymbolInWordMode.letter),
        )
    }

    @Test
    fun aSyllableTargetShowsOnlyTheLowercaseAtomForm() {
        // An uppercase syllable only exists because it happens to start a word;
        // it is not a second learnable glyph.
        assertEquals(
            SymbolInWordDerivation.TargetLabel("mi", null),
            SymbolInWordDerivation.targetLabel(pack.atom("mi"), SymbolInWordMode.syllable),
        )
    }

    // --- appendix spot-checks --------------------------------------------------

    @Test
    fun lessonThirteenRepeatsItsOnlyFocusGraphemeAndThenTakesTheSyllable() {
        // L13 has a single focus grapheme, so the rotation has nothing to rotate to.
        // Not a bug — the lesson is literally called "Sch (Der Dreifachlaut)".
        assertEquals(
            listOf("letter-sch", "letter-sch", "letter-sch", "schu"),
            rounds("l13").map { it.targetAtomId },
        )
    }

    @Test
    fun lessonSixteenHuntsBothItsDigraphsIncludingTheOneSoundWordSegmentsCannotSplit() {
        assertEquals(listOf("letter-ck", "letter-pf", "letter-pf"), rounds("l16").map { it.targetAtomId })
        assertEquals(listOf("A", "pf", "e", "l"), rounds("l16")[1].segments)
    }
}
