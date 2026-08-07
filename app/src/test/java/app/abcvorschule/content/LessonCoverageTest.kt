package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCoverageTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun allTwentySixLessonsAreAuthoredInPhaseOrder() {
        assertEquals(
            (1..26).map { "l%02d".format(it) },
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
    fun rechnenIconsComeFromTheLessonsOwnVocabulary() {
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>()
            val icons = math.flatMap { it.rounds }.map { it.iconAtomId }.distinct()
            assertEquals("lesson ${lesson.id} should stay on one icon", 1, icons.size)
            assertTrue(pack.atom(icons.single()).emoji.isNotBlank())
        }
    }

    @Test
    fun wordBuilderNeverOffersAnUntaughtGrapheme() {
        // A block may only use graphemes/syllables introduced in this or an earlier lesson.
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
    fun satzVersteherRunsOnceInEveryBaseLessonWithFourRounds() {
        pack.authoredLessons.filter { it.index <= 18 }.forEach { lesson ->
            val specs = pack.tasksOf(lesson).filterIsInstance<SentencePictureSpec>()
            assertEquals("lesson ${lesson.id}", 1, specs.size)
            assertEquals("lesson ${lesson.id}", 4, specs.single().rounds.size)
            assertEquals(
                "lesson ${lesson.id}",
                "Ordne das richtige Bild zu.",
                specs.single().instructionTts,
            )
        }
    }
}
