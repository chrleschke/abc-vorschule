package app.abcvorschule.content

data class ValidationIssue(val message: String)

class ContentValidationException(val issues: List<ValidationIssue>) :
    IllegalArgumentException(issues.joinToString("\n") { it.message })

object ContentValidator {
    /** Fixed didactic sequence every authored lesson must follow. */
    val TrainerOrder: List<TrainerKind> = listOf(
        TrainerKind.letter_trace,
        TrainerKind.syllable_merge,
        TrainerKind.word_build,
        TrainerKind.sentence_order,
        TrainerKind.sentence_picture,
        TrainerKind.count_add,
    )

    /** Rank of each kind within [TrainerOrder] — the source of truth for "does this
     * sequence ever go backward", now that authored lessons may repeat or skip a
     * kind instead of holding exactly one of each. */
    private val TrainerRank: Map<TrainerKind, Int> =
        TrainerOrder.withIndex().associate { (index, kind) -> kind to index }

    /** Rechnen-Zahlenraum: operands and answers stay countable up to 30. */
    private const val MaxMathQuantity = 30

    /** Authored-distractor budget: preschoolers must be able to scan the tray. */
    private const val MaxDistractorsPerRound = 2
    private const val MaxWordTrayTiles = 5
    private const val MaxSentenceTrayTiles = 6

    /** Redaktionsregeln für Finale-Sätze, siehe PRODUCT_PRINCIPLES.md Abschnitt 12. */
    private const val MinFinalePictures = 2
    private const val MaxFinalePictures = 4
    private const val MinFinaleWords = 4
    private const val MaxFinaleWords = 7

    /** Redaktionsregeln Satz-Versteher, siehe Design-Spec 2026-08-07. */
    private const val MinSentencePictureRounds = 3
    private const val MaxSentencePictureRounds = 6
    private const val MinSentencePictureWords = 4
    private const val MaxSentencePictureWords = 8
    private const val MaxSentencePictureCardAtoms = 3

    /**
     * Erreichbare Atome, die bewusst ohne Artikel gesprochen werden: Interjektion,
     * Pronomen, Adjektiv, Präposition. Alles andere Erreichbare muss eine
     * [NounClass] tragen, sonst fiele es still auf die artikellose Form zurück.
     */
    val ArticleFreeSpeechAtomIds: Set<String> = setOf("hallo", "ich", "rot", "am")

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
            if (atom.display.isBlank()) {
                issues += ValidationIssue("atom ${atom.id} has a blank display")
            }
            if (atom.nounClass != null && atom.nounClass != NounClass.properName && atom.gender == null) {
                issues += ValidationIssue(
                    "atom ${atom.id} has nounClass ${atom.nounClass} but no gender",
                )
            }
            if (atom.gender != null && atom.nounClass == null) {
                issues += ValidationIssue("atom ${atom.id} has a gender but no nounClass")
            }
            if (atom.nounClass == NounClass.properName && atom.gender != null) {
                issues += ValidationIssue(
                    "atom ${atom.id} is a name and must not carry a gender",
                )
            }
            if (!atom.articleSpeechOverride.isNullOrBlank() && atom.nounClass == null) {
                issues += ValidationIssue(
                    "atom ${atom.id} has an articleSpeechOverride but no nounClass",
                )
            }
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

        // Plural-Atome ("Häuser") nehmen im Deutschen "die", unabhängig vom Genus des
        // Singulars; die Ableitung kann das nicht wissen und braucht einen Override.
        // Selbst-Plurale ("Eimer" → "Eimer") sind keine eigenen Plural-Atome.
        val pluralDisplays: Set<String> = pack.atoms.values
            .mapNotNull { singular ->
                singular.pluralDisplay?.takeIf { it != singular.display }
            }
            .toSet()
        pack.atoms.values.forEach { atom ->
            if (atom.display in pluralDisplays &&
                atom.nounClass != null &&
                atom.articleSpeechOverride.isNullOrBlank()
            ) {
                issues += ValidationIssue(
                    "atom ${atom.id} is a plural form and needs an articleSpeechOverride",
                )
            }
        }

