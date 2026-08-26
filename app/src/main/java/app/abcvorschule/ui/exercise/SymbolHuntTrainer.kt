package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
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
    // so surviving tiles keep their position/color across a correct tap.
    val initialTileCount = remember(roundKey) { state.tiles.size }
    var resolved by remember(roundKey) { mutableStateOf(false) }
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
                    state = state,
                    initialTileCount = initialTileCount,
                    pack = pack,
                    enabled = !batteryFull && !interactionLocked,
                    onTap = ::handleTap,
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha * interactionOpacity),
                )
            }
        },
        answers = {
            SymbolHuntBattery(collected = state.collected, total = state.targetHitCount, celebrate = batteryFull)
            if (SymbolHuntProgress.resolveAvailable(state) && !resolved && !batteryFull) {
                AbcResolveButton(
                    onClick = {
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
    state: SymbolHuntState,
    initialTileCount: Int,
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
        val positions = remember(state.seed, initialTileCount, widthPx, heightPx, tileSizePx) {
            SymbolHuntLayout.scatter(state.seed, initialTileCount, widthPx, heightPx, tileSizePx)
        }
        state.tiles.forEach { tile ->
            // Indexed by the tile's stable instanceId (its index in the original,
            // pre-shrink tile list) rather than its position in the current
            // (shrinking) list, so a surviving tile keeps the same slot/color.
            val position = positions.getOrNull(tile.instanceId) ?: return@forEach
            val tileDp = TileSize * position.scale
            val offsetX = with(density) { position.x.toDp() } - tileDp / 2
            val offsetY = with(density) { position.y.toDp() } - tileDp / 2
            val color = TilePalette[tile.instanceId % TilePalette.size]
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(tileDp)
                    // Without this clip, the tile's click/ripple bounds default to the
                    // Box's full square size instead of the CircleShape drawn below —
                    // the touch feedback flashes as a square and, where scattered tiles
                    // sit close together, can spill visibly into a neighbouring tile.
                    .clip(CircleShape)
                    .background(color = color.copy(alpha = 0.22f), shape = CircleShape)
                    .border(width = 3.dp, color = color, shape = CircleShape)
                    .clickable(enabled = enabled) { onTap(tile.instanceId) }
                    .testTag("hunt_tile_${tile.instanceId}"),
                contentAlignment = Alignment.Center,
            ) {
                val glyph = pack.atoms[tile.atomId]?.display ?: tile.atomId
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
    }
}

@Composable
private fun SymbolHuntBattery(
    collected: Int,
    total: Int,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
) {
    // The infinite pulse transition is only ever visible while celebrating (the
    // very end of a round), so it's only created/started then — otherwise it
    // would tick continuously for the entire round's lifetime for no visible
    // effect, wasting battery/CPU.
    val glow = if (celebrate) {
        val infiniteTransition = rememberInfiniteTransition(label = "battery_glow")
        val animatedGlow by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "battery_glow_value",
        )
        animatedGlow
    } else {
        1f
    }
    Row(
        modifier = modifier.fillMaxWidth().testTag("hunt_battery"),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            val filled = i < collected
            val empty = !celebrate && !filled
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 44.dp)
                    .alpha(if (celebrate) glow else 1f)
                    .background(
                        color = when {
                            // Deviates from the literal StarGold mapping for the same
                            // reason as TilePalette above: StarGold alone is only ~1.85:1
                            // against Cream, well under the 3:1 floor for this filled
                            // rectangle. StarGoldDeep clears it (~3.29:1).
                            celebrate -> StarGoldDeep
                            filled -> LeafGreen
                            else -> CreamElevated
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    .let {
                        // CreamElevated on Cream is only ~1.24:1 — an empty cell would be
                        // nearly invisible against the page without an outline. WarmMuted
                        // at alpha 0.9f clears 3:1 against both CreamElevated (3.11:1, the
                        // fill it borders) and Cream (3.86:1, the page around it), same
                        // value as the empty slot/peg borders above. Filled/celebrating
                        // cells carry enough of their own contrast and stay bare.
                        if (empty) {
                            it.border(
                                width = 3.dp,
                                color = WarmMuted.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(6.dp),
                            )
                        } else {
                            it
                        }
                    },
            )
        }
    }
}
