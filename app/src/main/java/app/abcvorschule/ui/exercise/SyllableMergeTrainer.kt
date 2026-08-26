package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.WarmInk
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MergeProgress {
    /** Contact while dragging: the tiles physically touch and merge immediately. */
    const val CommitFraction = 0.98f

    /** Release at or past this and the magnet pulls the tiles the rest of the way. */
    const val AttractFraction = 0.6f

    /** One tap nudges the tiles this much closer; two taps reach the magnet zone. */
    const val TapStep = 0.3f

    /** Shared closing progress: either tile drags it, the right one with inverted sign. */
    fun applyDrag(fraction: Float, deltaPx: Float, travelPx: Float, fromRightTile: Boolean): Float {
        if (travelPx <= 0f) return fraction
        val towardsMiddle = if (fromRightTile) -deltaPx else deltaPx
        return (fraction + towardsMiddle / travelPx).coerceIn(0f, 1f)
    }

    fun stepped(fraction: Float): Float = (fraction + TapStep).coerceIn(0f, 1f)

    fun isContact(fraction: Float): Boolean = fraction >= CommitFraction

    fun shouldAttract(fraction: Float): Boolean = fraction >= AttractFraction

    /** Visual stand-in for the intensifying sound: 0.25 at rest, 1.0 on contact. */
    fun glow(fraction: Float): Float = (0.25f + 0.75f * fraction.coerceIn(0f, 1f))
}

private val IdleNudge = 10.dp
private const val IdleNudgeDelayMs = 3_500L
private const val TrackWaveMs = 1_400

/**
 * Trainer 3 — Silben-Verschmelzer. Both tiles are slidable and converge
 * symmetrically on the middle (magnet metaphor): a dotted track with an inward
 * light wave and an idle "breathing" nudge invite the slide without any text.
 * Releasing inside the magnet zone snaps the tiles together; a tap reads the
 * sound aloud and nudges one step closer, so two taps merge without a gesture.
 * System TTS cannot stretch a phoneme continuously, so the stretched sound
 * plays once per gesture and the intensification is carried visually.
 */
