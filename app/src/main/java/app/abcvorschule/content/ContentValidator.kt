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

    private const val MinSoundPositionRounds = 2

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
                }
                is CountAddSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.iconAtomId)
                    if (round.left < 0 || round.right < 0) {
                        issues += ValidationIssue("task $id has a negative operand")
                    }
                    if (round.operation == "add" && round.left + round.right != round.answer) {
                        issues += ValidationIssue(
                            "task $id answer ${round.answer} does not match ${round.left}+${round.right}",
                        )
                    }
                }
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
                    if (kinds != TrainerOrder) {
                        issues += ValidationIssue(
                            "authored lesson ${lesson.id} must hold $TrainerOrder but holds $kinds",
                        )
                    }
                    if (lesson.focusAtomIds.isEmpty()) {
                        issues += ValidationIssue("authored lesson ${lesson.id} needs focusAtomIds")
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
        return issues
    }

    fun requireValid(pack: ContentPack): ContentPack {
        val issues = validate(pack)
        if (issues.isNotEmpty()) throw ContentValidationException(issues)
        return pack
    }
}