        // Reichweite des Erfolgs-Vorsprechens: nur diese Atome werden je mit Artikel
        // gesprochen (Wort-Detektiv leitet sich aus word_build ab, ist also enthalten).
        val speechReachable: Set<String> = pack.tasks.values.flatMap { spec ->
            when (spec) {
                is WordBuildSpec -> spec.rounds.map { it.targetAtomId }
                else -> emptyList()
            }
        }.toSet()
        speechReachable.forEach { id ->
            if (id in ArticleFreeSpeechAtomIds) return@forEach
            val atom = pack.atoms[id] ?: return@forEach
            if (atom.nounClass == null) {
                issues += ValidationIssue(
                    "atom $id is spoken as a success answer but has no nounClass " +
                        "(add gender + nounClass, or list it in ArticleFreeSpeechAtomIds)",
                )
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
                is LetterTraceSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.atomId)
                    val atom = pack.atoms[round.atomId]
                    if (atom != null && atom.strokes.isEmpty()) {
                        issues += ValidationIssue("task $id traces ${atom.id} which has no strokes")
                    }
                    if (round.glyph.isBlank()) {
                        issues += ValidationIssue("task $id has a trace round without a glyph")
                    }
                    if (round.rewardTts.isBlank()) {
                        issues += ValidationIssue("task $id has a trace round without rewardTts")
                    }
                    if (round.rewardEmoji.isBlank()) {
                        issues += ValidationIssue("task $id has a trace round without a rewardEmoji")
                    }
                }
                is SyllableMergeSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.leftAtomId)
                    requireAtom("task $id", round.rightAtomId)
                    requireAtom("task $id", round.resultAtomId)
                    if (round.stretchTts.isBlank()) {
                        issues += ValidationIssue("task $id has a merge round without stretchTts")
                    }
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
                    if (!isShortWordBuildPrompt(round.promptTts)) {
                        issues += ValidationIssue(
                            "task $id word_build promptTts must be 'Baue das Wort ….' without build instructions",
                        )
                    }
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
                    if ("Ordne die Wörter" in round.promptTts) {
                        issues += ValidationIssue(
                            "task $id sentence_order promptTts must not include 'Ordne die Wörter' instructions",
                        )
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
                    // Same rule as word_build: a distractor that reads like a word of
                    // the sentence makes the "wrong" tile indistinguishable from a
                    // right one.
                    if (sentence != null) {
                        val duplicate = round.distractors.map { it.display }
                            .intersect(pack.sentenceWords(sentence).toSet())
                        if (duplicate.isNotEmpty()) {
                            issues += ValidationIssue(
                                "task $id distractors duplicate sentence words $duplicate",
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
                is SentencePictureSpec -> {
                    if (spec.instructionTts.isBlank()) {
                        issues += ValidationIssue("task $id needs an instructionTts")
                    }
                    if (spec.rounds.size !in MinSentencePictureRounds..MaxSentencePictureRounds) {
                        issues += ValidationIssue(
                            "task $id holds ${spec.rounds.size} rounds; expected " +
                                "$MinSentencePictureRounds..$MaxSentencePictureRounds",
                        )
                    }
                    spec.rounds.forEach { round ->
                        val words = round.promptTts.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                        if (words !in MinSentencePictureWords..MaxSentencePictureWords) {
                            issues += ValidationIssue(
                                "task $id sentence holds $words words; expected " +
                                    "$MinSentencePictureWords..$MaxSentencePictureWords",
                            )
                        }
                        if ("Ordne" in round.promptTts) {
                            issues += ValidationIssue(
                                "task $id sentence must not repeat the 'Ordne' instruction",
                            )
                        }
                        listOf("correct" to round.correctAtomIds, "wrong" to round.wrongAtomIds)
                            .forEach { (label, ids) ->
                                if (ids.size !in 1..MaxSentencePictureCardAtoms) {
                                    issues += ValidationIssue(
                                        "task $id $label card holds ${ids.size} atoms; " +
                                            "expected 1..$MaxSentencePictureCardAtoms",
                                    )
                                }
                                ids.forEach { atomId ->
                                    requireAtom("task $id", atomId)
                                    val atom = pack.atoms[atomId]
                                    if (atom != null && atom.emoji.isBlank()) {
                                        issues += ValidationIssue(
                                            "task $id $label card atom $atomId carries no emoji",
                                        )
                                    }
                                }
                            }
                        // Beide Karten müssen unterscheidbar sein, sonst kann die
                        // Aufgabe nicht fehlschlagen (Prüffrage der Prinzipien).
                        fun glyphs(ids: List<String>) =
                            ids.joinToString("") { pack.atoms[it]?.emoji.orEmpty() }
                        if (glyphs(round.correctAtomIds) == glyphs(round.wrongAtomIds)) {
                            issues += ValidationIssue("task $id cards are indistinguishable")
                        }
                    }
                }
                is CountAddSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.iconAtomId)
                    if (round.left < 0 || round.right < 0) {
                        issues += ValidationIssue("task $id has a negative operand")
                    }
                    if (round.left > MaxMathQuantity || round.right > MaxMathQuantity ||
                        round.answer > MaxMathQuantity
                    ) {
                        issues += ValidationIssue(
                            "task $id exceeds the curriculum quantity cap of $MaxMathQuantity",
                        )
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
                    } else if (operation == app.abcvorschule.ui.exercise.MathOperation.Multiply &&
                        (
                            round.left > app.abcvorschule.ui.exercise.MultiplicationMatrix.MaxRows ||
                                round.right > app.abcvorschule.ui.exercise.MultiplicationMatrix.MaxColumns
                            )
                    ) {
                        issues += ValidationIssue(
                            "task $id multiplication grid ${round.left}x${round.right} exceeds the " +
                                "${app.abcvorschule.ui.exercise.MultiplicationMatrix.MaxRows}x" +
                                "${app.abcvorschule.ui.exercise.MultiplicationMatrix.MaxColumns} matrix",
                        )
                    }
                }
                // Derived trainers are synthesized at runtime (SymbolHuntInsertion,
                // SymbolInWordInsertion) and must never be authored — a spec of
                // either kind inside the pack is itself the defect.
                is SymbolHuntSpec ->
                    issues += ValidationIssue(
                        "task $id is a derived trainer (${spec.kind}) and must not appear in authored content",
                    )
                is SymbolInWordSpec ->
                    issues += ValidationIssue(
                        "task $id is a derived trainer (${spec.kind}) and must not appear in authored content",
                    )
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
                    // Derived trainers (Jagd, Wort-Detektiv) exist only at runtime and
                    // have no rank in TrainerOrder — report them as an issue instead of
                    // crashing on getValue, and judge the sequence over the rest so all
                    // remaining issues still get collected.
                    val (authoredKinds, derivedKinds) = kinds.partition { it in TrainerRank }
                    derivedKinds.distinct().forEach { kind ->
                        issues += ValidationIssue(
                            "authored lesson ${lesson.id} must not hold derived trainer $kind",
                        )
                    }
                    val ranks = authoredKinds.map { TrainerRank.getValue(it) }
                    val monotonic = ranks.zipWithNext().all { (a, b) -> a <= b }
                    val startsAndEndsRight = authoredKinds.firstOrNull() == TrainerKind.letter_trace &&
                        authoredKinds.lastOrNull() == TrainerKind.count_add
                    if (!monotonic || !startsAndEndsRight) {
                        issues += ValidationIssue(
                            "authored lesson ${lesson.id} must hold trainer kinds in " +
                                "non-decreasing $TrainerOrder rank, starting with letter_trace " +
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

        // Der Satz-Versteher sagt in jeder Lektion dasselbe, also braucht er
        // genau eine Aufnahme: der Clip-Index schlüsselt nach Text, eine zweite
        // Formulierung würde still eine zweite Aufnahme verlangen.
        val instructions = pack.tasks.values.filterIsInstance<SentencePictureSpec>()
            .map { it.instructionTts }
            .distinct()
        if (instructions.size > 1) {
            issues += ValidationIssue(
                "sentence_picture tasks must share one instructionTts, but hold $instructions",
            )
        }
        return issues
    }

    fun requireValid(pack: ContentPack): ContentPack {
        val issues = validate(pack)
        if (issues.isNotEmpty()) throw ContentValidationException(issues)
        return pack
    }

    /** Prompt is exactly `Baue das Wort {Wort}.` — no tray-ordering instructions. */
    private fun isShortWordBuildPrompt(promptTts: String): Boolean {
        if (!promptTts.startsWith("Baue das Wort ") || !promptTts.endsWith(".")) return false
        val word = promptTts.removePrefix("Baue das Wort ").removeSuffix(".")
        if (word.isBlank() || '.' in word) return false
        return "Suche die passenden" !in promptTts && "richtige Reihenfolge" !in promptTts
    }
}
