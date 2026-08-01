package app.abcvorschule.content

data class ValidationIssue(val message: String)

class ContentValidationException(val issues: List<ValidationIssue>) :
    IllegalArgumentException(issues.joinToString("\n") { it.message })

object ContentValidator {
    /** Fixed didactic sequence every authored lesson must follow. */
    val TrainerOrder: List<TrainerKind> = listOf(
        TrainerKind.sound_position,
        TrainerKind.letter_trace,
        TrainerKind.syllable_merge,
        TrainerKind.word_build,
        TrainerKind.sentence_order,
        TrainerKind.count_add,
    )

    /** Rank of each kind within [TrainerOrder] — the source of truth for "does this
     * sequence ever go backward", now that authored lessons may repeat or skip a
     * kind instead of holding exactly one of each. */
    private val TrainerRank: Map<TrainerKind, Int> =
        TrainerOrder.withIndex().associate { (index, kind) -> kind to index }

    private const val MinSoundPositionRounds = 2

    /** Authored-distractor budget: preschoolers must be able to scan the tray. */
    private const val MaxDistractorsPerRound = 2
    private const val MaxWordTrayTiles = 5
    private const val MaxSentenceTrayTiles = 6

    /** Redaktionsregeln für Finale-Sätze, siehe PRODUCT_PRINCIPLES.md Abschnitt 12. */
    private const val MinFinalePictures = 2
    private const val MaxFinalePictures = 4
    private const val MinFinaleWords = 4
    private const val MaxFinaleWords = 7

    fun validate(pack: ContentPack): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val atomIds = pack.atoms.keys

        if (pack.manifest.schemaVersion < 2) {
            issues += ValidationIssue("schemaVersion must be >= 2")
        }

        fun requireAtom(where: String, id: String) {
            if (id !in atomIds) issues += ValidationIssue("$where references missing atom $id")
        }

        pack.atoms.values.forEach { atom ->
            atom.strokes.forEachIndexed { i, stroke ->
                if (stroke.points.size < 2) {
                    issues += ValidationIssue("atom ${atom.id} stroke $i needs at least 2 points")
                }
                stroke.points.forEach { p ->
                    if (p.size != 2) {
                        issues += ValidationIssue("atom ${atom.id} stroke $i has a non-2D point")
                    } else if (p[0] !in 0.0..1.0 || p[1] !in 0.0..1.0) {
                        issues += ValidationIssue("atom ${atom.id} stroke $i leaves the unit box")
                    }
                }
            }
        }

        pack.sentences.values.forEach { sentence ->
            if (sentence.atomIds.isEmpty()) {
                issues += ValidationIssue("sentence ${sentence.id} has no atoms")
            }
            sentence.atomIds.forEach { requireAtom("sentence ${sentence.id}", it) }
            sentence.displayOverride?.let { override ->
                if (override.size != sentence.atomIds.size) {
                    issues += ValidationIssue(
                        "sentence ${sentence.id} displayOverride size must match atomIds",
                    )
                }
            }
        }

        pack.finales.values.forEach { finale ->
            val count = finale.pictureAtomIds.size
            if (count !in MinFinalePictures..MaxFinalePictures) {
                issues += ValidationIssue(
                    "finale ${finale.id} holds $count pictures; expected " +
                        "$MinFinalePictures..$MaxFinalePictures",
                )
            }
            val words = finale.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            if (words !in MinFinaleWords..MaxFinaleWords) {
                issues += ValidationIssue(
                    "finale ${finale.id} holds $words words; expected " +
                        "$MinFinaleWords..$MaxFinaleWords",
                )
            }
            if (finale.tts.isBlank()) {
                issues += ValidationIssue("finale ${finale.id} has no tts")
            }
            finale.pictureAtomIds.forEach { id ->
                requireAtom("finale ${finale.id}", id)
                // Only judge the emoji when the atom actually resolves — a missing
                // atom already produced its own issue via requireAtom above, and
                // pack.atoms[id]?.emoji.isNullOrBlank() would otherwise be true for
                // that same missing id too, doubling up on one bad reference.
                val atom = pack.atoms[id]
                if (atom != null && atom.emoji.isBlank()) {
                    issues += ValidationIssue("finale ${finale.id} picture $id carries no emoji")
                }
            }
            // Dedupe on the glyph, not the atom id: `katze` and `mimi` share one cat
            // emoji, and two identical pictures read as a bug — same rule as LessonEmojis.
            val glyphs = finale.pictureAtomIds.mapNotNull { pack.atoms[it]?.emoji }
                .filter { it.isNotBlank() }
            if (glyphs.size != glyphs.distinct().size) {
                issues += ValidationIssue("finale ${finale.id} shows the same glyph twice")
            }
        }

