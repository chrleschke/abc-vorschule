package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        // l01 traces M then A: "Mama" takes M, "am" must take A, not M again.
        assertEquals(listOf("letter-m", "letter-a"), rounds("l01").map { it.targetAtomId })
    }

    @Test
    fun noRoundHuntsInsideSomethingThatIsNotAWord() {
        // "Finde den Buchstaben A im Wort ma" was the authored defect: l01-t7 built
        // the syllable `ma` with the Wort-Bauer, and the derivation faithfully called
        // it a word. The lesson now builds `am`, which is one. ContentValidator holds
        // the authoring side of this; this test holds the derived prompts.
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                assertNotEquals(
                    "lesson ${lesson.id}: ${round.promptTts} calls a syllable a word",
                    AtomKind.syllable,
                    pack.atom(round.wordAtomId).kind,
                )
            }
        }
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
    fun aRepeatedGraphemeYieldsTwoHits() {
        // "Mimi" would give `Mi·mi` with the target `mi` in syllable mode — every
        // segment a hit, so unfailable and rejected (see the all-hits guard tests
        // below). The letter round it falls back to has two hits in four segments.
        val mimi = rounds("l02").last()
        assertEquals(SymbolInWordMode.letter, mimi.mode)
        assertEquals("letter-i", mimi.targetAtomId)
        assertEquals(listOf("M", "i", "m", "i"), mimi.segments)
        assertEquals(listOf(1, 3), mimi.targetIndices)
        assertEquals("Finde alle Buchstaben - I - im Wort - Mimi.", mimi.promptTts)
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
    fun noDerivedRoundOfAnyAuthoredLessonCanBeSolvedWithoutRisk() {
        // Design doc §4 / PRODUCT_PRINCIPLES "Kann die Aufgabe überhaupt
        // fehlschlagen?": if every segment is a hit there is nothing to tap wrong,
        // so no Miss can be reported for adaptivity and "Zeig mir" is unreachable.
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                assertTrue(
                    "lesson ${lesson.id}: ${round.promptTts} has a hit on every one of ${round.segments}",
                    round.targetIndices.size < round.segments.size,
                )
            }
        }
    }

    @Test
    fun anAllHitsSyllableRoundFallsBackToLetterMode() {
        // l02's second word is "Mimi": the syllable candidate `Mi·mi` / `mi` is two
        // hits in two segments and is dropped, exactly like the block-display
        // mismatch in l17. The letter round that replaces it hunts the lesson's own
        // focus grapheme I and can be failed.
        val l02 = rounds("l02")
        assertEquals(listOf(SymbolInWordMode.letter, SymbolInWordMode.letter), l02.map { it.mode })
        assertEquals(listOf("letter-o", "letter-i"), l02.map { it.targetAtomId })
        // The repeat lesson derives from the same words and must agree.
        assertEquals(l02.map { it.targetAtomId }, rounds("l20").map { it.targetAtomId })
    }

    @Test
    fun aWordWhoseEveryGraphemeIsTheFocusLetterProducesNoRound() {
        // Synthetic, because the authored content has no such word: "Mm" splits into
        // M·m and both segments are hits for the focus grapheme M. With the letter
        // round unfailable too, the word yields nothing at all (design doc §4).
        val word = Atom(id = "synthetic-mm", lemma = "Mm", display = "Mm", emoji = "🤫")
        val wordBuild = WordBuildSpec(
            id = "synthetic-word-build",
            rounds = listOf(
                WordBuildRound(
                    promptTts = "Baue Mm.",
                    targetAtomId = word.id,
                    blocks = listOf(
                        WordBlock(atomId = "letter-m", display = "M"),
                        WordBlock(atomId = "letter-m", display = "m"),
                    ),
                ),
            ),
        )
        val synthetic = pack.copy(
            atoms = pack.atoms + (word.id to word),
            tasks = pack.tasks + (wordBuild.id to wordBuild),
        )
        val base = pack.lesson("l01")
        val lesson = base.copy(
            taskIds = base.taskIds.filter { pack.tasks[it] is LetterTraceSpec } + wordBuild.id,
        )
        assertTrue(SymbolInWordDerivation.buildRounds(synthetic, lesson).isEmpty())
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

    @Test
    fun lessonSeventeenFallsBackToLetterModeWhenTheSyllableBlockDisagreesWithItsAtom() {
        // l17-t8 ("Spinne") groups "Spin" under the `spi` syllable atom, whose own
        // display is "spi" — a mismatch beyond casing. A round cannot honestly
        // label a "spi" target above a "Spin" segment, so the syllable round is
        // not asked at all and the word falls back to letter mode, hunting "Sp".
        assertEquals(
            listOf(SymbolInWordMode.letter, SymbolInWordMode.letter),
            rounds("l17").map { it.mode },
        )
        assertEquals(listOf("letter-st", "letter-sp"), rounds("l17").map { it.targetAtomId })
        assertEquals(listOf("Sp", "i", "n", "n", "e"), rounds("l17")[1].segments)
    }

    // --- displayed target must be recognisable among the segments -------------

    @Test
    fun theDisplayedTargetAlwaysOccursLiterallyAmongTheSegments() {
        // Stronger than "targetIndices is not empty": a hit the child cannot
        // recognise as the thing it was asked to find is not a hit. This is what
        // rules out L17's "spi" label sitting above a "Spin" segment.
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                val label = SymbolInWordDerivation.targetLabel(
                    pack.atom(round.targetAtomId),
                    round.mode,
                ).primary
                assertTrue(
                    "lesson ${lesson.id}: label '$label' is not a segment of ${round.segments}",
                    round.segments.any { it.equals(label, ignoreCase = true) },
                )
                round.targetIndices.forEach { index ->
                    assertTrue(
                        "lesson ${lesson.id}: hit '${round.segments[index]}' is not the label '$label'",
                        round.segments[index].equals(label, ignoreCase = true),
                    )
                }
            }
        }
    }
}
