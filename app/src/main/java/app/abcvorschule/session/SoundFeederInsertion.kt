package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SoundFeederDerivation
import app.abcvorschule.content.SoundFeederSpec
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntSpec

/**
 * Schiebt den Laut-Fresser zur Laufzeit hinter die Buchstaben-Jagd (design doc §2):
 * das Kind hat den neuen Buchstaben gerade nachgezeichnet und gejagt, jetzt hört
 * es ihn gegen einen Bekannten, bevor es ihn in Silben verbaut. Ohne Jagd hängt
 * er am letzten Spurensucher. Ergibt die Ableitung nichts (L01, L02), passiert
 * nichts — dieselbe stille Degradation wie beim Wort-Detektiv.
 */
object SoundFeederInsertion {
    fun insertSoundFeeder(
        trainers: List<ScheduledTrainer>,
        pack: ContentPack,
        lesson: Lesson,
    ): List<ScheduledTrainer> {
        val letterHuntSuffix = ":symbol_hunt:${SymbolHuntMode.letter.name}"
        val hunt = trainers.indexOfLast { it.spec is SymbolHuntSpec && it.spec.id.endsWith(letterHuntSuffix) }
        val anchor = if (hunt >= 0) hunt else trainers.indexOfLast { it.spec is LetterTraceSpec }
        if (anchor < 0) return trainers
        val rounds = SoundFeederDerivation.buildRounds(pack, lesson)
        if (rounds.isEmpty()) return trainers
        val feeder = ScheduledTrainer(spec = SoundFeederSpec(id = "${lesson.id}:sound_feeder", rounds = rounds))
        return trainers.toMutableList().apply { add(anchor + 1, feeder) }
    }
}