        pack.tasks.forEach { (id, spec) ->
            if (spec.id != id) {
                issues += ValidationIssue("task key $id does not match spec id ${spec.id}")
            }
            if (spec.roundCount == 0) {
                issues += ValidationIssue("task $id has no rounds")
            }
            spec.rounds.forEach { round ->
                if (round.promptTts.isBlank()) {
                    issues += ValidationIssue("task $id has a round without promptTts")
                }
            }
            when (spec) {
                is SoundPositionSpec -> {
                    if (spec.rounds.size < MinSoundPositionRounds) {
                        issues += ValidationIssue(
                            "task $id needs at least $MinSoundPositionRounds rounds to be failable",
                        )
                    }
                    spec.rounds.forEach { requireAtom("task $id", it.atomId) }
                }
                is LetterTraceSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.atomId)
                    val atom = pack.atoms[round.atomId]
                    if (atom != null && atom.strokes.isEmpty()) {
                        issues += ValidationIssue("task $id traces ${atom.id} which has no strokes")
                    }
                    if (round.glyph.isBlank()) {
                        issues += ValidationIssue("task $id has a trace round without a glyph")
                    }
                }
                is SyllableMergeSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.leftAtomId)
                    requireAtom("task $id", round.rightAtomId)
                    requireAtom("task $id", round.resultAtomId)
                    val spelled = round.leftDisplay + round.rightDisplay
                    val expected = pack.atoms[round.resultAtomId]?.display
                    if (expected != null && !spelled.equals(expected, ignoreCase = true)) {
                        issues += ValidationIssue(
                            "task $id merge parts '$spelled' do not spell result '$expected'",
                        )
                    }
                }
                is WordBuildSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.targetAtomId)
                    if (round.blocks.isEmpty()) {
                        issues += ValidationIssue("task $id has a word_build round without blocks")
                    }
                    // The Wort-Bauer says "Baue das Wort …" and the Wort-Detektiv
                    // derives "Finde … im Wort …" from the same target, so a target
                    // that is a syllable makes both trainers assert something false
                    // ("Baue das Wort ma"). Only `syllable` is rejected: `Kleid` is
                    // authored as `other` because it is also picture vocabulary, and
                    // that is a classification, not a lie about what it is.
                    val targetKind = pack.atoms[round.targetAtomId]?.kind
                    if (targetKind == AtomKind.syllable) {
                        issues += ValidationIssue(
                            "task $id builds ${round.targetAtomId} as a word, but the atom is a syllable",
                        )
                    }
                    (round.blocks + round.distractors).forEach { requireAtom("task $id", it.atomId) }
                    val spelled = round.blocks.joinToString("") { it.display }
                    val expected = pack.atoms[round.targetAtomId]?.display
                    if (expected != null && spelled != expected) {
                        issues += ValidationIssue(
                            "task $id blocks '$spelled' do not spell target '$expected'",
                        )
                    }
                    val duplicate = round.distractors.map { it.display }
                        .intersect(round.blocks.map { it.display }.toSet())
                    if (duplicate.isNotEmpty()) {
                        issues += ValidationIssue(
                            "task $id distractors duplicate solution blocks $duplicate",
                        )
                    }
                    if (round.distractors.size > MaxDistractorsPerRound) {
                        issues += ValidationIssue(
                            "task $id has ${round.distractors.size} distractors; max is $MaxDistractorsPerRound",
                        )
                    }
                    val tray = round.blocks.size + round.distractors.size
                    if (tray > MaxWordTrayTiles) {
                        issues += ValidationIssue(
                            "task $id tray holds $tray tiles; max is $MaxWordTrayTiles",
                        )
                    }
                }
                is SentenceOrderSpec -> spec.rounds.forEach { round ->
                    val sentence = pack.sentences[round.sentenceId]
                    if (sentence == null) {
                        issues += ValidationIssue("task $id references missing sentence ${round.sentenceId}")
                    }
                    round.illustrationAtomId?.let { requireAtom("task $id", it) }
                    round.distractors.forEach { requireAtom("task $id", it.atomId) }
                    round.holisticAtomIds.forEach { holistic ->
                        requireAtom("task $id", holistic)
                        if (sentence != null && holistic !in sentence.atomIds) {
                            issues += ValidationIssue(
                                "task $id marks $holistic holistic but the sentence does not use it",
                            )
                        }
                    }
                    if (round.distractors.size > MaxDistractorsPerRound) {
                        issues += ValidationIssue(
                            "task $id has ${round.distractors.size} distractors; max is $MaxDistractorsPerRound",
                        )
                    }
                    val tray = (sentence?.atomIds?.size ?: 0) + round.distractors.size
                    if (tray > MaxSentenceTrayTiles) {
                        issues += ValidationIssue(
                            "task $id tray holds $tray tiles; max is $MaxSentenceTrayTiles",
                        )
                    }
                }
                is CountAddSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.iconAtomId)
                    if (round.left < 0 || round.right < 0) {
                        issues += ValidationIssue("task $id has a negative operand")
                    }
                    val operation = app.abcvorschule.ui.exercise.MathOperation.fromWireName(round.operation)
                    if (operation == null) {
                        issues += ValidationIssue(
                            "task $id uses unsupported operation '${round.operation}'",
                        )
                    } else if (operation == app.abcvorschule.ui.exercise.MathOperation.Subtract &&
                        round.left < round.right
                    ) {
                        issues += ValidationIssue("task $id subtracts a larger amount than it starts with")
                    } else if (operation.answer(round.left, round.right) != round.answer) {
                        issues += ValidationIssue(
                            "task $id answer ${round.answer} does not match ${round.operation} operands",
                        )
                    }
                }
                is SymbolHuntSpec -> Unit // synthetic-only; never appears in authored content
                is SymbolInWordSpec -> Unit // synthetic-only; never appears in authored content
            }
        }

        val lessonIds = mutableSetOf<String>()
        pack.lessons.forEach { lesson ->
            if (!lessonIds.add(lesson.id)) {
                issues += ValidationIssue("duplicate lesson id ${lesson.id}")
            }
            lesson.focusAtomIds.forEach { requireAtom("lesson ${lesson.id}", it) }
            val missing = lesson.taskIds.filter { it !in pack.tasks }
            if (missing.isNotEmpty()) {
                issues += ValidationIssue("lesson ${lesson.id} references missing tasks $missing")
            }
            when (lesson.status) {
                LessonStatus.authored -> {
                    val kinds = lesson.taskIds.mapNotNull { pack.tasks[it]?.kind }
                    val ranks = kinds.map { TrainerRank.getValue(it) }
                    val monotonic = ranks.zipWithNext().all { (a, b) -> a <= b }
                    val startsAndEndsRight = kinds.firstOrNull() == TrainerKind.sound_position &&
                        kinds.lastOrNull() == TrainerKind.count_add
                    if (!monotonic || !startsAndEndsRight) {
                        issues += ValidationIssue(
                            "authored lesson ${lesson.id} must hold trainer kinds in " +
                                "non-decreasing $TrainerOrder rank, starting with sound_position " +
                                "and ending with count_add, but holds $kinds",
                        )
                    }
                    if (lesson.focusAtomIds.isEmpty()) {
                        issues += ValidationIssue("authored lesson ${lesson.id} needs focusAtomIds")
                    }
                    val finaleId = lesson.finaleId
                    if (finaleId.isNullOrBlank()) {
                        issues += ValidationIssue("authored lesson ${lesson.id} needs a finaleId")
                    } else if (finaleId !in pack.finales) {
                        issues += ValidationIssue(
                            "lesson ${lesson.id} references missing finale $finaleId",
                        )
                    }
                }
                LessonStatus.planned -> {
                    if (lesson.taskIds.isNotEmpty()) {
                        issues += ValidationIssue("planned lesson ${lesson.id} must not hold tasks")
                    }
                }
            }
        }
        if (pack.lessons.map { it.index } != (1..pack.lessons.size).toList()) {
            issues += ValidationIssue("lesson indices must be contiguous starting at 1")
        }
        if (pack.authoredLessons.isEmpty()) {
            issues += ValidationIssue("pack needs at least one authored lesson")
        }

        val referenced = pack.lessons.flatMap { it.taskIds }.toSet()
        (pack.tasks.keys - referenced).forEach {
            issues += ValidationIssue("task $it is not referenced by any lesson")
        }
        val referencedFinales = pack.lessons.mapNotNull { it.finaleId }.toSet()
        (pack.finales.keys - referencedFinales).forEach {
            issues += ValidationIssue("finale $it is not referenced by any lesson")
        }
        return issues
    }

    fun requireValid(pack: ContentPack): ContentPack {
        val issues = validate(pack)
        if (issues.isNotEmpty()) throw ContentValidationException(issues)
        return pack
    }
}
