package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import kotlinx.coroutines.delay

// Matches AbcDimens.kidTouch (the app-wide minimum touch target for 4-6-year-olds)
// so that even the smallest scattered tile (scale 0.8, see SymbolHuntLayout) renders
// at 64dp — comfortably above the design spec's 56dp hit-box floor.
private val TileSize = AbcDimens.kidTouch

/** Obergrenze des Kachel-Glyphen — die bisherige feste Größe, jetzt nur noch der
 * Deckel: einzelne Buchstaben auf jeder Kachel bleiben exakt wie gehabt, nur
 * Mehrzeichen-Symbole („Sch") und große Schriftskalierungen schrumpfen darunter. */
private const val MaxTileGlyphSp = 28f

// Four, not five: the dark theme's fifth entry (SoftSand, near-white) worked against a
// dark field but a near-Cream tile on the light field would vanish into the page. Also
// swaps StarGold for StarGoldDeep — StarGold's own border-solid contrast against Cream
// is only ~1.85:1 (see Color.kt), well under the 3:1 UI-component floor this tile's ring
// needs; StarGoldDeep clears it at ~3.29:1. The other three, at full opacity against
// Cream: SunCoral ~3.61:1, SkyBlue ~3.88:1, LeafGreen ~3.57:1 — all pass.
private val TilePalette = listOf(SunCoral, SkyBlue, StarGoldDeep, LeafGreen)

/**
 * Buchstaben-/Silben-Jagd: tiles scatter across the whole task area under a
 * fixed speaker strip (deliberate exception to Prinzip 9 — design doc §4), the
 * battery lives in the answer area (also an exception). A wrong tap reshuffles
 * without losing battery progress; when the battery fills, a short celebration
 * plays (400 ms field fade + golden pulse), then auto-proceeds after HuntCelebration.HoldMs
 * to the shared success pipeline — no "Weiter" tap needed (design doc §5).
 */
@Composable
fun SymbolHuntTrainer(
    round: SymbolHuntRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.targetAtomId}-${round.mode}"
    var state by remember(roundKey) {
        mutableStateOf(SymbolHuntProgress.initialState(round, seed = roundKey.hashCode().toLong()))
    }
    // Captured once, before any tap can shrink state.tiles — the scatter layout
    // must stay keyed on the round's original tile count, not the shrinking list,
    // so surviving tiles keep their position/color across a correct tap. Das Feld
    // komponiert außerdem über diese Liste (nicht über state.tiles), damit eine
    // eingesammelte Kachel noch wegploppen kann statt zu verschwinden.
    val initialTiles = remember(roundKey) { state.tiles }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    // Ladestand, den das Kind selbst geschafft hat. `resolve()` füllt die Batterie
    // in der Logik auf (die Runde ist damit abgeschlossen), aber angezeigt bleibt
    // der echte Stand: eine volle Batterie nach „Zeig mir" wäre eine Feier für
    // etwas, das das Kind nicht geschafft hat — dieselbe Regel, nach der die Pegs
    // des Satz-Architekten nach dem Auflösen still fallen (PRODUCT_PRINCIPLES §10).
    var earnedBeforeResolve by remember(roundKey) { mutableStateOf<Int?>(null) }
    var batteryFull by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalAbcHaptics.current

    fun handleTap(instanceId: Int) {
        if (resolved || batteryFull) return
        val tapped = state.tiles.firstOrNull { it.instanceId == instanceId } ?: return
        onSpeak(pack.atoms[tapped.atomId]?.lemma ?: tapped.atomId)
        val result = SymbolHuntProgress.tap(state, instanceId)
        state = result.state
        when (result.outcome) {
            SymbolHuntTapOutcome.Miss -> {
                // Nudge on every wrong tap the child makes (matching the
                // reshuffle-every-time behavior, not just the first-reported miss),
                // following the same haptic pattern as LetterTraceTrainer's
                // off-corridor excursion feedback.
                haptics.nudge()
                onResult(false, false, listOf(round.targetAtomId))
            }
            SymbolHuntTapOutcome.MissAlreadyReported ->
                haptics.nudge()
            SymbolHuntTapOutcome.Collected -> haptics.tick()
            SymbolHuntTapOutcome.RoundComplete -> {
                batteryFull = true
                haptics.celebrate()
            }
            SymbolHuntTapOutcome.Ignored -> Unit
        }
    }

    val fieldAlpha by animateFloatAsState(
        targetValue = if (batteryFull) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "hunt_field_fade",
    )
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "hunt_lock_opacity",
    )

    // Auto-proceed: the battery filling up IS the success signal, so a "Weiter"
    // tap only added a dead end for a child who cannot read the button. The delay
    // sits in front of onResult because reporting the result starts the spoken
    // success phase, which must not talk over the celebration.
    LaunchedEffect(batteryFull) {
        if (!batteryFull) return@LaunchedEffect
        delay(HuntCelebration.HoldMs)
        onResult(true, false, listOf(round.targetAtomId))
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
            if (!resolved) {
                SymbolHuntField(
                    roundKey = roundKey,
                    state = state,
                    initialTiles = initialTiles,
                    pack = pack,
                    enabled = !batteryFull && !interactionLocked,
                    onTap = ::handleTap,
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha * interactionOpacity),
                )
            }
        },
        answers = {
            SymbolHuntBattery(
                collected = earnedBeforeResolve ?: state.collected,
                total = state.targetHitCount,
                celebrate = batteryFull,
            )
            if (SymbolHuntProgress.resolveAvailable(state) && !resolved && !batteryFull) {
                AbcResolveButton(
                    onClick = {
                        earnedBeforeResolve = state.collected
                        resolved = true
                        state = SymbolHuntProgress.resolve(state)
                        onResult(false, true, listOf(round.targetAtomId))
                    },
                )
            }
        },
    )
}

