package app.abcvorschule.ui.world

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SkyBlueLight
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Die Farben der Übungs-Kopfzeile — Zurück-Pfeil, Punktestand, Fortschritt, Chevrons,
 * Lautsprecher — je nach Grund (PRODUCT_PRINCIPLES §10, „Nachtwelten").
 *
 * Auf Papier bleibt alles wie bisher. Auf einer Nachtwelt kippt die Kopfzeile auf die
 * helle Seite: Creme statt Tinte, SkyBlueLight statt SkyBlue (die helle Stufe, die es
 * für die Holzschilder ohnehin gibt), und der Lautsprecher wird der hellste Knopf der
 * Seite statt eines kaum sichtbaren Chips. Lerninhalt ist davon nie betroffen — der
 * steht auf seinen Licht-Inseln in Tinte, egal wie dunkel die Welt ist.
 */
@Immutable
data class ChromeColors(
    /** Zurück-Pfeil und Zahl des Punktestands. */
    val content: Color,
    /** Kontur des Punkte-Sterns: auf Papier braucht Gold eine dunkle Kante, auf der Nacht nicht. */
    val starOutline: Color,
    /** Chevrons (Rückfallweg) — gedämpft, aber über 3:1. */
    val chevron: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val speakerContainer: Color,
    val speakerIcon: Color,
    /** Leuchtring um den Lautsprecher, nur auf dunklem Grund. */
    val speakerGlow: Color,
    /** „Zeig mir": gedämpft, aber über 3:1 auf dem jeweiligen Grund. */
    val resolve: Color,
)

/** Die bisherigen Werte auf dem Papiergrund, unverändert übernommen. */
val PaperChrome = ChromeColors(
    content = WarmInk,
    starOutline = StarGoldDeep,
    chevron = WarmMuted.copy(alpha = 0.8f),
    progressTrack = WarmMuted.copy(alpha = 0.18f),
    progressFill = SkyBlue,
    speakerContainer = CreamElevated,
    speakerIcon = WarmInk,
    speakerGlow = Color.Transparent,
    resolve = WarmMuted,
)

/**
 * Kopfzeile auf einer Nachtwelt. Kontraste gegen den dunklen Kopf-Verlauf der Welten
 * (≈ #0A0E14): Cream 17:1, Chevron mit 0.6 rund 7:1, SkyBlueLight 8:1, die Spur mit
 * 0.22 bleibt als Stufe sichtbar und die Füllung steht auf ihr bei über 3:1.
 */
val NightChrome = ChromeColors(
    content = Cream,
    starOutline = StarGold,
    chevron = Cream.copy(alpha = 0.6f),
    progressTrack = Cream.copy(alpha = 0.22f),
    progressFill = SkyBlueLight,
    speakerContainer = Cream,
    speakerIcon = WarmInk,
    speakerGlow = Cream.copy(alpha = 0.28f),
    // WarmMuted fiel auf dem Meer auf 2.5–3.1:1; Creme mit 0.8 liegt über 9:1.
    resolve = Cream.copy(alpha = 0.8f),
)

val LocalChromeColors = staticCompositionLocalOf { PaperChrome }
