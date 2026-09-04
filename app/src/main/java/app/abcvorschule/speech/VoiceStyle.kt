package app.abcvorschule.speech

/**
 * Wie eine Äußerung klingt. Die Monster-Stimmen des Laut-Fressers sind **nur
 * Tonhöhe** auf den kuratierten Clips (design doc §7): Artikulation und Dauer
 * bleiben, ein Kind, das gerade S gegen Sch lernt, hört ein sauberes „sss" — nur
 * tiefer oder höher. Zwei Werte, weil zwei Fresser unterscheidbar sein müssen.
 */
enum class VoiceStyle(val pitch: Float) {
    Normal(1f),
    MonsterLow(0.75f),
    MonsterHigh(1.3f),
}

/** Ein Teil einer gesprochenen Sequenz mit seiner Stimme. */
data class SpokenPart(val text: String, val voice: VoiceStyle = VoiceStyle.Normal)
