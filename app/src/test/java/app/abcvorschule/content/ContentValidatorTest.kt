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
    fun sentencePictureRanksBetweenSentenceOrderAndCountAdd() {
        assertEquals(
            listOf(
                TrainerKind.sound_position,
                TrainerKind.letter_trace,
                TrainerKind.syllable_merge,
                TrainerKind.word_build,
                TrainerKind.sentence_order,
                TrainerKind.sentence_picture,
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

    /** Builds fake task ids for [kinds], each pairing a fresh id with an existing
     * [TaskSpec] instance of that kind from [lesson], and returns a mutated pack
     * whose [lesson] holds exactly those tasks in that order. */
    private fun packWithLessonKinds(lesson: Lesson, kinds: List<TrainerKind>): ContentPack {
        val fakeTaskIds = kinds.mapIndexed { i, kind ->
            val original = pack.tasksOf(lesson).first { it.kind == kind }
            "fake-$i" to original
        }
        return pack.copy(
            tasks = pack.tasks + fakeTaskIds,
            lessons = pack.lessons.map { l ->
                if (l.id == lesson.id) l.copy(taskIds = fakeTaskIds.map { (id, _) -> id }) else l
            },
        )
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
        val issues = ContentValidator.validate(packWithLessonKinds(lesson, repeatedAndSkipped)).map { it.message }
        assertTrue(issues.none { it.contains("must hold trainer kinds") })
    }

    @Test
    fun backwardJumpInTrainerKindsIsRejected() {
        // Correct start (sound_position) and correct end (count_add) so this test
        // isolates the monotonic-rank check: rank 3 (word_build) -> rank 1
        // (letter_trace) is a genuine mid-sequence backward jump, not a start/end
        // violation. If the monotonic check were dropped, this would slip through.
        val lesson = pack.authoredLessons.first()
        val dipsBackwardInTheMiddle = listOf(
            TrainerKind.sound_position,
            TrainerKind.word_build,
            TrainerKind.letter_trace,
            TrainerKind.count_add,
        )
        val issues = ContentValidator.validate(packWithLessonKinds(lesson, dipsBackwardInTheMiddle)).map { it.message }
        assertTrue(issues.any { it.contains("must hold trainer kinds") })
    }

    @Test
    fun derivedTrainerInAuthoredLessonIsAnIssueNotACrash() {
        // symbol_hunt/symbol_in_word have no TrainerRank — before the fix,
        // TrainerRank.getValue threw NoSuchElementException and validate() never
        // returned any issues for such a pack.
        val derived = SymbolInWordSpec(
            id = "derived-siw",
            rounds = listOf(
                SymbolInWordRound(
                    promptTts = "Finde den Buchstaben - M - im Wort - Mama.",
                    wordAtomId = "mama",
                    targetAtomId = "letter-m",
                    mode = SymbolInWordMode.letter,
                    segments = listOf("M", "a", "m", "a"),
                    targetIndices = listOf(0, 2),
                ),
            ),
        )
        val lesson = pack.authoredLessons.first()
        val issues = issuesOf { p ->
            p.copy(
                tasks = p.tasks + (derived.id to derived),
                lessons = p.lessons.map {
                    if (it.id == lesson.id) it.copy(taskIds = it.taskIds + derived.id) else it
                },
                // Break something unrelated too: the derived trainer must not stop
                // the validator from collecting all remaining issues.
                finales = p.finales + ("f-l01" to p.finale("f-l01").copy(tts = "")),
            )
        }
        assertTrue(
            issues.toString(),
            issues.any { it.contains("authored lesson ${lesson.id} must not hold derived trainer symbol_in_word") },
        )
        assertTrue(
            issues.toString(),
            issues.any { it.contains("derived-siw") && it.contains("must not appear in authored content") },
        )
        assertTrue(issues.toString(), issues.any { it.contains("f-l01") && it.contains("tts") })
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
    fun wordBuildLongInstructionPromptIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<WordBuildSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(
                    promptTts = "Baue das Wort Mama. Suche die passenden Buchstaben und " +
                        "setze sie in die richtige Reihenfolge.",
                )
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("without build instructions") })
    }

    @Test
    fun sentenceOrderOrdneSuffixIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<SentenceOrderSpec>().first {
            it.rounds.any { round -> "dem Bild zu" !in round.promptTts }
        }
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(
                    promptTts = "Hier sind Häuser. - Ordne die Wörter in die richtige Reihenfolge.",
                )
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("Ordne die Wörter") })
    }

    @Test
    fun sentenceOrderPictureMatchPromptStaysAllowed() {
        val spec = pack.tasks.values.filterIsInstance<SentenceOrderSpec>().first {
            it.rounds.any { round -> "dem Bild zu" in round.promptTts }
        }
        assertTrue(
            ContentValidator.validate(pack).none {
                it.message.contains(spec.id) && it.message.contains("Ordne die Wörter")
            },
        )
        assertTrue(spec.rounds.any { "Ordne das Wort" in it.promptTts && "dem Bild zu" in it.promptTts })
    }

    @Test
    fun countAddSumMustMatchAnswer() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val broken = spec.copy(rounds = spec.rounds.map { it.copy(answer = it.answer + 1) })
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("answer") })
    }

    @Test
    fun unsupportedCountOperationIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val broken = spec.copy(rounds = spec.rounds.map { it.copy(operation = "divide") })
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("unsupported operation") })
    }

    @Test
    fun subtractionAndMultiplicationAreValidatedWithTheirOwnArithmetic() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val subtraction = spec.copy(rounds = listOf(spec.rounds.first().copy(left = 8, right = 3, answer = 5, operation = "subtract")))
        val multiplication = spec.copy(rounds = listOf(spec.rounds.first().copy(left = 3, right = 4, answer = 12, operation = "multiply")))
        assertTrue(ContentValidator.validate(pack.copy(tasks = pack.tasks + (subtraction.id to subtraction))).none { it.message.contains("answer") })
        assertTrue(ContentValidator.validate(pack.copy(tasks = pack.tasks + (multiplication.id to multiplication))).none { it.message.contains("answer") })
    }

    @Test
    fun countAddQuantitiesAboveThirtyAreRejected() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val broken = spec.copy(
            rounds = listOf(spec.rounds.first().copy(left = 25, right = 8, answer = 33, operation = "add")),
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("quantity cap") })
    }

    @Test
    fun multiplicationOutsideTheMatrixGridIsRejected() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        // 3x9=27 stays under the quantity cap but no longer fits 5x6 — the matrix
        // visual is the reason for the limit, not the size of the product.
        val broken = spec.copy(
            rounds = listOf(spec.rounds.first().copy(left = 3, right = 9, answer = 27, operation = "multiply")),
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("matrix") })
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
    fun sentenceOrderDistractorDuplicatingASentenceWordIsRejected() {
        // Same rule word_build already has: a "wrong" tile that reads like a word
        // of the sentence is indistinguishable from a right one.
        val spec = pack.tasks.values.filterIsInstance<SentenceOrderSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                val word = pack.sentenceWords(pack.sentence(round.sentenceId)).first()
                round.copy(distractors = listOf(WordBlock("letter-m", word)))
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.toString(), issues.any { it.contains("duplicate sentence words") })
    }

    @Test
    fun traceRoundWithBlankRewardTtsIsRejected() {
        // One representative of the blank-field checks (missTts, rewardTts,
        // rewardEmoji, stretchTts, Atom.display all follow the promptTts pattern).
        val spec = pack.tasks.values.filterIsInstance<LetterTraceSpec>().first()
        val broken = spec.copy(rounds = spec.rounds.map { it.copy(rewardTts = " ") })
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.toString(), issues.any { it.contains("rewardTts") })
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

    @Test
    fun authoredLessonWithoutFinaleIsAnIssue() {
        val issues = issuesOf { p ->
            p.copy(lessons = p.lessons.map { if (it.id == "l01") it.copy(finaleId = null) else it })
        }
        assertTrue(issues.toString(), issues.any { it.contains("l01") && it.contains("finaleId") })
    }

    @Test
    fun unknownFinaleReferenceIsAnIssue() {
        val issues = issuesOf { p ->
            p.copy(lessons = p.lessons.map { if (it.id == "l01") it.copy(finaleId = "f-nope") else it })
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-nope") })
    }

    @Test
    fun finalePictureMustExistAsAnAtom() {
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("mama", "ghost"))))
        }
        assertTrue(issues.toString(), issues.any { it.contains("ghost") })
        // One bad reference must yield one issue, not also a redundant "no emoji"
        // complaint about the same missing atom.
        assertTrue(issues.toString(), issues.none { it.contains("ghost") && it.contains("emoji") })
    }

    @Test
    fun finalePictureMustCarryAnEmoji() {
        // `tisch` exists but carries emoji "" on purpose — there is no usable table emoji.
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("mama", "tisch"))))
        }
        assertTrue(issues.toString(), issues.any { it.contains("tisch") && it.contains("emoji") })
    }

    @Test
    fun finaleMustNotRepeatTheSameGlyph() {
        // `katze` and `mimi` are both 🐱 — two identical pictures read as a bug.
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("katze", "mimi"))))
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-l01") && it.contains("glyph") })
    }

    @Test
    fun finaleNeedsTwoToFourPictures() {
        val tooFew = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("mama"))))
        }
        assertTrue(tooFew.toString(), tooFew.any { it.contains("f-l01") && it.contains("pictures") })

        val tooMany = issuesOf { p ->
            p.copy(
                finales = p.finales + (
                    "f-l01" to p.finale("f-l01")
                        .copy(pictureAtomIds = listOf("mama", "maus", "apfel", "oma", "hut"))
                    ),
            )
        }
        assertTrue(tooMany.toString(), tooMany.any { it.contains("f-l01") && it.contains("pictures") })
    }

    @Test
    fun finaleTextNeedsFourToSevenWords() {
        val tooLong = issuesOf { p ->
            p.copy(
                finales = p.finales + (
                    "f-l01" to p.finale("f-l01")
                        .copy(text = "Mama Maus mampft einen ganz besonders dicken roten Apfel!")
                    ),
            )
        }
        assertTrue(tooLong.toString(), tooLong.any { it.contains("f-l01") && it.contains("words") })

        val tooShort = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(text = "Mama mampft!")))
        }
        assertTrue(tooShort.toString(), tooShort.any { it.contains("f-l01") && it.contains("words") })
    }

    @Test
    fun finaleTtsMustNotBeBlank() {
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(tts = "")))
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-l01") && it.contains("tts") })
    }

    @Test
    fun unreferencedFinaleIsAnIssue() {
        val issues = issuesOf { p ->
            p.copy(
                finales = p.finales + (
                    "f-orphan" to LessonFinale(
                        id = "f-orphan",
                        text = "Der Fuchs klaut den Keks!",
                        tts = "Der Fuchs klaut den Keks!",
                        pictureAtomIds = listOf("fuchs", "keks"),
                    )
                    ),
            )
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-orphan") && it.contains("not referenced") })
    }

    // --- sentence_picture -----------------------------------------------------

    private fun sentencePictureSpec(
        rounds: List<SentencePictureRound>,
        instruction: String = "Ordne das richtige Bild zu.",
    ) = SentencePictureSpec(id = "l01-spx", instructionTts = instruction, rounds = rounds)

    private fun validSentencePictureRound() = SentencePictureRound(
        promptTts = "Oma hat Mama gerufen.",
        correctAtomIds = listOf("oma", "mama"),
        wrongAtomIds = listOf("opa", "mama"),
    )

    /** Hängt den Spec vor das Rechnen der ersten autorierten Lektion. */
    private fun packWithSentencePicture(spec: SentencePictureSpec): ContentPack {
        val lesson = pack.authoredLessons.first()
        val countIndex = lesson.taskIds.indexOfFirst { pack.tasks.getValue(it) is CountAddSpec }
        val taskIds = lesson.taskIds.toMutableList().apply { add(countIndex, spec.id) }
        return pack.copy(
            tasks = pack.tasks + (spec.id to spec),
            lessons = pack.lessons.map { if (it.id == lesson.id) it.copy(taskIds = taskIds) else it },
        )
    }

    @Test
    fun validSentencePictureTaskPasses() {
        val rounds = List(4) { validSentencePictureRound() }
        val issues = ContentValidator.validate(packWithSentencePicture(sentencePictureSpec(rounds)))
        assertTrue(issues.joinToString { it.message }, issues.isEmpty())
    }

    @Test
    fun sentencePictureBlankInstructionIsRejected() {
        val rounds = List(4) { validSentencePictureRound() }
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(rounds, instruction = " ")),
        )
        assertTrue(issues.any { "instructionTts" in it.message })
    }

    @Test
    fun sentencePictureNeedsThreeToSixRounds() {
        val tooFew = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(2) { validSentencePictureRound() })),
        )
        assertTrue(tooFew.any { "rounds" in it.message })
        val tooMany = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(7) { validSentencePictureRound() })),
        )
        assertTrue(tooMany.any { "rounds" in it.message })
    }

    @Test
    fun sentencePictureSentenceNeedsFourToEightWords() {
        val short = validSentencePictureRound().copy(promptTts = "Oma ruft.")
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(4) { short })),
        )
        assertTrue(issues.any { "words" in it.message })
    }

    @Test
    fun sentencePictureInstructionInsideSentenceIsRejected() {
        val round = validSentencePictureRound().copy(promptTts = "Ordne das Bild der Oma zu.")
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(4) { round })),
        )
        assertTrue(issues.any { "Ordne" in it.message })
    }

    @Test
    fun sentencePictureCardsNeedOneToThreeExistingEmojiAtoms() {
        val emptyCard = validSentencePictureRound().copy(wrongAtomIds = emptyList())
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { emptyCard })),
            ).any { "card" in it.message },
        )
        val fourAtoms = validSentencePictureRound()
            .copy(correctAtomIds = listOf("oma", "oma", "oma", "oma"))
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { fourAtoms })),
            ).any { "card" in it.message },
        )
        val missingAtom = validSentencePictureRound().copy(correctAtomIds = listOf("gibtsnicht"))
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { missingAtom })),
            ).any { "missing atom" in it.message },
        )
        // "ist" existiert, trägt aber kein Emoji.
        val noEmoji = validSentencePictureRound().copy(correctAtomIds = listOf("ist"))
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { noEmoji })),
            ).any { "emoji" in it.message },
        )
    }

    @Test
    fun sentencePictureIdenticalCardsAreRejected() {
        val same = validSentencePictureRound().copy(
            correctAtomIds = listOf("oma", "mama"),
            wrongAtomIds = listOf("oma", "mama"),
        )
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(4) { same })),
        )
        assertTrue(issues.any { "indistinguishable" in it.message })
    }

    @Test
    fun sentencePictureTasksMustShareOneInstruction() {
        val rounds = List(4) { validSentencePictureRound() }
        val issues = ContentValidator.validate(
            packWithSentencePicture(
                sentencePictureSpec(rounds, instruction = "Welches Bild passt zum Satz?"),
            ),
        )
        assertTrue(issues.any { "share one instructionTts" in it.message })
    }

    @Test
    fun shippedSentencePictureTasksNeedOnlyOneInstructionRecording() {
        val instructions = ContentRepository.fromClasspath().load()
            .tasks.values.filterIsInstance<SentencePictureSpec>()
            .map { it.instructionTts }
            .distinct()
        assertEquals(listOf("Ordne das richtige Bild zu."), instructions)
    }
}
