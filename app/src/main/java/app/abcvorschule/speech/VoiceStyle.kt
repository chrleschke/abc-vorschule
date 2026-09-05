package app.abcvorschule.speech

/**
 * Wie eine Äußerung klingt. Die Monster-Stimmen des Laut-Fressers sind auf
 * normalen Clips und Android-TTS **nur Tonhöhe** ([pitch], design doc §7): Artikulation
 * und Dauer bleiben, nur tiefer oder höher. Spricht der Fresser dagegen seine eigene
 * Aufnahme aus `variants.monster`, gilt die viel kleinere [variantPitch] — die Aufnahme
 * ist schon Monster. Zwei Werte je Stil, weil zwei Fresser unterscheidbar sein müssen.
 */
enum class VoiceStyle(val pitch: Float, val variantPitch: Float) {
    Normal(1f, 1f),
    MonsterLow(0.75f, VARIANT_DOWN),
    MonsterHigh(1.3f, VARIANT_UP),
}

/**
 * Eine Halbstufe: 2^(±1/12). Gilt nur für Clips aus `variants.monster` — die Aufnahme
 * trägt den Monster-Charakter (Editor-Pitch, typisch −2 HS) schon in sich, und ein
 * Reibelaut wie S rückt bei ×0.75 spektral bis ans Sch heran (gemessen 2026-09-05:
 * Schwerpunkt 7,0 → 4,8 kHz). Spiegel in tools/tts/ttskit/mic.py `APP_MONSTER_PITCH`.
 * Auf Dateiebene, weil Enum-Einträge das Companion-Objekt noch nicht sehen.
 */
private const val VARIANT_UP = 1.0595f
private const val VARIANT_DOWN = 0.9439f

/** Ein Teil einer gesprochenen Sequenz mit seiner Stimme. */
data class SpokenPart(val text: String, val voice: VoiceStyle = VoiceStyle.Normal)