@Composable
fun SyllableMergeTrainer(
    round: SyllableMergeRound,
    roundIndex: Int,
    resultSpeech: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.leftAtomId}-${round.rightAtomId}"
    val density = LocalDensity.current
    val idleNudgePx = with(density) { IdleNudge.toPx() }
    val scope = rememberCoroutineScope()
    val fraction = remember(roundKey) { Animatable(0f) }
    val idleNudge = remember(roundKey) { Animatable(0f) }
    var merged by remember(roundKey) { mutableStateOf(false) }
    var dragging by remember(roundKey) { mutableStateOf(false) }
    var interactions by remember(roundKey) { mutableIntStateOf(0) }
    val scoredIds = remember(roundKey) {
        listOf(round.leftAtomId, round.rightAtomId, round.resultAtomId).distinct()
    }
    val haptics = LocalAbcHaptics.current
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "syllable_merge_lock_opacity",
    )

    fun commit() {
        if (merged) return
        merged = true
        // Small collect-moment for the snap itself: der Schnapp ist ein
        // Sammel-Erfolg, kein eigener Trainer-Abschluss — der große Stern am Ende
        // kommt bewusst ohne Haptik dazu.
        haptics.tick()
        onResult(true, false, scoredIds)
    }

    // Der Anzieh-Schnapp läuft rundengebunden statt in scope.launch: eine
    // scope-Coroutine überlebte einen Chevron-Rundenwechsel während der Feder
    // (einige hundert ms) und würde ihr onResult(true) der NEUEN Runde
    // gutschreiben. Der Rundenwechsel cancelt diesen Effect stattdessen.
    //
    // Ein Zähler, kein Bool: legt das Kind während des Schnapps den Finger
    // wieder auf und zieht, präemptiert das `fraction.snapTo` aus `onDrag` die
    // laufende `animateTo`-Mutation (MutatorMutex), dieser Effect wird gecancelt
    // und `commit()` läuft nie. Ein Bool bliebe danach für den Rest der Runde
    // auf true — derselbe Wert startet den Effect nicht neu, und damit wären
    // Magnet-Pfad UND Zwei-Tipp-Pfad tot. Der Trainer hat kein „Zeig mir", das
    // Kind säße fest. Jede neue Anzieh-Bitte ist deshalb ein neuer Wert.
    var attractRequest by remember(roundKey) { mutableIntStateOf(0) }
    LaunchedEffect(roundKey, attractRequest) {
        if (attractRequest == 0) return@LaunchedEffect
        idleNudge.snapTo(0f)
        fraction.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        // Nur verbuchen, wenn die Feder wirklich durchgelaufen ist: ein
        // abgebrochener Schnapp ist keine verschmolzene Runde.
        if (MergeProgress.isContact(fraction.value)) commit()
    }

    fun settle() {
        dragging = false
        interactions++
        if (merged) return
        if (MergeProgress.shouldAttract(fraction.value)) {
            attractRequest++
        } else {
            scope.launch {
                // No penalty: a short pull just glides back.
                fraction.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
        }
    }

    fun speakTile(fromRightTile: Boolean) {
        onSpeak(if (fromRightTile) round.rightDisplay else round.stretchTts)
    }

    // Tap = read the sound aloud and nudge the tiles one step closer: the
    // motor-skill-friendly path to the merge, replacing the old "→|" button.
    fun nudgeTap(fromRightTile: Boolean) {
        interactions++
        speakTile(fromRightTile)
        if (merged) return
        val target = MergeProgress.stepped(fraction.value)
        if (MergeProgress.shouldAttract(target)) {
            attractRequest++
        } else {
            scope.launch {
                idleNudge.snapTo(0f)
                fraction.animateTo(
                    target,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )
            }
        }
    }

    // travelPx ist Parameter UND pointerInput-Key: die Reisedistanz kommt jetzt aus
    // der gemessenen Bühnenbreite, und ein pointerInput fängt seine Captures beim
    // Start ein — ohne den Key rechnete eine nach Breiten-/Skalenwechsel laufende
    // Geste mit der alten Distanz weiter.
    fun Modifier.mergeDrag(fromRightTile: Boolean, enabled: Boolean, travelPx: Float): Modifier =
        if (!enabled) this else pointerInput(roundKey, travelPx) {
            detectDragGestures(
                onDragStart = {
                    dragging = true
                    // Der Finger übernimmt: ein laufender Anzieh-Schnapp gibt ab
                    // (sein snapTo unten cancelt ihn ohnehin), und die Bitte wird
                    // zurückgesetzt, damit das nächste Loslassen wieder als NEUER
                    // Wert ankommt und den Effect startet.
                    attractRequest = 0
                    scope.launch { idleNudge.snapTo(0f) }
                    speakTile(fromRightTile)
                },
                onDrag = { change, amount ->
                    change.consume()
                    val target = MergeProgress.applyDrag(fraction.value, amount.x, travelPx, fromRightTile)
                    scope.launch { fraction.snapTo(target) }
                    if (MergeProgress.isContact(target)) {
                        dragging = false
                        commit()
                    }
                },
                onDragEnd = { settle() },
                onDragCancel = { settle() },
            )
        }

    // Invitation to slide: after a quiet moment the tiles breathe towards each
    // other once, and keep reminding until the child takes over.
    LaunchedEffect(roundKey, merged, dragging, interactions) {
        if (merged || dragging) return@LaunchedEffect
        while (true) {
            delay(IdleNudgeDelayMs)
            idleNudge.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
            idleNudge.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
    }

    val resultScale = remember(roundKey) { Animatable(0.6f) }
    LaunchedEffect(merged) {
        if (merged) {
            resultScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    ExerciseStage(
        modifier = modifier,
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            // Gemessen statt angenommen, wie der Wort-Detektiv: die alten festen
            // Breiten (min. 108 + 120 + 108 = 336dp, mit „sch" 366dp) überliefen
            // die ~296dp nutzbare Bühne eines 360dp-Geräts und beschnitten die
            // rechte Kachel. Breiten, Lücke, Glyph und Spur-Versatz kommen aus
            // EINER Rechnung (SyllableFrameSizing), damit die Spur-Mitte exakt
            // der Treffpunkt der Kacheln ist (§3.3-Symmetrie).
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val fontScale = LocalDensity.current.fontScale
                if (merged) {
                    val spec = SyllableFrameSizing.resultFloe(
                        availableDp = maxWidth.value,
                        label = round.resultDisplay,
                        fontScale = fontScale,
                    )
                    Floe(
                        label = round.resultDisplay,
                        widthDp = spec.widthDp,
                        glyphSp = spec.glyphSp,
                        glow = { 1f },
                        frozen = true,
                        onTap = { onSpeak(resultSpeech) },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = resultScale.value
                                scaleY = resultScale.value
                            }
                            .testTag("merge_result"),
                    )
                } else {
                    val layout = SyllableFrameSizing.mergeLayout(
                        availableDp = maxWidth.value,
                        leftLabel = round.leftDisplay,
                        rightLabel = round.rightDisplay,
                        fontScale = fontScale,
                    )
                    // Each tile travels half the gap, so they meet in the middle;
                    // the dragged tile stays 1:1 under the finger while its
                    // partner mirrors the motion.
                    val tileTravelPx = with(density) { (layout.gapDp / 2f).dp.toPx() }
                    // Feder-Werte NUR in Lambdas, nie hier in der Komposition:
                    // ein `fraction.value` an dieser Stelle rekomponierte den
                    // ganzen Trainer pro Zieh-Frame und bei jedem Idle-Atemzug
                    // (der Loop läuft alle 3,5s endlos). Dieselbe Pflicht wie
                    // beim Peg-Morph des Satz-Architekten (§10, SlotFillMorph).
                    val inwardPx = {
                        fraction.value * tileTravelPx + idleNudge.value * idleNudgePx
                    }
                    Box(contentAlignment = Alignment.Center) {
                        MergeTrack(
                            progress = { fraction.value },
                            modifier = Modifier
                                .offset(x = layout.trackOffsetDp.dp)
                                .width(layout.gapDp.dp)
                                .height(AbcDimens.letterFrame),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Floe(
                                label = round.leftDisplay,
                                widthDp = layout.leftWidthDp,
                                glyphSp = layout.glyphSp,
                                glow = { MergeProgress.glow(fraction.value) },
                                frozen = false,
                                onTap = { nudgeTap(fromRightTile = false) },
                                enabled = !interactionLocked,
                                opacity = interactionOpacity,
                                modifier = Modifier
                                    .offset { IntOffset(inwardPx().roundToInt(), 0) }
                                    .mergeDrag(
                                        fromRightTile = false,
                                        enabled = !interactionLocked,
                                        travelPx = tileTravelPx,
                                    )
                                    .testTag("merge_left"),
                            )
                            Spacer(Modifier.width(layout.gapDp.dp))
                            Floe(
                                label = round.rightDisplay,
                                widthDp = layout.rightWidthDp,
                                glyphSp = layout.glyphSp,
                                glow = { MergeProgress.glow(fraction.value) },
                                frozen = false,
                                onTap = { nudgeTap(fromRightTile = true) },
                                enabled = !interactionLocked,
                                opacity = interactionOpacity,
                                modifier = Modifier
                                    .offset { IntOffset(-inwardPx().roundToInt(), 0) }
                                    .mergeDrag(
                                        fromRightTile = true,
                                        enabled = !interactionLocked,
                                        travelPx = tileTravelPx,
                                    )
                                    .testTag("merge_right"),
                            )
                        }
                    }
                }
            }
        },
        answers = {},
    )
}

