package app.abcvorschule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.world.LocalChromeColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wie lange das Kind den Zurück-Pfeil halten muss, bis die Lektion verlassen wird
 * (PRODUCT_PRINCIPLES §5/§9).
 *
 * Der Pfeil sitzt oben links, genau dort, wo eine kleine Hand beim Halten des Telefons
 * aufliegt; ein einzelner Tipp beendete bis September 2026 die Lektion ohne Rückfrage.
 * Eine Rückfrage-Box kann ein Vorschulkind nicht lesen. Halten dagegen ist eine Geste,
 * die es von der Elterntür kennt — und die Absicht verlangt, ohne Text zu brauchen.
 */
object HoldToExit {
    /** Lang genug gegen versehentliches Aufliegen, kurz genug, dass Absicht nicht nervt. */
    const val HoldMs = 800
}

/**
 * Zurück-Pfeil, der die Lektion erst nach [HoldToExit.HoldMs] Halten verlässt. Während
 * des Haltens füllt sich ein Ring um den Pfeil (SunCoral: hier passiert gerade eine
 * Handlung); lässt das Kind vorher los, läuft der Ring zurück und der Pfeil wackelt
 * einmal — die Antwort auf „du hast mich angetippt", ohne etwas auszulösen.
 *
 * Für TalkBack bleibt der Pfeil ein normaler Knopf: die Barrierefreiheits-Aktion
 * verlässt die Lektion direkt, denn Halten ist mit Screenreader keine übliche Geste.
 */
@Composable
fun AbcHoldToExitButton(
    onExit: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val progress = remember { Animatable(0f) }
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalAbcHaptics.current
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick { onExit(); true }
            }
            .pointerInput(onExit) {
                detectTapGestures(
                    onPress = {
                        var fill: Job? = null
                        fill = scope.launch {
                            try {
                                progress.animateTo(1f, tween(HoldToExit.HoldMs, easing = LinearEasing))
                                haptics.tick()
                                onExit()
                            } catch (_: CancellationException) {
                                // Losgelassen, bevor der Ring voll war.
                            }
                        }
                        val released = tryAwaitRelease()
                        if (progress.value < 1f) {
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, tween(AbcMotion.QuickMs)) }
                            if (released) {
                                scope.launch {
                                    wiggle.snapTo(1f)
                                    wiggle.animateTo(0f, AbcMotion.Wobble.spec())
                                }
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size - 8.dp)) {
            val p = progress.value
            if (p <= 0f) return@Canvas
            val stroke = 3.dp.toPx()
            drawArc(
                color = SunCoral,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
            )
        }
        IconArrowBack(
            tint = LocalChromeColors.current.content,
            size = 24.dp,
            modifier = Modifier.graphicsLayer {
                // Wackeln seitwärts, nicht drehen: ein Pfeil, der sich dreht, zeigt woanders hin.
                translationX = -WiggleDp.toPx() * wiggle.value
            },
        )
    }
}

private val WiggleDp = 5.dp
