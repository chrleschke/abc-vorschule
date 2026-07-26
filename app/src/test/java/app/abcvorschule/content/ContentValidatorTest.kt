package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentValidatorTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun issuesOf(mutate: (ContentPack) -> ContentPack): List<String> =
        ContentValidator.validate(mutate(pack)).map { it.message }

    @Test
    fun shippedPackIsValid() {
        assertEquals(emptyList<ValidationIssue>(), ContentValidator.validate(pack))
    }

    @Test
    fun trainerOrderIsTheSixDidacticTrainers() {
        assertEquals(
            listOf(
                TrainerKind.sound_position,
                TrainerKind.letter_trace,
                TrainerKind.syllable_merge,
                TrainerKind.word_build,
                TrainerKind.sentence_order,
                TrainerKind.count_add,
            ),
            ContentValidator.TrainerOrder,
        )
    }

    @Test
    fun everyAuthoredLessonHoldsTrainerKindsInNonDecreasingRank() {
        val rank = ContentValidator.TrainerOrder.withIndex().associate { (i, k) -> k to i }
        pack.authoredLessons.forEach { lesson ->
            val kinds = pack.tasksOf(lesson).map { it.kind }
            val ranks = kinds.map { rank.getValue(it) }
            assertTrue("lesson ${lesson.id} kinds $kinds must be non-decreasing rank", ranks.zipWithNext().all { (a, b) -> a <= b })
            assertEquals("lesson ${lesson.id} must start with sound_position", TrainerKind.sound_position, kinds.first())
            assertEquals("lesson ${lesson.id} must end with count_add", TrainerKind.count_add, kinds.last())
        }
    }

    @Test
    fun monotonicOrderAcceptsRepeatedAndSkippedKinds() {
        val lesson = pack.authoredLessons.first()
        val repeatedAndSkipped = listOf(
            TrainerKind.sound_position,
            TrainerKind.sound_position,
            TrainerKind.letter_trace,
            TrainerKind.word_build,
            TrainerKind.count_add,
        )
        val fakeTaskIds = repeatedAndSkipped.mapIndexed { i, kind ->
            val original = pack.tasksOf(lesson).first { it.kind == kind }
            "fake-$i" to original
        }
        val issues = issuesOf {
            it.copy(
                tasks = it.tasks + fakeTaskIds,
                lessons = it.lessons.map { l ->
                    if (l.id == lesson.id) l.copy(taskIds = fakeTaskIds.map { (id, _) -> id }) else l
                },
            )
        }
        assertTrue(issues.none { it.contains("must hold trainer kinds") })
    }

    @Test
    fun backwardJumpInTrainerKindsIsRejected() {
        val lesson = pack.authoredLessons.first()
        val backward = listOf(TrainerKind.word_build, TrainerKind.sound_position, TrainerKind.count_add)
        val fakeTaskIds = backward.mapIndexed { i, kind ->
            val original = pack.tasksOf(lesson).first { it.kind == kind }
            "fake-$i" to original
        }
        val issues = issuesOf {
            it.copy(
                tasks = it.tasks + fakeTaskIds,
                lessons = it.lessons.map { l ->
                    if (l.id == lesson.id) l.copy(taskIds = fakeTaskIds.map { (id, _) -> id }) else l
                },
            )
        }
        assertTrue(issues.any { it.contains("must hold trainer kinds") })
    }

    @Test
    fun plannedLessonWithTasksIsRejected() {
        // The shipped pack may have zero planned lessons at any given time (it does,
        // post-588cf1f) — mutate one authored lesson to planned-with-tasks instead of
        // relying on a real planned lesson existing.
        val target = pack.authoredLessons.first()
        val lessons = pack.lessons.map { lesson ->
            if (lesson.id == target.id) lesson.copy(status = LessonStatus.planned) else lesson
        }
        val issues = issuesOf { it.copy(lessons = lessons) }
        assertTrue(issues.any { it.contains("planned lesson") })
    }

    @Test
    fun danglingAtomReferenceIsRejected() {
        val broken = pack.tasks.values.filterIsInstance<WordBuildSpec>().first().let { spec ->
            spec.copy(
                rounds = spec.rounds.map { it.copy(targetAtomId = "does-not-exist") },
            )
        }
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("does-not-exist") })
    }

    @Test
    fun traceRoundWithoutStrokeDataIsRejected() {
        val strippedAtoms = pack.atoms.mapValues { (_, atom) -> atom.copy(strokes = emptyList()) }
        val issues = issuesOf { it.copy(atoms = strippedAtoms) }
        assertTrue(issues.any { it.contains("has no strokes") })
    }

    @Test
    fun wordBuildBlocksMustSpellTheTargetDisplay() {
        val spec = pack.tasks.values.filterIsInstance<WordBuildSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(blocks = round.blocks.reversed() + WordBlock("letter-m", "X"))
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("do not spell") })
    }

    @Test
    fun countAddSumMustMatchAnswer() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val broken = spec.copy(rounds = spec.rounds.map { it.copy(answer = it.answer + 1) })
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("answer") })
    }

    @Test
    fun countAddOperationOtherThanAddIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val broken = spec.copy(rounds = spec.rounds.map { it.copy(operation = "sub") })
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("unsupported operation") })
    }

    @Test
    fun wordBuildRoundWithTooManyDistractorsIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<WordBuildSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(
                    distractors = listOf(
                        WordBlock("letter-m", "M"),
                        WordBlock("letter-a", "A"),
                        WordBlock("baum", "Baum"),
                    ),
                )
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("distractors") && it.contains("max is 2") })
    }

    @Test
    fun wordBuildTrayOverflowIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<WordBuildSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(
                    blocks = round.blocks + WordBlock("baum", "Baum") + WordBlock("maus", "Maus"),
                    distractors = listOf(WordBlock("letter-m", "M"), WordBlock("letter-a", "A")),
                )
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("tray holds") && it.contains("max is 5") })
    }

    @Test
    fun sentenceOrderTrayOverflowIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<SentenceOrderSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(
                    distractors = listOf(
                        WordBlock("letter-m", "M"),
                        WordBlock("letter-a", "A"),
                        WordBlock("ma", "ma"),
                        WordBlock("ameise", "Ameise"),
                        WordBlock("maus", "Maus"),
                        WordBlock("baum", "Baum"),
                    ),
                )
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("tray holds") && it.contains("max is 6") })
    }

    @Test
    fun lessonIndicesAreContiguousFromOne() {
        assertEquals((1..pack.lessons.size).toList(), pack.lessons.map { it.index })
    }

    @Test
    fun glyphStrokePointsStayInsideUnitBox() {
        pack.atoms.values.filter { it.strokes.isNotEmpty() }.forEach { atom ->
            atom.strokes.forEach { stroke ->
                assertTrue("stroke of ${atom.id} needs >= 2 points", stroke.points.size >= 2)
                stroke.points.forEach { p ->
                    assertEquals("point of ${atom.id} needs x,y", 2, p.size)
                    assertTrue("${atom.id} x out of range", p[0] in 0.0..1.0)
                    assertTrue("${atom.id} y out of range", p[1] in 0.0..1.0)
                }
            }
        }
    }
}
