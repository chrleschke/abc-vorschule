package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.AbcSfx
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.Sfx
import app.abcvorschule.ui.rewards.StarFlight
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.LeafGreenLight
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SkyBlueLight
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.world.rememberReduceMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Matches AbcDimens.kidTouch (the app-wide minimum touch target for 4-6-year-olds)
// so that even the smallest scattered tile (scale 0.8, see SymbolHuntLayout) renders
// at 64dp — comfortably above the design spec's 56dp hit-box floor.
private val TileSize = AbcDimens.kidTouch

/** Obergrenze des Kachel-Glyphen — die bisherige feste Größe, jetzt nur noch der
 * Deckel: einzelne Buchstaben auf jeder Kachel bleiben exakt wie gehabt, nur
 * Mehrzeichen-Symbole („Sch") und große Schriftskalierungen schrumpfen darunter. */
private const val MaxTileGlyphSp = 28f

// Die Ringe der Blasen in der Tiefsee (PRODUCT_PRINCIPLES §10, „Nachtwelten"). Auf dem
// dunklen Meer tragen die **hellen** Stufen die 3:1 für UI-Bauteile, nicht mehr die
// abgedunkelten des Papiergrunds: gegen das Meer an der hellsten Stelle (#0F5068) liegt
// der schwächste Ring noch bei 3.9:1, weiter unten deutlich darüber. Die Farben haben
// jetzt eine Aufgabe: jede eingefangene Blase wird zu einer Perle in ihrer Ringfarbe
// und fliegt in die Herzmuschel. Gold ist bewusst ein gedämpftes Sandgold, nicht
// StarGold — Gold bleibt die Sternbelohnung.
internal val TilePalette = listOf(
    Color(0xFFF0A58A), // Koralle, hell
    SkyBlueLight,
    Color(0xFFE6C46A), // Sandgold
    LeafGreenLight,
)

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
    // Solange die Kugeln nach einem Fehltipp an ihre neuen Plätze hüpfen, nimmt das
    // Feld keine Tipps an (HuntShuffleHop) — Bremse gegen Durchtippen, keine Strafe.
    var shuffling by remember(roundKey) { mutableStateOf(false) }
    LaunchedEffect(roundKey, state.seed) {
        if (!shuffling) return@LaunchedEffect
        delay(HuntShuffleHop.LockMs.toLong())
        shuffling = false
    }
    val haptics = LocalAbcHaptics.current

    // Herzmuschel statt Batterie: jede eingefangene Blase fliegt als Perle in ihrer
    // Ringfarbe hinein (CockleShell). Gelandete Perlen zählen, fliegende noch nicht.
    val cockle = remember(roundKey) { CockleAnchor() }
    val openness = rememberCockleOpenness(roundKey)
    val landed = remember(roundKey) { mutableStateListOf<Color>() }
    val flights = remember(roundKey) { mutableStateListOf<PearlFlight>() }
    var lastTouch by remember(roundKey) { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val currentRound = rememberUpdatedState(roundKey)
    val reduceMotion = rememberReduceMotion()

    fun launchPearl(from: Offset, color: Color, full: Boolean) {
        val flight = PearlFlight(from = from, slot = landed.size + flights.size, color = color, round = roundKey)
        flights += flight
        scope.launch {
            launch { openness.animateTo(1f, tween(AbcMotion.ShortMs, easing = AbcMotion.Enter)) }
            flight.progress.animateTo(1f, tween(PearlFlight.FlightMs, easing = AbcMotion.Enter))
            // Runde inzwischen gewechselt (Chevron): kein Klang in die neue Runde hinein.
            if (currentRound.value != flight.round) return@launch
            flights.remove(flight)
            landed += flight.color
            AbcSfx.play(Sfx.Snap)
            if (full) return@launch
            // Offen lassen, solange noch eine Perle unterwegs ist; dann federnd zu.
            delay(PearlFlight.CloseAfterMs)
            // Nicht zuklappen, wenn die Muschel inzwischen voll ist: zwei schnelle Treffer,
            // und der erste klappte sonst die volle Muschel während der Feier zu.
            if (flights.isEmpty() && !batteryFull) openness.animateTo(0f, AbcMotion.Settle.spec())
        }
    }

    fun handleTap(instanceId: Int, from: Offset) {
        if (resolved || batteryFull || shuffling) return
        lastTouch = System.nanoTime()
        val tapped = state.tiles.firstOrNull { it.instanceId == instanceId } ?: return
        onSpeak(pack.atoms[tapped.atomId]?.lemma ?: tapped.atomId)
        val result = SymbolHuntProgress.tap(state, instanceId)
        if (result.state.seed != state.seed) {
            shuffling = true
            AbcSfx.play(Sfx.Shuffle)
        }
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
            SymbolHuntTapOutcome.Collected -> {
                haptics.tick()
                AbcSfx.play(Sfx.Pop)
                launchPearl(from, TilePalette[instanceId % TilePalette.size], full = false)
            }
            SymbolHuntTapOutcome.RoundComplete -> {
                AbcSfx.play(Sfx.Pop)
                launchPearl(from, TilePalette[instanceId % TilePalette.size], full = true)
                batteryFull = true
                haptics.celebrate()
            }
            SymbolHuntTapOutcome.Ignored -> Unit
        }
    }

    val fieldAlpha by animateFloatAsState(
        targetValue = if (batteryFull) 0f else 1f,
        animationSpec = tween(durationMillis = AbcMotion.LongMs),
        label = "hunt_field_fade",
    )
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = AbcMotion.QuickMs),
        label = "hunt_lock_opacity",
    )

    // Auto-proceed: the battery filling up IS the success signal, so a "Weiter"
    // tap only added a dead end for a child who cannot read the button. The delay
    // sits in front of onResult because reporting the result starts the spoken
    // success phase, which must not talk over the celebration.
    // Längere Pause: die Muschel lugt halb auf, damit das Kind sieht, wie viele Perlen
    // es schon hat (Nutzer-Wunsch, §10). Jeder Tipp setzt die Uhr zurück.
    LaunchedEffect(roundKey, lastTouch, batteryFull, resolved, interactionLocked) {
        // Bei „Bewegung reduzieren" lugt sie nicht — die Welt steht dann still (§10).
        // Und nicht, solange die Ansage noch läuft: die Pause zählt erst ab der Freigabe.
        if (batteryFull || resolved || reduceMotion || interactionLocked) return@LaunchedEffect
        try {
            var wait = PearlFlight.PeekAfterIdleMs
            while (true) {
                delay(wait)
                wait = PearlFlight.PeekRepeatMs
                if (flights.isNotEmpty() || openness.value > 0.01f) continue
                openness.animateTo(CockleGeometry.PeekOpenness, tween(PearlFlight.PeekOpenMs, easing = AbcMotion.Linger))
                delay(PearlFlight.PeekHoldMs)
                openness.animateTo(0f, tween(PearlFlight.PeekCloseMs, easing = AbcMotion.Exit))
            }
        } finally {
            // Ein Tipp mitten im Lugen: zuklappen — außer eine Perle fliegt gerade,
            // dann gehört die Öffnung ihr.
            if (flights.isEmpty() && !batteryFull && openness.value in 0.01f..0.5f) {
                scope.launch { openness.animateTo(0f, tween(PearlFlight.PeekCloseMs, easing = AbcMotion.Exit)) }
            }
        }
    }

    LaunchedEffect(batteryFull) {
        if (!batteryFull) return@LaunchedEffect
        delay(HuntCelebration.HoldMs)
        onResult(true, false, listOf(round.targetAtomId))
    }

    val overlay = remember(roundKey) { OverlayOrigin() }
    Box(modifier = modifier.onGloballyPositioned { overlay.topLeft = it.positionInRoot() }) {
    ExerciseStage(
        modifier = Modifier.fillMaxSize(),
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
                    enabled = !batteryFull && !interactionLocked && !shuffling,
                    onTap = { id, from -> handleTap(id, from) },
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha * interactionOpacity),
                )
            }
        },
        answers = {
            // Die Muschel zeigt nur gelandete Perlen: nach „Zeig mir" bleibt der echte
            // Stand stehen (eine volle Muschel wäre eine Feier für etwas, das das Kind
            // nicht geschafft hat — dieselbe Regel wie früher bei der Batterie).
            CockleShell(
                total = state.targetHitCount,
                pearls = landed,
                openness = openness.asState(),
                celebrate = batteryFull,
                anchor = cockle,
                // Gehört zum Feld: solange die Ansage läuft, ruht sie mit ihm.
                modifier = Modifier.alpha(interactionOpacity),
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
    // Die fliegenden Perlen, über der ganzen Bühne: vom getippten Buchstaben in die Muschel.
    Canvas(Modifier.matchParentSize()) {
        val total = state.targetHitCount
        flights.forEach { f ->
            val to = cockle.slotInRoot(f.slot, total)
            val p = f.progress.value
            val at = f.from + StarFlight.offset(f.from, to, p) - overlay.topLeft
            val endRadius = CockleGeometry.slotRadius(total) * cockle.widthPx
            val startRadius = PearlFlight.StartRadius.toPx()
            val radius = startRadius + (endRadius - startRadius) * p
            drawPearl(at, radius, f.color)
        }
    }
    }
}

