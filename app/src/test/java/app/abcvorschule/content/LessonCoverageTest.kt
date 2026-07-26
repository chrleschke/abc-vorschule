package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCoverageTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun phaseOneAndTwoAreAuthored() {
        assertEquals(
            listOf("l01", "l02", "l03", "l04", "l05", "l06"),
            pack.authoredLessons.map { it.id },
        )
    }

    @Test
    fun lessonsSevenToSixteenStayPlanned() {
        val planned = pack.lessons.filter { it.status == LessonStatus.planned }
        assertEquals(10, planned.size)
        assertTrue(planned.all { it.taskIds.isEmpty() })
    }

    @Test
    fun everyFocusGraphemeHasTraceStrokesAndATraceRound() {
        pack.authoredLessons.forEach { lesson ->
            val traced = (pack.tasksOf(lesson).first { it.kind == TrainerKind.letter_trace }
                as LetterTraceSpec).rounds.map { it.atomId }
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
            assertEquals("lesson ${lesson.id}", 1, math.size)
            assertTrue("lesson ${lesson.id} needs at least two sums", math.single().rounds.size >= 2)
        }
    }

    @Test
    fun rechnenIconsComeFromTheLessonsOwnVocabulary() {
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>().single()
            val icons = math.rounds.map { it.iconAtomId }.distinct()
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
            val merges = (pack.tasksOf(lesson).first { it.kind == TrainerKind.syllable_merge }
                as SyllableMergeSpec).rounds
            introduced += merges.map { it.resultAtomId }
            val build = pack.tasksOf(lesson).first { it.kind == TrainerKind.word_build }
                as WordBuildSpec
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

    @Test
    fun sentenceRoundsOnlyUseWordsThatWereBuiltOrIntroduced() {
        val known = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            val build = pack.tasksOf(lesson).first { it.kind == TrainerKind.word_build }
                as WordBuildSpec
            known += build.rounds.map { it.targetAtomId }
            val sentences = pack.tasksOf(lesson).first { it.kind == TrainerKind.sentence_order }
                as SentenceOrderSpec
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

    @Test
    fun firstEncounterOfANewWordStaysDistractorFree() {
        val build = pack.tasksOf(pack.lesson("l01")).first { it.kind == TrainerKind.word_build }
            as WordBuildSpec
        assertTrue(build.rounds.all { it.distractors.isEmpty() })
    }
}
