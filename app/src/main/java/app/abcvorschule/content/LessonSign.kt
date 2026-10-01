package app.abcvorschule.content

/**
 * One cube on a path sign: a sound of the lesson in its capital form, and the
 * picture its side shows when the cube turns.
 *
 * @param emoji The Anlaut picture from the lesson's own Spurensucher round for this
 * sound ("M wie Mond" → 🌙), or null when the lesson does not trace it. A cube
 * without a picture simply does not turn.
 * @param tone Which of the block colours this sound wears: its position in the order
 * the Fibel introduces sounds, so the same sound has the same colour everywhere on
 * the path and two sounds introduced together never share one.
 */
data class SignBlock(val atomId: String, val glyph: String, val emoji: String?, val tone: Int)

/**
 * What a path sign shows for a lesson: one ABC block per focus sound, stacked when
 * there are more than two, plus a "Wiederholung" flag for the ↻ badge.
 */
data class LessonSign(val blocks: List<SignBlock>, val review: Boolean)

/**
 * Derives a lesson's [LessonSign] from the pack.
 *
 * Deterministic by design — no Random, no shuffling. The same lesson always yields
 * the same blocks in the same order, so the path does not rearrange itself between
 * two launches.
 *
 * Until October 2026 a sign showed the authored `nodeLabel` ("M a", "Ei w" — first
 * sound capital, the rest small, which read like a word that is none) and three
 * picture words from the lesson's later trainers, which named no sound at all. The
 * blocks now come from [Lesson.focusAtomIds] in their capital form (`ck` and `ß`
 * have none), and the picture is the Spurensucher's reward for that very sound: the
 * child hears "M wie Mond" after tracing and finds the same 🌙 on the sign's cube.
 * Showing the reward on the path is deliberate now — it is the anchor, not a spoiler.
 */
object LessonSigns {
    /** Wiederholungslektionen tragen im autorierten Label ein „+" („M a+"). */
    private const val ReviewMarker = '+'

    fun forLesson(pack: ContentPack, lesson: Lesson): LessonSign {
        // Unbekannte taskIds bleiben stillschweigend liegen (der Validator meldet sie
        // an anderer Stelle) — deshalb hier selbst aus pack.tasks lesen statt tasksOf
        // zu rufen, das auf einer unbekannten Id wirft.
        val traceRounds = lesson.taskIds
            .mapNotNull { pack.tasks[it] as? LetterTraceSpec }
            .flatMap { it.rounds }
        val introduced = pack.lessons.flatMap { it.focusAtomIds }.distinct()
        val blocks = lesson.focusAtomIds.mapNotNull { atomId ->
            val atom = pack.atoms[atomId] ?: return@mapNotNull null
            SignBlock(
                atomId = atomId,
                glyph = atom.display,
                emoji = traceRounds.firstOrNull { it.atomId == atomId }?.rewardEmoji?.takeIf { it.isNotBlank() },
                tone = introduced.indexOf(atomId),
            )
        }
        return LessonSign(
            blocks = blocks.ifEmpty { fallbackBlocks(lesson.nodeLabel) },
            review = lesson.nodeLabel.trimEnd().endsWith(ReviewMarker),
        )
    }

    /**
     * A lesson without focus atoms (only a `planned` one can be) still needs blocks,
     * or its sign would be an empty shelf: the label's tokens, capitalised, without
     * pictures.
     */
    private fun fallbackBlocks(nodeLabel: String): List<SignBlock> =
        nodeLabel.split(' ')
            .map { it.trim().trimEnd(ReviewMarker) }
            .filter { it.isNotEmpty() && it != "?" }
            .mapIndexed { i, token ->
                SignBlock(atomId = "", glyph = token.replaceFirstChar { it.uppercase() }, emoji = null, tone = i)
            }
}
