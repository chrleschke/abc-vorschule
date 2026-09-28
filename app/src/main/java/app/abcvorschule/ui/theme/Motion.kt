package app.abcvorschule.ui.theme

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Das Bewegungs-Vokabular der App (PRODUCT_PRINCIPLES §10, „Motion-Tokens").
 *
 * Bis September 2026 trug jede Animation ihre eigenen Zahlen — 180, 200, 260,
 * 320, 350, 360, 400, 420, 450 ms, Federn mit 0,38 / 0,42 / 0,45 / 0,5 / 0,55
 * Dämpfung. Keine davon war falsch, aber zusammen sprachen sie keine Sprache:
 * dieselbe Tat (etwas erscheint, etwas rastet ein) fühlte sich je Trainer anders an.
 * Hier steht die Palette **einmal**; ein Trainer wählt daraus, statt neu zu erfinden.
 *
 * Drei Regeln, die an den Tokens hängen:
 *
 * 1. **Bewegung antwortet auf eine Tat des Kindes.** Nichts hier ist dafür da, eine
 *    Bühne beim Laden aufzubauen (§10: „ein Trainer, der geladen wird, animiert
 *    nichts") oder dauerhaft zu zappeln. Die Dauerschleifen unten sind die wenigen
 *    Aufmerksamkeits-Hinweise, die es schon gab — keine Einladung für neue.
 * 2. **Federn für alles, was man anfasst, Tweens für alles, was nur passiert.** Eine
 *    Karte unter dem Finger hat Masse; eine Einblendung hat eine Dauer.
 * 3. **Choreografie darf eigene Zahlen tragen.** Der Kau- und Spuck-Rhythmus der
 *    Laut-Fresser oder die Halte-Kurve des Jagd-Druckmorphs sind Figurenspiel, keine
 *    Grammatik — sie behalten ihre Werte und dokumentieren sie dort, wo sie stehen.
 *
 * Die Dauern steigen in Stufen von grob 1,4 — eine Stufe weiter ist gerade eben
 * spürbar länger. Gewählt nach den Werten, die schon im Code lagen; beim Umzug
 * wurden Nachbarn auf die nächste Stufe gerundet (180 → 170, 320/350 → 360, 400/420 →
 * 450), was kein Auge unterscheidet.
 */
object AbcMotion {
    /** Kollaps, Zuschnappen — kürzer als ein Blinzeln. */
    const val MicroMs = 90

    /** Kleine Antworten: Wegploppen, Einblenden eines Sterns, Aufgaben-Sperre dimmen. */
    const val QuickMs = 170

    /** Überblendungen zwischen zwei Zuständen desselben Bauteils. */
    const val ShortMs = 260

    /** Der Normalfall: ein Ding bewegt sich von A nach B. */
    const val StandardMs = 360

    /** Betonte Bewegung: Drehen, Füllen, Ausblenden eines ganzen Felds. */
    const val LongMs = 450

    /** Feiern — Funken, Konfetti-Anlauf. Länger nur beim Lektions-Ende. */
    const val CelebrateMs = 600

    /** Halbe Periode der Puls-Schleifen (Batterie-Saum, Detektiv-Glühen). */
    const val PulseMs = 500

    /** Halbe Periode des Atmens (Ring des aktuellen Pfad-Schilds). */
    const val BreathMs = 900

    /** Halbe Periode des Wippens (Du-bist-hier-Marker). */
    const val BobMs = 1100

    /** Etwas kommt herein oder bewegt sich: schnell los, weich an. */
    val Enter: Easing = FastOutSlowInEasing

    /** Etwas geht: langsam los, schnell weg. */
    val Exit: Easing = FastOutLinearInEasing

    /** Etwas wächst und kommt zur Ruhe (Halten, Nachklingen). */
    val Linger: Easing = LinearOutSlowInEasing

    /** Etwas füllt sich und rastet am Ende ein (Balken im Spurensucher). */
    val Fill: Easing = EaseIn

    /**
     * Weich einrasten, mit sichtbarem Nachwippen — die Squish-Feder aus
     * [app.abcvorschule.ui.exercise.SlotFillMorph], auch das Anschwellen der Fresser.
     */
    val Settle = MotionSpring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)

    /**
     * Gedämpft zurück an den Platz, ein kleiner Überschwinger: der Rückflug einer
     * daneben abgelegten Karte, das Aufpoppen einer neuen Karte.
     */
    val Soft = MotionSpring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

    /** Erscheinen mit Schwung: Erfolgs-Stern, fertig verschmolzene Silbe, Anfassen. */
    val Bouncy = MotionSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Langsames Zurückgleiten und Atmen (Silben-Kacheln, die sich nicht trafen). */
    val Glide = MotionSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** Hart und schnell zurück in Form — das „Plopp" nach dem Kollaps. */
    val Pop = MotionSpring(dampingRatio = 0.38f, stiffness = Spring.StiffnessHigh)

    /** Wackelt aus: die Karte, die das falsche Maul ausspuckt. */
    val Wobble = MotionSpring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)

    /** Ohne Nachwippen ans Ziel — zwei Kacheln, die zusammenschnappen. */
    val Snap = MotionSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}

/** Eine benannte Feder der Palette; [spec] liefert sie für jeden animierten Typ. */
data class MotionSpring(val dampingRatio: Float, val stiffness: Float) {
    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness, visibilityThreshold = visibilityThreshold)
}
