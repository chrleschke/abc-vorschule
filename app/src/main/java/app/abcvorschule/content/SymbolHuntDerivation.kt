package app.abcvorschule.content

/**
 * Pure derivation for the Buchstaben-/Silben-Jagd trainer: which atoms count as
 * distractors, how many hit/distractor tiles a round gets, and the fully-resolved
 * [SymbolHuntRound] itself. No JSON is read here beyond what [ContentPack] already
 * parsed — see design doc §3 for the rules this implements.
 */
object SymbolHuntDerivation {
    private const val PromptLetterTemplate = "Finde alle Buchstaben %s!"
    private const val PromptSyllableTemplate = "Finde alle Silben %s!"

    /**
     * Distractor pool for [targetAtomId]: atoms of the eligible kind for [mode],
     * sourced *only* from letter_trace rounds (mode = letter) or syllable_merge
     * result atoms (mode = syllable) in lessons `1..currentLessonIndex` — not
     * every atom the lesson has touched. Excludes the target itself.
     */
    fun distractorPool(
        pack: ContentPack,
        currentLessonIndex: Int,
        mode: SymbolHuntMode,
        targetAtomId: String,
    ): List<String> {
        val eligibleLessons = pack.lessons.filter { it.index <= currentLessonIndex }
        val sourceAtomIds = eligibleLessons.flatMap { lesson ->
            pack.tasksOf(lesson).flatMap { spec ->
                when (mode) {
                    SymbolHuntMode.letter ->
                        (spec as? LetterTraceSpec)?.rounds?.map { it.atomId } ?: emptyList()
                    SymbolHuntMode.syllable ->
                        (spec as? SyllableMergeSpec)?.rounds?.map { it.resultAtomId } ?: emptyList()
                }
            }
        }
        val eligibleKinds = when (mode) {
            SymbolHuntMode.letter -> setOf(AtomKind.letter, AtomKind.digraph)
            SymbolHuntMode.syllable -> setOf(AtomKind.syllable)
        }
        return sourceAtomIds.distinct()
            .filter { it != targetAtomId }
            .filter { pack.atoms[it]?.kind in eligibleKinds }
    }

    /**
     * (hitCount, distractorTileCount) for a given unique-distractor pool size, or
     * null when the round must be skipped entirely (no pool at all — see design
     * doc §3's degeneration table).
     */
    fun tileCounts(poolSize: Int): Pair<Int, Int>? = when {
        poolSize <= 0 -> null
        poolSize <= 2 -> 3 to (poolSize * 2)
        else -> 5 to 6
    }

    /**
     * Builds a fully-resolved [SymbolHuntRound] for [targetAtomId], or null if the
     * round must be skipped: the target atom doesn't exist, a syllable-mode target
     * isn't actually [AtomKind.syllable], or the distractor pool is empty.
     */
    fun buildRound(
        pack: ContentPack,
        currentLessonIndex: Int,
        mode: SymbolHuntMode,
        targetAtomId: String,
    ): SymbolHuntRound? {
        val target = pack.atoms[targetAtomId] ?: return null
        if (mode == SymbolHuntMode.syllable && target.kind != AtomKind.syllable) return null
        val pool = distractorPool(pack, currentLessonIndex, mode, targetAtomId)
        if (pool.isEmpty()) return null
        val template = if (mode == SymbolHuntMode.letter) PromptLetterTemplate else PromptSyllableTemplate
        return SymbolHuntRound(
            promptTts = template.format(target.display),
            targetAtomId = targetAtomId,
            mode = mode,
            distractorPool = pool,
        )
    }
}
