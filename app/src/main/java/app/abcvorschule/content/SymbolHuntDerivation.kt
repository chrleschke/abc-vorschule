package app.abcvorschule.content

/**
 * Pure derivation for the Buchstaben-/Silben-Jagd trainer: which atoms count as
 * distractors, how many hit/distractor tiles a round gets, and the fully-resolved
 * [SymbolHuntRound] itself. No JSON is read here beyond what [ContentPack] already
 * parsed — see design doc §3 for the rules this implements.
 */
/** Which base hunt prompt applies — Buchstabe vs Laut vs Silbe naming. */
enum class SymbolHuntPromptKind { Buchstabe, Laut, Silbe }

object SymbolHuntDerivation {
    /** Base Qwen prompt clips — grapheme is spoken separately (phoneme lemma). */
    const val PromptLetter = "Finde alle Buchstaben"
    const val PromptDigraph = "Finde alle Laute"
    const val PromptSyllable = "Finde alle Silben"

    fun promptKind(mode: SymbolHuntMode, target: Atom): SymbolHuntPromptKind = when {
        mode == SymbolHuntMode.syllable -> SymbolHuntPromptKind.Silbe
        target.display.length > 1 -> SymbolHuntPromptKind.Laut
        else -> SymbolHuntPromptKind.Buchstabe
    }

    fun promptText(kind: SymbolHuntPromptKind): String = when (kind) {
        SymbolHuntPromptKind.Buchstabe -> PromptLetter
        SymbolHuntPromptKind.Laut -> PromptDigraph
        SymbolHuntPromptKind.Silbe -> PromptSyllable
    }

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
        return SymbolHuntRound(
            promptTts = promptText(promptKind(mode, target)),
            targetAtomId = targetAtomId,
            mode = mode,
            distractorPool = pool,
        )
    }
}