@Composable
private fun SymbolHuntField(
    /** Keyt die Kacheln (siehe unten) — ein SymbolHuntSpec trägt mehrere Runden. */
    roundKey: String,
    state: SymbolHuntState,
    initialTiles: List<SymbolHuntTile>,
    pack: ContentPack,
    enabled: Boolean = true,
    onTap: (Int, Offset) -> Unit,
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
                    shuffleSeed = state.seed,
                    instanceId = tile.instanceId,
                    color = TilePalette[tile.instanceId % TilePalette.size],
                    present = tile.instanceId in presentIds,
                    enabled = enabled,
                    onTap = { center -> onTap(tile.instanceId, center) },
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
 * Woher und wohin eine Kugel beim Neu-Mischen hüpft. Wie [HuntPressLatch] bewusst
 * kein State: gelesen wird nur in der Layout- und Zeichenphase, getrieben vom
 * Animatable daneben.
 */
private class HuntFlight(position: HuntTilePosition, var seed: Long) {
    var from = position
    var to = position

    /**
     * Neu gemischt, aber der Flug-Effekt lief noch nicht an: bis dahin steht die
     * Kugel am Startpunkt, sonst blitzte sie einen Frame lang schon am Ziel auf.
     */
    var pending = false

    fun x(t: Float) = HuntShuffleHop.x(from.x, to.x, t)
    fun scale(t: Float) = HuntShuffleHop.scale(from.scale, to.scale, t)

    /** Wo die Kugel bei [t] steht, als Startpunkt für einen Flug, der sie im Flug erwischt. */
    fun at(t: Float, hopPx: Float) = HuntTilePosition(
        x = x(t),
        y = HuntShuffleHop.y(from.y, to.y, t, hopPx),
        scale = scale(t),
        colorIndex = to.colorIndex,
    )
}

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
    /** Wechselt bei jedem Fehltipp — dann hüpft die Kugel an [position]. */
    shuffleSeed: Long,
    instanceId: Int,
    color: Color,
    present: Boolean,
    enabled: Boolean,
    onTap: (centerInRoot: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerInRoot = remember { TileCenter() }
    val inflate = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val latch = remember { HuntPressLatch() }
    var poppedAway by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hopPx = with(density) { (TileSize * HuntShuffleHop.HopHeightFraction).toPx() }
    // 1 = gelandet. Startet gelandet: eine Runde, die geladen wird, animiert nichts.
    val hop = remember { Animatable(1f) }
    val flight = remember { HuntFlight(position, shuffleSeed) }
    // In der Komposition, nicht im Effekt: ändert das Feld nur seine Größe (erste
    // Messung, Drehen), muss die Kugel im selben Layout-Durchgang am neuen Platz
    // stehen. Stand das hier im Effekt, blieb sie am zuerst gemessenen Platz liegen —
    // der Effekt läuft nach dem Layout, und danach stieß nichts ein neues an
    // (SymbolHuntTileBoundsTest: Kugel 10 dp über den Rand).
    if (shuffleSeed != flight.seed) {
        // Neu gemischt: Start ist, wo die Kugel gerade ist — auch mitten im Flug.
        flight.from = flight.at(if (flight.pending) 0f else hop.value, hopPx)
        flight.seed = shuffleSeed
        flight.pending = true
    }
    flight.to = position
    LaunchedEffect(shuffleSeed) {
        if (!flight.pending) return@LaunchedEffect
        hop.snapTo(0f)
        flight.pending = false
        delay(HuntShuffleHop.staggerMs(instanceId).toLong())
        hop.animateTo(1f, tween(HuntShuffleHop.FlightMs, easing = AbcMotion.Enter))
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            latch.touched = true
            // Anfassen: kurze Feder auf +6 %, mit leichtem Nachwippen.
            inflate.animateTo(
                targetValue = HuntTileMorph.PressPuff,
                animationSpec = AbcMotion.Bouncy.spec(),
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
                animationSpec = AbcMotion.Pop.spec(),
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

    val tileDp = TileSize * position.scale
    val tilePx = with(density) { tileDp.toPx() }
    Box(
        modifier = modifier
            // Gelegt wird in der Größe des Ziels, geflogen in der Layout-Phase: der
            // Bogen liest den Hüpf-Fortschritt, ohne 450 ms lang zu rekomponieren.
            .offset {
                val t = if (flight.pending) 0f else hop.value
                IntOffset(
                    x = (flight.x(t) - tilePx / 2).roundToInt(),
                    y = (HuntShuffleHop.y(flight.from.y, flight.to.y, t, hopPx) - tilePx / 2).roundToInt(),
                )
            }
            .size(tileDp)
            .onGloballyPositioned { centerInRoot.value = it.boundsInRoot().center }
            .graphicsLayer {
                val growth = flight.scale(if (flight.pending) 0f else hop.value) / position.scale
                val factor = HuntTileMorph.scale(inflate.value, exit.value) * growth
                scaleX = factor
                scaleY = factor
                alpha = HuntTileMorph.alpha(exit.value)
            }
            // Der Clip hält die Verläufe im Kreis — der Glanzpunkt sitzt
            // außermittig und ragte sonst an der Kante heraus — und deckelt
            // weiter den Glyphen (siehe Größenrechnung unten).
            // Leuchten ums Wasser herum, vor dem Clip: es darf über den Kreis hinaus.
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.62f to BubbleGlow,
                        1f to Color.Transparent,
                        radius = size.minDimension * 0.72f,
                    ),
                    radius = size.minDimension * 0.72f,
                )
            }
            .clip(CircleShape)
            .drawBehind {
                val press = HuntTileMorph.pressProgress(inflate.value)
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val lightCenter = Offset(
                    x = size.width * HuntTileMorph.GlossCenterX,
                    y = size.height * HuntTileMorph.GlossCenterY,
                )
                // Eine Luftblase im Meer: heller, fast weißer Kern — die Licht-Insel,
                // auf der der Buchstabe in Tinte steht (≥ 10:1) — und ein Rand, durch
                // den das Meer schimmert. Der Druck macht den Rand etwas satter.
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to BubbleCore,
                        0.45f to BubbleMid,
                        0.78f to BubbleEdge.copy(alpha = 0.84f - 0.08f * press),
                        1f to BubbleEdge.copy(alpha = 0.62f),
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
                onClick = { onTap(centerInRoot.value) },
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

/** Root-Mittelpunkt einer Blase, gesetzt beim Layout, gelesen beim Tipp. Kein State. */
private class TileCenter { var value: Offset = Offset.Zero }

/** Wo das Flug-Overlay im Root liegt. Kein State: gelesen nur in der Zeichenphase. */
private class OverlayOrigin { var topLeft: Offset = Offset.Zero }

private val BubbleCore = Color(0xFFFFFDF6)
private val BubbleMid = Color(0xFFF2F0E8)
private val BubbleEdge = Color(0xFFECF2F0)
private val BubbleGlow = Color(0x59A0DCEB)