/**
 * Dotted slide track between the tiles. A soft brightness wave travels from
 * both edges towards the middle — the wordless "push them together" cue —
 * and the whole track fades out as the tiles approach each other.
 */
@Composable
private fun MergeTrack(progress: () -> Float, modifier: Modifier = Modifier) {
    // Bewusst ohne `by`: Welle und Fortschritt werden erst im Canvas gelesen,
    // also in der Zeichenphase. Mit `by` rekomponierte die endlose Welle diese
    // Funktion Frame für Frame, solange die Runde läuft.
    val phase = rememberInfiniteTransition(label = "merge_track")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(TrackWaveMs, easing = LinearEasing)),
            label = "merge_track_phase",
        )
    Canvas(modifier) {
        val fade = (1f - progress()).coerceIn(0f, 1f)
        if (fade <= 0f) return@Canvas
        val dotRadius = 3.dp.toPx()
        val dotCount = 7
        val stepX = size.width / (dotCount + 1)
        val centerY = size.height / 2f
        for (i in 1..dotCount) {
            val x = stepX * i
            // 0 at the edges, 1 in the middle — the wave chases this value.
            val toMiddle = 1f - abs(x - size.width / 2f) / (size.width / 2f)
            val highlight = (1f - abs(toMiddle - phase.value) * 3f).coerceIn(0f, 1f)
            drawCircle(
                color = SkyBlue.copy(alpha = fade * (0.30f + 0.55f * highlight)),
                radius = dotRadius * (0.8f + 0.4f * highlight),
                center = Offset(x, centerY),
            )
        }
    }
}