@Composable
private fun SymbolHuntField(
    /** Keyt die Kacheln (siehe unten) — ein SymbolHuntSpec trägt mehrere Runden. */
    roundKey: String,
    state: SymbolHuntState,
    initialTiles: List<SymbolHuntTile>,
    pack: ContentPack,
    enabled: Boolean = true,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // Keyed on the round's original tile count (never on state.tiles.size,
        // which shrinks on every hit) so a correct tap — which does not touch
        // state.seed — cannot reshuffle the surviving tiles. A wrong tap DOES
        // bump state.seed, which is the intended "mix the field" reshuffle.
        val tileSizePx = with(density) { TileSize.toPx() }
        val positions = remember(state.seed, initialTiles.size, widthPx, heightPx, tileSizePx) {
            SymbolHuntLayout.scatter(state.seed, initialTiles.size, widthPx, heightPx, tileSizePx)
        }
        // Gelaufen wird über die *ursprüngliche* Kachelliste, nicht über
        // state.tiles: eine eingesammelte Kachel muss noch ein paar Frames
        // komponiert bleiben, sonst gibt es kein Wegploppen (HuntTileMorph
        // Phase 4) — sie wäre schlicht weg. Wer noch im Feld liegt, sagt
        // `present`; wer fertig weggeploppt ist, komponiert sich selbst heraus.
        val presentIds = remember(state.tiles) { state.tiles.mapTo(HashSet()) { it.instanceId } }
        initialTiles.forEach { tile ->
            // Indexed by the tile's stable instanceId (its index in the original,
            // pre-shrink tile list) rather than its position in the current
            // (shrinking) list, so a surviving tile keeps the same slot/color.
            val position = positions.getOrNull(tile.instanceId) ?: return@forEach
            // `key(roundKey, …)` ist Pflicht: ohne Schlüssel hängt die Identität
            // einer Kachel an ihrer Position in dieser Schleife, und ein
            // SymbolHuntSpec trägt mehrere Runden hintereinander. Slot i der neuen
            // Runde erbte dann `poppedAway = true` von der Treffer-Kachel der
            // Vorrunde und fehlte einen Frame lang, bis der present-Effect ihn
            // zurücksetzt — dieselbe Falle wie beim ungekeyten AnimatedContent des
            // Wort-Bauers (§10).
            key(roundKey, tile.instanceId) {
                HuntTile(
                    glyph = pack.atoms[tile.atomId]?.display ?: tile.atomId,
                    position = position,
                    color = TilePalette[tile.instanceId % TilePalette.size],
                    present = tile.instanceId in presentIds,
                    enabled = enabled,
                    onTap = { onTap(tile.instanceId) },
                    modifier = Modifier.testTag("hunt_tile_${tile.instanceId}"),
                )
            }
        }
    }
}

/** Merker „diese Kachel war schon einmal unter dem Finger". Bewusst kein
 * `mutableStateOf`: er wird nur im Druck-Effekt gelesen und geschrieben, eine
 * Recomposition dafür wäre umsonst. Ohne ihn liefe der Loslassen-Zweig schon
 * beim ersten Komponieren mit und das ganze Feld wackelte beim Rundenstart. */
private class HuntPressLatch { var touched = false }

/**
 * Eine Kachel im Streufeld, samt Druck-Morph — Kurven, Grenzen und Begründung
 * stehen in [HuntTileMorph].
 *
 * Beide Federwerte werden ausschließlich in der **Zeichenphase** gelesen
 * (`graphicsLayer` / `drawBehind`), nie in der Komposition: 1,5 Sekunden Halten
 * dürfen nicht 1,5 Sekunden lang rekomponieren, und die Streuposition darf
 * dabei nicht unter dem Finger wandern (gleiche Begründung wie beim Peg-Morph
 * des Satz-Architekten). Rekomponiert wird nur beim Kippen von `pressed` und
 * einmal am Ende des Wegploppens.
 */
