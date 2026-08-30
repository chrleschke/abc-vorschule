package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCoverageTest {
    private val pack = ContentRepository.fromClasspath().load()

    /** Bis hierher zeigt der Trainer echte Stückzahlen (§8, `SymbolicFrom = 11`). */
    private val PictureableQuantity = 10

    @Test
    fun allThirtyFourLessonsAreAuthoredInPhaseOrder() {
        assertEquals(
            (1..34).map { "l%02d".format(it) },
            pack.authoredLessons.map { it.id },
        )
    }

    @Test
    fun noLessonsStayPlannedInTheExpandedPack() {
        assertTrue(pack.lessons.none { it.status == LessonStatus.planned })
    }

    @Test
    fun everyFocusGraphemeHasTraceStrokesAndATraceRound() {
        pack.authoredLessons.forEach { lesson ->
            val traced = pack.tasksOf(lesson).filterIsInstance<LetterTraceSpec>()
                .flatMap { it.rounds }.map { it.atomId }
            assertEquals(
                "lesson ${lesson.id} must trace exactly its focus graphemes",
                lesson.focusAtomIds,
                traced,
            )
            lesson.focusAtomIds.forEach {
                assertTrue("$it needs strokes", pack.atom(it).strokes.isNotEmpty())
            }
        }
    }

    @Test
    fun everyMerksatzNamesAWordThatReallyStartsWithItsGrapheme() {
        // „X wie Y" behauptet, dass Y mit X anfängt. „M wie Schneemann" tut das
        // nicht — ein Kind, das gerade M lernt, hört am Wortanfang ein Sch. Wo das
        // Graphem mitten im Wort sitzt (ß, ck, Ch, Ö, X können gar kein deutsches
        // Wort anfangen), heißt es „wie **in**": „Ü wie in Küken".
        val shape = Regex("""^(.+?) - wie (in )?(.+)\.$""")
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<LetterTraceSpec>().flatMap { it.rounds }
                .forEach { round ->
                    val match = shape.matchEntire(round.rewardTts)
                    assertTrue(
                        "lesson ${lesson.id}: '${round.rewardTts}' is not '<Graphem> - wie [in] <Wort>.'",
                        match != null,
                    )
                    val (spoken, inWord, word) = match!!.destructured
                    assertEquals(
                        "lesson ${lesson.id}: '${round.rewardTts}' names ${'$'}spoken but traces ${round.glyph}",
                        round.glyph,
                        spoken,
                    )
                    val initial = word.lowercase().startsWith(round.glyph.lowercase())
                    assertEquals(
                        "lesson ${lesson.id}: '${round.rewardTts}' — " +
                            if (initial) "'${'$'}word' starts with ${round.glyph}, so drop the 'in'"
                            else "'${'$'}word' does not start with ${round.glyph}, so it must read 'wie in'",
                        !initial,
                        inWord.isNotEmpty(),
                    )
                }
        }
    }

    @Test
    fun rechnenIsPresentInEveryAuthoredLesson() {
        // User decision: Rechnen runs in every lesson for variety.
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>()
            assertTrue("lesson ${lesson.id} needs count_add", math.isNotEmpty())
            assertTrue(
                "lesson ${lesson.id} needs at least two sums",
                math.sumOf { it.rounds.size } >= 2,
            )
        }
    }

    @Test
    fun rechnenIconsAreEarnedPerRound() {
        // Bis August 2026 galt „ein Icon pro Lektion". Das zwang eine Kuh-Lektion,
        // auch die Dreißiger-Aufgabe mit Kühen zu rechnen — dreißig Kühe kann sich
        // niemand vorstellen. Jetzt entscheidet die einzelne Runde: ein Bild nur,
        // wo die Szene trägt, sonst gar keins und nur Ziffern.
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<CountAddSpec>().flatMap { it.rounds }
                .forEach { round ->
                    val icon = round.iconAtomId ?: return@forEach
                    assertTrue(
                        "lesson ${lesson.id} counts $icon, which carries no emoji",
                        pack.atom(icon).emoji.isNotBlank(),
                    )
                }
        }
    }

    @Test
    fun aPictureIsOnlyPromisedWhereAChildCanPictureIt() {
        // §8: bis 10 zeigt der Trainer echte Stückzahlen, ab 11 nur noch ein
        // Symbol neben der Ziffer. Genau dort hört das Bild auf zu arbeiten und
        // fängt an, eine Szene zu behaupten ("dreißig Mülltonnen stehen am Weg").
        // Ein Bildwort darf deshalb nur an Runden hängen, deren Mengen ein Kind
        // sich wirklich hinstellen kann.
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<CountAddSpec>().flatMap { it.rounds }
                .forEach { round ->
                    if (round.iconAtomId == null) return@forEach
                    val largest = maxOf(round.left, round.right, round.answer)
                    assertTrue(
                        "lesson ${lesson.id}: ${round.promptTts} shows ${round.iconAtomId} " +
                            "for a quantity of $largest — drop the icon or shrink the numbers",
                        largest <= PictureableQuantity,
                    )
                }
        }
    }

    @Test
    fun wordBuilderNeverOffersAnUntaughtGrapheme() {
        // A block may only use graphemes/syllables introduced in this or an earlier
        // lesson — plus **words the child has already built**. Phase 8 rests on that
        // second half: "Fußball" is offered as `Fuß` + `ball`, and a compound is only
        // a legible task if both of its parts are old acquaintances. The target is
        // added *after* its own round is checked, so a lesson still has to build the
        // simple word before it may reuse it inside a compound.
        val introduced = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            introduced += lesson.focusAtomIds
            val merges = pack.tasksOf(lesson).filterIsInstance<SyllableMergeSpec>().flatMap { it.rounds }
            introduced += merges.map { it.resultAtomId }
            pack.tasksOf(lesson).filterIsInstance<WordBuildSpec>().forEach { build ->
                build.rounds.forEach { round ->
                    (round.blocks + round.distractors).forEach { block ->
                        assertTrue(
                            "lesson ${lesson.id} offers ${block.atomId} before it is taught",
                            block.atomId in introduced,
                        )
                    }
                    introduced += round.targetAtomId
                }
            }
        }
    }

    @Test
    fun sentenceRoundsOnlyUseWordsThatWereBuiltOrIntroduced() {
        val known = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<WordBuildSpec>().forEach { build ->
                known += build.rounds.map { it.targetAtomId }
            }
            pack.tasksOf(lesson).filterIsInstance<SentenceOrderSpec>().forEach { sentences ->
                sentences.rounds.forEach { round ->
                    pack.sentence(round.sentenceId).atomIds.forEach { atomId ->
                        // Lowercase function words (ist, am, da, das, ruft) are introduced by
                        // the sentence itself; anything else must be built or declared holistic.
                        val functionWord = pack.atom(atomId).display.first().isLowerCase()
                        assertTrue(
                            "lesson ${lesson.id} sentence uses unbuilt word $atomId",
                            atomId in known || functionWord || atomId in round.holisticAtomIds,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun firstWordBuildEncounterPerLessonStaysDistractorFree() {
        pack.authoredLessons.forEach { lesson ->
            val first = pack.tasksOf(lesson).filterIsInstance<WordBuildSpec>().firstOrNull() ?: return@forEach
            assertTrue(
                "lesson ${lesson.id} first word_build task must stay distractor-free",
                first.rounds.all { it.distractors.isEmpty() },
            )
        }
    }

    @Test
    fun noAtomSitsInThePackWithoutEverBeingShown() {
        // „Kein Atom ohne Auftritt" (PRODUCT_PRINCIPLES §4). Im August 2026 lagen
        // 60 Wort- und Bildatome im Pack, die kein Task, kein Satz und kein Finale
        // je referenziert hat — darunter genau die Alltagswörter, die vermeintlich
        // fehlten. Ein gepflegtes Atom, das kein Kind je sieht, ist kein Vorrat,
        // sondern eine Karteileiche: es kostet Kuratierung, TTS-Aufnahmen und
        // Review-Zeit und zahlt nichts zurück.
        val referenced = buildSet {
            pack.tasks.values.forEach { spec ->
                when (spec) {
                    is LetterTraceSpec -> spec.rounds.forEach { add(it.atomId) }
                    is SyllableMergeSpec -> spec.rounds.forEach {
                        add(it.leftAtomId); add(it.rightAtomId); add(it.resultAtomId)
                    }
                    is WordBuildSpec -> spec.rounds.forEach { round ->
                        add(round.targetAtomId)
                        (round.blocks + round.distractors).forEach { add(it.atomId) }
                    }
                    is SentenceOrderSpec -> spec.rounds.forEach { round ->
                        round.illustrationAtomId?.let { add(it) }
                        round.distractors.forEach { add(it.atomId) }
                        addAll(round.holisticAtomIds)
                        pack.sentences[round.sentenceId]?.let { addAll(it.atomIds) }
                    }
                    is SentencePictureSpec -> spec.rounds.forEach {
                        addAll(it.correctAtomIds); addAll(it.wrongAtomIds)
                    }
                    is CountAddSpec -> spec.rounds.forEach { r -> r.iconAtomId?.let { add(it) } }
                    else -> Unit
                }
            }
            pack.finales.values.forEach { addAll(it.pictureAtomIds) }
            pack.lessons.forEach { addAll(it.focusAtomIds) }
        }
        assertEquals(
            "atoms that no trainer, sentence or finale ever shows",
            emptySet<String>(),
            pack.atoms.keys - referenced,
        )
    }

    @Test
    fun satzVersteherRunsOnceInEveryLessonWithFourOrFiveRounds() {
        // Bis 2026-08 trugen nur die 18 Basis-Lektionen den Satz-Versteher; die
        // Wiederholungen l19–l26 waren ohne ihn zu dünn, um ein Kind zu halten.
        pack.authoredLessons.forEach { lesson ->
            val specs = pack.tasksOf(lesson).filterIsInstance<SentencePictureSpec>()
            assertEquals("lesson ${lesson.id}", 1, specs.size)
            // Vier Runden sind die Regel; siebzehn Lektionen tragen eine fünfte,
            // die je ein sonst totes Bildwort als *richtige* Karte zeigt
            // (Nashorn, Flamingo, Tiger, Nilpferd, Affe, Raupe, Pilz …).
            assertTrue(
                "lesson ${lesson.id} holds ${specs.single().rounds.size} rounds",
                specs.single().rounds.size in 4..5,
            )
            assertEquals(
                "lesson ${lesson.id}",
                "Ordne das richtige Bild zu.",
                specs.single().instructionTts,
            )
        }
    }
}
