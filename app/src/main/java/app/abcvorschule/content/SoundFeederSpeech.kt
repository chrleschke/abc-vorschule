package app.abcvorschule.content

import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.speech.VoiceStyle

/**
 * Was der Laut-Fresser wann sagt (design doc §5–§7). Wörter sind die kuratierten
 * Lemma-Clips, Laute die `soundTts`-Clips (Profil `sound`); nur die Stimme
 * wechselt — die Tonhöhe macht daraus das Monster. „Bäh!" und „Mmmmh!" sind die
 * einzigen Monster-eigenen Strings (extra-strings.json, Profil `monster`).
 */
object SoundFeederSpeech {
    const val Yuck = "Bäh!"
    const val Content = "Mmmmh!"

    fun voiceFor(side: FeederSide): VoiceStyle =
        if (side == FeederSide.left) VoiceStyle.MonsterLow else VoiceStyle.MonsterHigh

    private fun lemma(pack: ContentPack, atomId: String): String =
        pack.atoms[atomId]?.lemma?.takeIf { it.isNotBlank() }
            ?: pack.atoms[atomId]?.display?.takeIf { it.isNotBlank() }
            ?: atomId

    /**
     * Der Laut, nicht der Buchstabenname: „sss" statt „Es". Kommt aus [Atom.soundTts]
     * (Profil `sound`); ohne kuratierten Laut bleibt es beim Lemma-Clip.
     */
    fun soundPart(round: SoundFeederRound, side: FeederSide, pack: ContentPack): SpokenPart {
        val atomId = round.atomIdFor(side)
        val sound = pack.atoms[atomId]?.soundTts?.takeIf { it.isNotBlank() }
        return SpokenPart(sound ?: lemma(pack, atomId), voiceFor(side))
    }

    fun wordPart(card: SoundFeederCard, pack: ContentPack): SpokenPart =
        SpokenPart(lemma(pack, card.atomId))

    /** Ansage, dann stellt sich links vor, dann rechts — statt einer Frage (§7). */
    fun introParts(round: SoundFeederRound, pack: ContentPack): List<SpokenPart> = listOf(
        SpokenPart(round.promptTts),
        soundPart(round, FeederSide.left, pack),
        soundPart(round, FeederSide.right, pack),
    )

    /** „Sss … Sonne." — Laut in Monster-Stimme, Wort normal, ohne Artikel. */
    fun eatParts(round: SoundFeederRound, card: SoundFeederCard, pack: ContentPack): List<SpokenPart> =
        listOf(soundPart(round, card.side, pack), wordPart(card, pack))

    fun missParts(round: SoundFeederRound, card: SoundFeederCard, wrongSide: FeederSide, pack: ContentPack): List<SpokenPart> =
        listOf(SpokenPart(Yuck, voiceFor(wrongSide)), wordPart(card, pack))

    /** Beide rülpsen ihren Laut, dann ein gemeinsames „Mmmmh!". */
    fun finishParts(round: SoundFeederRound, pack: ContentPack): List<SpokenPart> = listOf(
        soundPart(round, FeederSide.left, pack),
        soundPart(round, FeederSide.right, pack),
        SpokenPart(Content, VoiceStyle.MonsterLow),
    )
}
