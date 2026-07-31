package app.abcvorschule.content

/**
 * The picture words a path signpost shows for a lesson: at most three emojis,
 * drawn from the lesson's own vocabulary.
 *
 * Deterministic by design — no Random, no shuffling. The same lesson always
 * yields the same emojis in the same order, so the path does not rearrange
 * itself between two launches.
 */
object LessonEmojis {
    const val DefaultLimit = 3

    fun forLesson(pack: ContentPack, lesson: Lesson, limit: Int = DefaultLimit): List<String> {
        if (limit <= 0) return emptyList()
        val specs = lesson.taskIds.mapNotNull { pack.tasks[it] }
        val chosen = LinkedHashSet<String>()
        for (atomId in sourceAtomIds(specs)) {
            val emoji = pack.atoms[atomId]?.emoji.orEmpty()
            // Dedupe on the glyph, not the atom id: `dach` and `haus` share one
            // house emoji, and two identical pictures read as a bug.
            if (emoji.isNotBlank()) chosen += emoji
            if (chosen.size == limit) break
        }
        return chosen.toList()
    }

    /**
     * Atom ids in the order a sign should prefer them — the trainers whose atoms
     * actually carry a picture. letter_trace and syllable_merge are skipped: their
     * atoms are letters and syllables, and those have no emoji in the content.
     * letter_trace's own `rewardEmoji` is left out on purpose too — it is the
     * trainer's reward and should not be spoiled on the path.
     */
    private fun sourceAtomIds(specs: List<TaskSpec>): List<String> =
        specs.filterIsInstance<SoundPositionSpec>().flatMap { spec -> spec.rounds.map { it.atomId } } +
            specs.filterIsInstance<WordBuildSpec>().flatMap { spec -> spec.rounds.map { it.targetAtomId } } +
            specs.filterIsInstance<CountAddSpec>().flatMap { spec -> spec.rounds.map { it.iconAtomId } } +
            specs.filterIsInstance<SentenceOrderSpec>()
                .flatMap { spec -> spec.rounds.mapNotNull { it.illustrationAtomId } }
}