/** Ruheradius der Kachelecke — unverändert der bisherige Wert. */
private val FloeCornerRadius = 26.dp

private val FloeBorderWidth = 4.dp

@Composable
private fun Floe(
    label: String,
    /** Aus [SyllableFrameSizing] gelöst — Breite und Glyph kommen aus derselben
     * Rechnung wie Lücke und Spur, sonst driftet die Zeile wieder auseinander. */
    widthDp: Float,
    glyphSp: Float,
    /** Als Lambda, nicht als Wert: der Glühwert hängt am Zieh-Fortschritt und
     * wird ausschließlich in der Zeichenphase gelesen (§10) — als Parameter
     * rekomponierte er den ganzen Trainer pro Zieh-Frame. Der frühere
     * `animateFloatAsState`-Nachlauf entfällt damit: `fraction` ist beim Ziehen
     * ohnehin fingergenau und läuft sonst auf einer eigenen Feder. */
    glow: () -> Float,
    frozen: Boolean,
    onTap: () -> Unit,
    enabled: Boolean = true,
    opacity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val fill = if (frozen) LeafGreen.copy(alpha = 0.25f) else CreamElevated
    val borderColor = if (frozen) LeafGreen else SkyBlue
    Box(
        modifier = modifier
            .width(widthDp.dp)
            .height(AbcDimens.letterFrame)
            .alpha(opacity)
            // Fläche und Rand selbst gezeichnet statt background()/border(): nur
            // so lässt sich der Glühwert in der Zeichenphase lesen. Der Rand
            // sitzt um seine halbe Breite eingerückt und damit innen, genau wie
            // Modifier.border — dieselbe Rechnung wie am Rahmen des Wort-Bauers.
            .drawBehind {
                val radius = FloeCornerRadius.toPx()
                drawRoundRect(color = fill, cornerRadius = CornerRadius(radius))
                val stroke = FloeBorderWidth.toPx()
                drawRoundRect(
                    color = borderColor.copy(alpha = glow().coerceIn(0f, 1f)),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius((radius - stroke / 2f).coerceAtLeast(0f)),
                    style = Stroke(width = stroke),
                )
            }
            .clickable(enabled = enabled, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            // Effektiv auf AbcDimens.letterSp gedeckelt (SyllableFrameSizing teilt
            // durch fontScale, Muster FinaleLayout.capEffectiveSize) und auf
            // schmalen Bühnen darunter geschrumpft, damit die Zeile nie überläuft.
            fontSize = glyphSp.sp,
            // Always WarmInk, even when frozen: LeafGreen text on the LeafGreen-tinted
            // wash below only reaches ~2.25:1 (and ~2.87:1 even against plain
            // CreamElevated) — short of the required 3:1 for large glyphs on Cream. The
            // wash, border and scale-in animation already carry the "merged" cue.
            color = WarmInk,
        )
    }
}
