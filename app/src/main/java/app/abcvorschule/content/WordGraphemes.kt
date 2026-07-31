package app.abcvorschule.content

/**
 * Splits a written word into the grapheme units the curriculum has actually
 * taught so far — the segments the Wort-Detektiv makes tappable (design doc §3).
 *
 * The table is derived from the pack instead of hardcoded (unlike
 * [app.abcvorschule.ui.exercise.SoundWordSegments], which only tints three train
 * carriages and can afford a fixed list). Two reasons:
 *
 * 1. **Correctness.** The table must be lesson-scoped. L07 builds "Nest" and hunts
 *    the `S`; a global table would fuse `st` into one segment (`N·e·st`) and the
 *    `S` would no longer be tappable — an unsolvable round. `St` is introduced in
 *    L17, so L07 correctly yields `N·e·s·t`.
 * 2. **Locale.** The pack owns its language. A Spanish pack that authors `ll` as an
 *    atom gets `ll`-as-one-unit for free, while `l` stays a single letter in words
 *    without it.
 *
 * The pack has no [AtomKind.digraph] atoms: every multi-letter grapheme the Fibel
 * teaches (`Ei`, `Sch`, `ck`, `Pf`, `Qu`, …) is an [AtomKind.letter] atom whose
 * [Atom.display] is longer than one character. "Introduced" means some
 * [LetterTraceSpec] round in a lesson at or before the current index traces it.
 */
object WordGraphemes {
    /**
     * Multi-letter graphemes taught in lessons with `index <= [lessonIndex]`,
     * longest first so that [split] does longest-match.
     */
    fun table(pack: ContentPack, lessonIndex: Int): List<String> =
        pack.lessons
            .filter { it.index <= lessonIndex }
            .flatMap { lesson ->
                lesson.taskIds
                    .mapNotNull { pack.tasks[it] }
                    .filterIsInstance<LetterTraceSpec>()
                    .flatMap { spec -> spec.rounds.map { it.atomId } }
            }
            .mapNotNull { pack.atoms[it] }
            .filter { it.kind == AtomKind.letter && it.display.length > 1 }
            .map { it.display }
            .distinct()
            .sortedByDescending { it.length }

    /**
     * Segments of [word] against an already-resolved [table]. Segments keep the
     * word's own casing — the child sees `P·a·p·a`, not `P·A·P·A` — while matching
     * against the table ignores case.
     */
    fun split(word: String, table: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < word.length) {
            val grapheme = table.firstOrNull { candidate ->
                word.regionMatches(index, candidate, 0, candidate.length, ignoreCase = true)
            }
            if (grapheme == null) {
                result += word[index].toString()
                index += 1
            } else {
                result += word.substring(index, index + grapheme.length)
                index += grapheme.length
            }
        }
        return result
    }

    fun split(pack: ContentPack, lessonIndex: Int, word: String): List<String> =
        split(word, table(pack, lessonIndex))
}
