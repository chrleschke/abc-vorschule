package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordSpec
import app.abcvorschule.content.WordBuildSpec

/**
 * Splices the Wort-Detektiv into a lesson's scheduled trainer list at runtime —
 * no JSON authoring, no ContentValidator involvement (design doc §1). It sits
 * right after the last word_build trainer, so the child hunts a symbol in a word
 * it has just finished building.
 *
 * A lesson with no word_build trainer, or one whose derived rounds all fall away
 * (single-grapheme word, no focus grapheme present), gets no detective at all —
 * the same silent degradation as [SymbolHuntInsertion].
 *
 * Order-independent with respect to [SymbolHuntInsertion]: the hunts land after
 * letter_trace and syllable_merge, which both rank before word_build, so neither
 * insertion can move the other's anchor.
 */
object SymbolInWordInsertion {
    fun insertSymbolInWord(
        trainers: List<ScheduledTrainer>,
        pack: ContentPack,
        lesson: Lesson,
    ): List<ScheduledTrainer> {
        val anchor = trainers.indexOfLast { it.spec is WordBuildSpec }
        if (anchor < 0) return trainers
        val rounds = SymbolInWordDerivation.buildRounds(pack, lesson)
        if (rounds.isEmpty()) return trainers
        val detective = ScheduledTrainer(
            spec = SymbolInWordSpec(id = "${lesson.id}:symbol_in_word", rounds = rounds),
        )
        return trainers.toMutableList().apply { add(anchor + 1, detective) }
    }
}
