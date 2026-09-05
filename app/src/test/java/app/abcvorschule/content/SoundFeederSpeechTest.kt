package app.abcvorschule.content

import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.speech.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFeederSpeechTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val round = SoundFeederDerivation.derive(pack).getValue("l13") // S/Sch
    private val sonne = SoundFeederCard("sonne", FeederSide.left)

    @Test
    fun theIntroIsThePromptThenEachMonsterInItsOwnVoice() {
        assertEquals(
            listOf(
                SpokenPart("Füttere die Laut-Fresser."),
                SpokenPart("S", VoiceStyle.MonsterLow),
                SpokenPart("Sch", VoiceStyle.MonsterHigh),
            ),
            SoundFeederSpeech.introParts(round, pack),
        )
    }

    @Test
    fun eatingSpeaksTheSoundInMonsterVoiceThenTheBareWord() {
        // Ohne Artikel: „Sss … die Sonne" würde Laut und Wort trennen (design doc §6).
        assertEquals(
            listOf(SpokenPart("S", VoiceStyle.MonsterLow), SpokenPart("Sonne")),
            SoundFeederSpeech.eatParts(round, sonne, pack),
        )
    }

    @Test
    fun spittingSaysYuckInTheWrongMonstersVoiceThenRepeatsTheWord() {
        assertEquals(
            listOf(SpokenPart("Bäh!", VoiceStyle.MonsterHigh), SpokenPart("Sonne")),
            SoundFeederSpeech.missParts(round, sonne, wrongSide = FeederSide.right, pack = pack),
        )
    }

    @Test
    fun theFinishIsBothBurpsThenContentment() {
        assertEquals(
            listOf(
                SpokenPart("S", VoiceStyle.MonsterLow),
                SpokenPart("Sch", VoiceStyle.MonsterHigh),
                SpokenPart("Mmmmh!", VoiceStyle.MonsterLow),
            ),
            SoundFeederSpeech.finishParts(round, pack),
        )
    }

    @Test
    fun anUnknownAtomFallsBackToItsId() {
        assertEquals(SpokenPart("nirgends"), SoundFeederSpeech.wordPart(SoundFeederCard("nirgends", FeederSide.left), pack))
    }
}
