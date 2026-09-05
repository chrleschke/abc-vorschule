package app.abcvorschule.content

import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.speech.VoiceStyle

/**
 * Was der Laut-Fresser wann sagt (design doc §5–§7). Wörter sind die kuratierten
 * Lemma-Clips; die Laute sind **dasselbe Lemma** („S", „Sch") in der Variante
 * `monster` des Clip-Index — von Hand aufgenommene Laute
 * (docs/superpowers/specs/2026-09-05-lautfresser-mikrofon-aufnahme-design.md).
 * Die App wählt die Variante über die Stimme ([VoiceStyle.MonsterLow]/[MonsterHigh])
 * und legt ihre Tonhöhe obendrauf. „Bäh!" und „Mmmmh!" sind die einzigen
 * Monster-eigenen Strings (extra-strings.json, Profil `monster`).
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
     * Der Laut des Graphems: das Lemma in Monster-Stimme. Der Clip-Index liefert
     * dafür die Aufnahme aus `variants.monster`; ohne Aufnahme fällt es auf den
     * Buchstabennamen-Clip bzw. Android-TTS zurück — „S" liest die TTS besser als
     * eine Fake-Aussprache wie „sss".
     */
    fun soundPart(round: SoundFeederRound, side: FeederSide, pack: ContentPack): SpokenPart =
        SpokenPart(lemma(pack, round.atomIdFor(side)), voiceFor(side))

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
