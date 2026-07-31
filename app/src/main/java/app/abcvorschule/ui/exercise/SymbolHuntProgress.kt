package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntRound
import kotlin.random.Random

/** One collectible/decoy instance in the scatter field. Distinct instances can
 * share the same underlying atom — a distractor letter repeats as several tiles
 * when only one or two other letters are known yet (design doc §3). */
data class SymbolHuntTile(val instanceId: Int, val atomId: String, val isTarget: Boolean)

data class SymbolHuntState(
    val tiles: List<SymbolHuntTile>,
    val targetHitCount: Int,
    val collected: Int = 0,
    val consecutiveMisses: Int = 0,
    val reportedMissThisRound: Boolean = false,
    val seed: Long = 0L,
)

enum class SymbolHuntTapOutcome { Collected, RoundComplete, Miss, MissAlreadyReported, Ignored }

data class SymbolHuntTapResult(val state: SymbolHuntState, val outcome: SymbolHuntTapOutcome)

/**
 * Tap handling for the Buchstaben-/Silben-Jagd battery game (design doc §5): a
 * wrong tap reshuffles the field (bumps [SymbolHuntState.seed]) without losing
 * battery progress, and only the first miss of a round is reported for
 * adaptivity. Resolve unlocks after [ResolveThreshold] *consecutive* misses —
 * resets on any correct tap, unlike the cumulative off-road count in the
 * Spurensucher (LetterTraceTrainer); the design doc explicitly calls for
 * "aufeinanderfolgende" (consecutive) misses here, reusing only the threshold
 * number.
 */
object SymbolHuntProgress {
    const val ResolveThreshold = ResolveGate.Threshold

    // XOR salt so the distractor-selection shuffle is a distinct deterministic
    // stream from the scatter-layout shuffle, which reuses the same base `seed`
    // directly (see SymbolHuntField). Both derive from the same round seed, so
    // both stay fully deterministic for a given round; the salt just keeps them
    // from picking the exact same permutation as each other.
    private const val DistractorShuffleSalt = 0x5EEDL

    fun initialState(round: SymbolHuntRound, seed: Long): SymbolHuntState {
        val (hitCount, distractorCount) = requireNotNull(
            SymbolHuntDerivation.tileCounts(round.distractorPool.size),
        ) { "SymbolHuntRound ${round.targetAtomId} has an empty distractor pool" }
        val hits = (0 until hitCount).map { i ->
            SymbolHuntTile(instanceId = i, atomId = round.targetAtomId, isTarget = true)
        }
        // Shuffle the pool deterministically per round instead of always taking its
        // first `distractorCount` entries (curriculum-introduction order) — without
        // this, every hunt from roughly lesson 3 onward showed the identical first-N
        // distractors, since the pool only ever grows. Same round + same seed always
        // produces the same shuffle (design relies on determinism for testability).
        val shuffledPool = round.distractorPool.shuffled(Random(seed xor DistractorShuffleSalt))
        val distractors = (0 until distractorCount).map { i ->
            val atomId = shuffledPool[i % shuffledPool.size]
            SymbolHuntTile(instanceId = hitCount + i, atomId = atomId, isTarget = false)
        }
        return SymbolHuntState(tiles = hits + distractors, targetHitCount = hitCount, seed = seed)
    }

    fun tap(state: SymbolHuntState, instanceId: Int): SymbolHuntTapResult {
        val tile = state.tiles.firstOrNull { it.instanceId == instanceId }
            ?: return SymbolHuntTapResult(state, SymbolHuntTapOutcome.Ignored)
        if (tile.isTarget) {
            val remaining = state.tiles.filter { it.instanceId != instanceId }
            val collected = state.collected + 1
            val next = state.copy(tiles = remaining, collected = collected, consecutiveMisses = 0)
            val outcome = if (collected >= state.targetHitCount) {
                SymbolHuntTapOutcome.RoundComplete
            } else {
                SymbolHuntTapOutcome.Collected
            }
            return SymbolHuntTapResult(next, outcome)
        }
        val alreadyReported = state.reportedMissThisRound
        val next = state.copy(
            consecutiveMisses = state.consecutiveMisses + 1,
            reportedMissThisRound = true,
            seed = state.seed + 1,
        )
        val outcome = if (alreadyReported) SymbolHuntTapOutcome.MissAlreadyReported else SymbolHuntTapOutcome.Miss
        return SymbolHuntTapResult(next, outcome)
    }

    fun resolveAvailable(state: SymbolHuntState): Boolean = state.consecutiveMisses >= ResolveThreshold

    /** Resolve: auto-fill the remaining battery segments and clear the field. */
    fun resolve(state: SymbolHuntState): SymbolHuntState =
        state.copy(tiles = emptyList(), collected = state.targetHitCount)
}
