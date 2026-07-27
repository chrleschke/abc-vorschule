package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntSpec
import app.abcvorschule.content.SyllableMergeSpec

/**
 * Splices the (up to two) Buchstaben-/Silben-Jagd steps into a lesson's scheduled
 * trainer list at runtime — no JSON authoring, no ContentValidator involvement
 * (design doc §2). The letter hunt is derived from every letter_trace round in
 * the lesson and placed right after the last letter_trace trainer; the syllable
 * hunt is derived from every syllable_merge round and placed right after the
 * last syllable_merge trainer. A lesson missing the source kind, or one whose
 * derived rounds are all degenerate (empty distractor pool), gets no hunt for
 * that mode at all.
 */
object SymbolHuntInsertion {
    fun insertSymbolHunts(
        trainers: List<ScheduledTrainer>,
        pack: ContentPack,
        lessonId: String,
        currentLessonIndex: Int,
    ): List<ScheduledTrainer> {
        val afterLetterHunt = insertHunt(trainers, SymbolHuntMode.letter, lessonId, currentLessonIndex, pack)
        return insertHunt(afterLetterHunt, SymbolHuntMode.syllable, lessonId, currentLessonIndex, pack)
    }

    private fun insertHunt(
        trainers: List<ScheduledTrainer>,
        mode: SymbolHuntMode,
        lessonId: String,
        currentLessonIndex: Int,
        pack: ContentPack,
    ): List<ScheduledTrainer> {
        val targetAtomIds = when (mode) {
            SymbolHuntMode.letter -> trainers.filter { it.spec is LetterTraceSpec }
                .flatMap { (it.spec as LetterTraceSpec).rounds }
                .map { it.atomId }
            SymbolHuntMode.syllable -> trainers.filter { it.spec is SyllableMergeSpec }
                .flatMap { (it.spec as SyllableMergeSpec).rounds }
                .map { it.resultAtomId }
        }
        val lastSourceIndex = trainers.indexOfLast {
            if (mode == SymbolHuntMode.letter) it.spec is LetterTraceSpec else it.spec is SyllableMergeSpec
        }
        if (lastSourceIndex < 0) return trainers
        val rounds = targetAtomIds.mapNotNull { targetAtomId ->
            SymbolHuntDerivation.buildRound(pack, currentLessonIndex, mode, targetAtomId)
        }
        if (rounds.isEmpty()) return trainers
        val hunt = ScheduledTrainer(
            spec = SymbolHuntSpec(id = "$lessonId:symbol_hunt:${mode.name}", rounds = rounds),
        )
        return trainers.toMutableList().apply { add(lastSourceIndex + 1, hunt) }
    }
}
