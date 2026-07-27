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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

private val TileSize = 64.dp
private val TilePalette = listOf(SoftMint, SoftCoral, SoftSky, SoftGold, SoftSand)

/**
 * Buchstaben-/Silben-Jagd: tiles scatter across the whole task area under a
 * fixed speaker strip (deliberate exception to Prinzip 9 — design doc §4), the
 * battery lives in the answer area (also an exception). A wrong tap reshuffles
 * without losing battery progress; the battery-full moment gates on a local
 * "Weiter" tap before handing off to the shared success pipeline (design doc §5).
 */
@Composable
fun SymbolHuntTrainer(
    round: SymbolHuntRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.targetAtomId}-${round.mode}"
    var state by remember(roundKey) {
        mutableStateOf(SymbolHuntProgress.initialState(round, seed = roundKey.hashCode().toLong()))
    }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var batteryFull by remember(roundKey) { mutableStateOf(false) }

    fun handleTap(instanceId: Int) {
        if (resolved || batteryFull) return
        val tapped = state.tiles.firstOrNull { it.instanceId == instanceId } ?: return
        onSpeak(pack.atoms[tapped.atomId]?.lemma ?: tapped.atomId)
        val result = SymbolHuntProgress.tap(state, instanceId)
        state = result.state
        when (result.outcome) {
            SymbolHuntTapOutcome.Miss -> onResult(false, false, listOf(round.targetAtomId))
            SymbolHuntTapOutcome.RoundComplete -> batteryFull = true
            SymbolHuntTapOutcome.Collected,
            SymbolHuntTapOutcome.MissAlreadyReported,
            SymbolHuntTapOutcome.Ignored,
            -> Unit
        }
    }

    val fieldAlpha by animateFloatAsState(
        targetValue = if (batteryFull) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "hunt_field_fade",
    )

    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            if (!resolved) {
                SymbolHuntField(
                    state = state,
                    pack = pack,
                    onTap = ::handleTap,
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha),
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
            if (batteryFull) {
                AbcContinueButton(
                    onClick = { onResult(true, false, listOf(round.targetAtomId)) },
                    centered = true,
                )
            }
        },
    )
}

@Composable
private fun SymbolHuntField(
    state: SymbolHuntState,
    pack: ContentPack,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val positions = remember(state.seed, state.tiles.size, widthPx, heightPx) {
            SymbolHuntLayout.scatter(state.seed, state.tiles.size, widthPx, heightPx)
        }
        state.tiles.forEachIndexed { index, tile ->
            val position = positions.getOrNull(index) ?: return@forEachIndexed
            val tileDp = TileSize * position.scale
            val offsetX = with(density) { position.x.toDp() } - tileDp / 2
            val offsetY = with(density) { position.y.toDp() } - tileDp / 2
            val color = TilePalette[index % TilePalette.size]
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(tileDp)
                    .background(color = color.copy(alpha = 0.22f), shape = CircleShape)
                    .border(width = 3.dp, color = color, shape = CircleShape)
                    .clickable { onTap(tile.instanceId) }
                    .testTag("hunt_tile_${tile.instanceId}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pack.atoms[tile.atomId]?.display ?: tile.atomId,
                    fontSize = 28.sp,
                    color = SoftSand,
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
    val infiniteTransition = rememberInfiniteTransition(label = "battery_glow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "battery_glow_value",
    )
    Row(
        modifier = modifier.fillMaxWidth().testTag("hunt_battery"),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            val filled = i < collected
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 44.dp)
                    .alpha(if (celebrate) glow else 1f)
                    .background(
                        color = when {
                            celebrate -> SoftGold
                            filled -> SoftMint
                            else -> NightElevated
                        },
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
        }
    }
}
