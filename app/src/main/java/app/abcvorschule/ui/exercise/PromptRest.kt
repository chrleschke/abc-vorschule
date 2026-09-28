package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import app.abcvorschule.ui.theme.AbcMotion
import kotlinx.coroutines.flow.SharedFlow

/**
 * Ruhen und Aufwachen während der Rundenansage (PRODUCT_PRINCIPLES §7, „Ansage-Sperre").
 *
 * Solange die Ansage läuft, nimmt die Aufgabe keine Tipps an — der gesuchte Buchstabe
 * steckt nur in der Stimme, jeder Tipp davor wäre Raten. Bis September 2026 war die
 * Aufgabe dabei auf 50 % gedimmt und Tipps verpufften stumm; das sah aus wie kaputt.
 *
 * Jetzt **ruht** sie: voll hell bis auf einen Hauch ([Dim]), leicht abgesunken
 * ([Scale]), und in der Jagd noch ohne Buchstaben auf den Blasen. In dem Moment, in dem
 * die Ansage ihren Freigabe-Punkt erreicht (das Symbol ist genannt), **wacht** sie auf:
 * federt mit Überschwinger auf volle Größe, und die Buchstaben erscheinen.
 *
 * Ein Wert für alle Trainer: 1 = ruht, 0 = wach. Verteilt über [LocalPromptRest].
 */
object PromptRest {
    /** Wie weit die ruhende Aufgabe schrumpft. */
    const val Scale = 0.03f

    /** Wie weit sie gedämpft ist — kaum, sie soll nicht deaktiviert aussehen. */
    const val Dim = 0.1f
}

/** 1 = die Aufgabe ruht (Ansage läuft), 0 = wach. Außerhalb einer Übung immer wach. */
val LocalPromptRest = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Tipps, die während der Ruhe auf die Aufgabe fallen, als Root-Position — damit ein
 * Trainer die getippte Stelle antworten lassen kann (die Jagd lässt die Blase wackeln).
 */
val LocalEarlyTaps = staticCompositionLocalOf<SharedFlow<Offset>?> { null }

/**
 * Wo der Lautsprecher liegt (Root). Er spielt die Ansage auch während der Sperre ab —
 * ein Tipp auf ihn ist deshalb kein „früher Tipp". Über die Lage statt über
 * „verbraucht" entschieden: gesperrte Bauteile der Trainer verbrauchen Tipps zum Teil
 * trotzdem, und dann antwortete nichts.
 */
class SpeakerBounds { var rect: Rect = Rect.Zero }

val LocalSpeakerBounds = staticCompositionLocalOf<SpeakerBounds?> { null }

/** Zählt frühe Tipps hoch — der Lautsprecher leuchtet bei jedem einmal auf: „hör zu". */
val LocalPromptNudge = compositionLocalOf { 0 }

/**
 * Der Ruhe-Wert zur Sperre. **Beim Sperren sofort 1**, noch in derselben Komposition —
 * sonst stünden die Buchstaben der neuen Runde einen Frame lang sichtbar da. Beim
 * Freigeben federt er mit der `Bouncy`-Feder auf 0 und etwas darüber hinaus: das
 * Aufwachen.
 */
@Composable
fun rememberPromptRest(locked: Boolean): State<Float> {
    val anim = remember { Animatable(if (locked) 1f else 0f) }
    val lockedNow = rememberUpdatedState(locked)
    LaunchedEffect(locked) {
        if (locked) anim.snapTo(1f) else anim.animateTo(0f, AbcMotion.Bouncy.spec())
    }
    return remember { derivedStateOf { if (lockedNow.value) 1f else anim.value } }
}

/** Die Aufgabe sinkt beim Ruhen leicht ab und federt beim Aufwachen zurück. */
fun Modifier.promptRest(rest: State<Float>): Modifier = graphicsLayer {
    val s = 1f - PromptRest.Scale * rest.value
    scaleX = s
    scaleY = s
}

/** Deckkraft für die Aufgabe eines Trainers — ersetzt die frühere 50-%-Dimmung. */
@Composable
fun rememberRestOpacity(): Float = 1f - PromptRest.Dim * LocalPromptRest.current.value.coerceIn(0f, 1f)