@Composable
private fun HuntTile(
    glyph: String,
    position: HuntTilePosition,
    color: Color,
    present: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inflate = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val latch = remember { HuntPressLatch() }
    var poppedAway by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) {
            latch.touched = true
            // Anfassen: kurze Feder auf +6 %, mit leichtem Nachwippen.
            inflate.animateTo(
                targetValue = HuntTileMorph.PressPuff,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
            )
            // Halten: verzögert weiter bis zum Deckel +10 % und dort stillstehen.
            inflate.animateTo(
                targetValue = HuntTileMorph.MaxInflate,
                animationSpec = tween(
                    durationMillis = HuntTileMorph.HoldMs,
                    easing = LinearOutSlowInEasing,
                ),
            )
        } else if (latch.touched) {
            // Loslassen: schneller Kollaps unter den Ruhedurchmesser …
            inflate.animateTo(
                targetValue = -HuntTileMorph.CollapseUndershoot,
                animationSpec = tween(
                    durationMillis = HuntTileMorph.CollapseMs,
                    easing = FastOutLinearInEasing,
                ),
            )
            // … dann das Plopp zurück in Form.
            inflate.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessHigh),
            )
        }
    }

    // Eigener Effekt auf einem eigenen Animatable: beim Treffer laufen Kollaps
    // (Finger geht hoch) und Wegploppen (Kachel verlässt das Feld) im selben
    // Frame los und dürfen sich nicht gegenseitig abbrechen — eine geteilte
    // Feder würde genau das tun.
    LaunchedEffect(present) {
        if (present) {
            exit.snapTo(0f)
            poppedAway = false
        } else {
            exit.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = HuntTileMorph.PopAwayMs,
                    easing = FastOutLinearInEasing,
                ),
            )
            poppedAway = true
        }
    }

    // Nach dem Ploppen wirklich raus aus dem Baum: eine auf Deckkraft 0
    // stehengelassene Kachel bliebe mit ihrem Glyphen in der Semantik und
    // TalkBack läse eingesammelte Buchstaben weiter vor.
    if (poppedAway) return

    val density = LocalDensity.current
    val tileDp = TileSize * position.scale
    val offsetX = with(density) { position.x.toDp() } - tileDp / 2
    val offsetY = with(density) { position.y.toDp() } - tileDp / 2
    Box(
        modifier = modifier
            .offset(x = offsetX, y = offsetY)
            .size(tileDp)
            .graphicsLayer {
                val factor = HuntTileMorph.scale(inflate.value, exit.value)
                scaleX = factor
                scaleY = factor
                alpha = HuntTileMorph.alpha(exit.value)
            }
            // Der Clip hält die Verläufe im Kreis — der Glanzpunkt sitzt
            // außermittig und ragte sonst an der Kante heraus — und deckelt
            // weiter den Glyphen (siehe Größenrechnung unten).
            .clip(CircleShape)
            .drawBehind {
                val press = HuntTileMorph.pressProgress(inflate.value)
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val lightCenter = Offset(
                    x = size.width * HuntTileMorph.GlossCenterX,
                    y = size.height * HuntTileMorph.GlossCenterY,
                )
                // Grundwäsche mit Tiefe statt flacher Fläche: heller Kern oben
                // links, satter Rand. Im Mittel die bisherigen 0,22 Deckkraft.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = HuntTileMorph.coreAlpha(press)),
                            color.copy(alpha = HuntTileMorph.rimAlpha(press)),
                        ),
                        center = lightCenter,
                        radius = radius * HuntTileMorph.WashRadiusFactor,
                    ),
                    radius = radius,
                    center = center,
                )
                // Innenschatten am Rand, der mit dem Druck zunimmt: die Kachel
                // liest als weiche Kugel, die man eindrückt, nicht als Zoom.
                drawCircle(
                    brush = Brush.radialGradient(
                        HuntTileMorph.ShadeInnerStop to Color.Transparent,
                        1f to color.copy(alpha = HuntTileMorph.shadeAlpha(press)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                // Glanzpunkt: wird beim Drücken schwächer und zieht sich zusammen.
                val glossRadius = radius * HuntTileMorph.glossRadiusFactor(press)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SoftSand.copy(alpha = HuntTileMorph.glossAlpha(press)),
                            SoftSand.copy(alpha = 0f),
                        ),
                        center = lightCenter,
                        radius = glossRadius,
                    ),
                    radius = glossRadius,
                    center = lightCenter,
                )
            }
            .border(width = 3.dp, color = color, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                // Keine Ripple mehr: der Morph *ist* die Druckantwort, zwei
                // gleichzeitige Druck-Rückmeldungen im selben Kreis lesen als
                // Doppelbild.
                indication = null,
                enabled = enabled && present,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Aus dem Kacheldurchmesser abgeleitet statt fest 28sp: der Kreis
        // clippt (siehe .clip oben), also würde ein „Sch" auf der kleinsten
        // 64dp-Kachel ab font_scale 1.3 angeschnitten (28sp × 1.3 × 0.72 × 3
        // ≈ 79dp Vorschub). Gleiches dp-Budget-durch-fontScale-Muster wie
        // WordFrameSizing.wordGlyphSp; GlyphAspect inklusive Headroom von dort.
        val glyphSp = (
            tileDp.value /
                (glyph.length.coerceAtLeast(1) * WordFrameSizing.GlyphAspect * density.fontScale)
            ).coerceAtMost(MaxTileGlyphSp)
        Text(
            text = glyph,
            fontSize = glyphSp.sp,
            color = WarmInk,
        )
    }
}
