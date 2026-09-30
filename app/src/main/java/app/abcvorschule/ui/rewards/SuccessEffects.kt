package app.abcvorschule.ui.rewards

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.AbcMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Der Stern am Ende eines Trainers (PRODUCT_PRINCIPLES §10). Er steigt leicht gedreht
 * auf und federt ein, ein Lichtring läuft aus, vier Glanzfunken blitzen, ein Glanzstreifen
 * zieht über ihn. Dann fliegt er im Bogen mit einer Funkenspur in den Punktestand, wird
 * dabei klein und dreht sich einmal; beim Einschlag funkelt es kurz am Zähler.
 *
 * Bis September 2026 war es ein flacher Stern mit dunkler Kontur, acht bunten Punkten und
 * einem geraden Flug — auf den dunklen Welten wirkte das wie aus einem alten Spiel.
 * Alles hier wird nur in der Zeichenphase gelesen; nichts rekomponiert pro Frame.
 */
@Composable
fun SuccessBurst(
    trigger: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Wohin der Stern fliegt (Punktestand). Ohne Ziel verglimmt er an Ort und Stelle. */
    target: StarCounterAnchor? = null,
    /** Der Stern ist im Punktestand eingeschlagen — jetzt darf die Zahl springen. */
    onLanded: () -> Unit = {},
) {
    if (!trigger) return
    val rise = remember(trigger) { Animatable(0f) }
    val clock = remember(trigger) { Animatable(0f) }
    val flight = remember(trigger) { Animatable(0f) }
    val fade = remember(trigger) { Animatable(1f) }
    val landed = remember(trigger) { Animatable(0f) }
    var origin by remember(trigger) { mutableStateOf<Offset?>(null) }
    var landing by remember(trigger) { mutableStateOf<Offset?>(null) }

    LaunchedEffect(trigger) {
        // Bewusst ohne Haptik: der große Stern am Trainer-Ende kommt oft direkt
        // nach einem Trainer-eigenen Puls (celebrate/success) — zwei Vibrationen
        // hintereinander waren zu viel. Der Chime trägt den Moment allein.
        playSuccessChime()
        // Die Uhr für Ring, Funken und Glanzstreifen läuft nebenher und wird nie abgewartet.
        launch { clock.animateTo(ClockEndS, tween((ClockEndS * 1000).toInt(), easing = LinearEasing)) }
        launch { rise.animateTo(1f, AbcMotion.Settle.spec()) }
        delay(RiseMs)
        delay(StarFlight.HoldMs)
        val to = target?.centerInRoot
        if (to != null && origin != null) {
            landing = to
            AbcSfx.play(Sfx.Whoosh)
            flight.animateTo(1f, tween(FlightMs, easing = FastOutSlowInEasing))
            AbcSfx.play(Sfx.Ding)
            onLanded()
            landed.animateTo(1f, tween(LandSparkMs, easing = AbcMotion.Exit))
        } else {
            // Ohne Ziel: sanft verglimmen, und erst danach weiter — sonst verschwände
            // der Stern mitten in der Bewegung.
            fade.animateTo(0f, tween(AbcMotion.StandardMs))
            onLanded()
        }
        onFinished()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val home = Offset(size.width / 2f, size.height * HomeFraction)
        val t = clock.value
        val f = flight.value
        val r0 = BigRadius.toPx()
        // Weicher Lichtschein hinter dem Aufstieg.
        val bloom = (t / 0.25f).coerceIn(0f, 1f) * (1f - ((t - 1f) / 0.4f).coerceIn(0f, 1f)) * (1f - f)
        if (bloom > 0f) {
            val br = 120.dp.toPx()
            drawCircle(
                Brush.radialGradient(0f to Bloom.copy(alpha = 0.28f * bloom), 1f to Color.Transparent, center = home, radius = br),
                radius = br,
                center = home,
            )
        }
        // Lichtring, der ausläuft.
        val ring = ((t - 0.12f) / 0.6f).coerceIn(0f, 1f)
        if (ring in 0.001f..0.999f) {
            drawCircle(
                color = RingLight.copy(alpha = 0.5f * (1f - ring)),
                radius = (30 + 90 * (1f - (1f - ring) * (1f - ring) * (1f - ring))).dp.toPx(),
                center = home,
                style = Stroke(width = (2.5f * (1f - ring) + 0.5f).dp.toPx()),
            )
        }
        // Vier Glanzfunken, versetzt.
        GlintSpots.forEach { (dx, dy, start) ->
            val p = ((t - start) / 0.5f).coerceIn(0f, 1f)
            val s = kotlin.math.sin(p * PI.toFloat())
            drawGlint(home + Offset(dx.dp.toPx(), dy.dp.toPx()), 11.dp.toPx() * s, s * (1f - f))
        }
        val from = origin
        val to = landing?.let { if (from != null) it - from else null }
        if (landed.value <= 0f) {
            val r = rise.value
            var center = home + Offset(0f, (1f - r) * 40.dp.toPx())
            var radius = r0 * r.coerceIn(0f, 1.2f) * (1f + 0.02f * kotlin.math.sin(t * 4f))
            var rotation = (1f - r) * -26f
            if (to != null && f > 0f) {
                val ctrl = home + Offset(110.dp.toPx(), -10.dp.toPx())
                fun bez(u: Float) = home * ((1 - u) * (1 - u)) + ctrl * (2 * (1 - u) * u) + to * (u * u)
                center = bez(f)
                radius = r0 + (LandedRadius.toPx() - r0) * f
                rotation = f * 360f
                for (i in 1..7) {
                    val u = f - i * 0.06f
                    if (u <= 0f) continue
                    drawGlint(bez(u), (7f - i * 0.6f).dp.toPx(), 0.7f * (1f - i / 8f))
                }
            }
            val shimmer = if (f == 0f && t in 0.6f..1.15f) (t - 0.6f) / 0.55f else -1f
            drawGlowStar(center, radius, rotation, alpha = fade.value, shimmer = shimmer)
        }
        // Einschlag: kurzes Funkeln am Zähler.
        val l = landed.value
        if (to != null && l > 0f && l < 1f) {
            for (i in 0 until 5) {
                val a = i / 5f * 2f * PI.toFloat() + 0.3f
                val d = (8 + 22 * (1f - (1f - l) * (1f - l))).dp.toPx()
                drawGlint(to + Offset(kotlin.math.cos(a) * d, kotlin.math.sin(a) * d), 5.dp.toPx() * (1f - l), 1f - l)
            }
        }
    }
}

/**
 * Wo der Stern aufsteigt: unter dem Lautsprecher. In der Mitte des oberen Drittels (bis
 * September 2026) überdeckte er Fortschritt und Lautsprecher zugleich.
 */
private const val HomeFraction = 0.32f
private val BigRadius = 42.dp
/** Halbe Größe des Sterns im Punktestand (22 dp). */
private val LandedRadius = 11.dp
private const val RiseMs = 520L
private const val FlightMs = 560
private const val LandSparkMs = 320
private const val ClockEndS = 1.6f
private val Bloom = Color(0xFFFFD278)
private val RingLight = Color(0xFFFFECBE)

/** (dx dp, dy dp, Start s) — um den Stern verteilt, nie symmetrisch. */
private val GlintSpots = listOf(
    Triple(-58f, -34f, 0.25f),
    Triple(62f, -18f, 0.42f),
    Triple(-40f, 48f, 0.58f),
    Triple(50f, 44f, 0.75f),
)

private const val SampleRate = 44100

/** Short ascending major arpeggio (C-E-G-C) — a cheerful "ta-da" chime, synthesized on-device. */
fun playSuccessChime() {
    if (AbcSfx.play(Sfx.Chime)) return
    val notes = listOf(523.25, 659.25, 783.99, 1046.50)
    playTone(notes, noteMs = 90)
}

/** One rising scale step per collected trace star (C major, wrapping after an octave). */
fun playStarBlip(step: Int) {
    if (AbcSfx.play(Sfx.Blip, rate = AbcSfx.blipRate(step))) return
    val scale = listOf(523.25, 587.33, 659.25, 698.46, 783.99, 880.0, 987.77, 1046.50)
    val freq = scale[step.coerceAtLeast(0) % scale.size]
    playTone(listOf(freq), noteMs = 70, gapMs = 0)
}

/**
 * Short low tone for negative/blocked feedback (a locked node, a miss) so a tap
 * without German TTS is never a silent no-op.
 */
fun playBlockedBlip() {
    if (AbcSfx.play(Sfx.Blocked)) return
    playTone(listOf(220.0), noteMs = 120)
}

/**
 * [gapMs] defaults to buildArpeggio's original spacing so routing the existing
 * success chime through this shared path does not change how it sounds.
 */
private fun playTone(freqsHz: List<Double>, noteMs: Int, gapMs: Int = 15) {
    runCatching {
        val samples = buildArpeggio(freqsHz, noteMs = noteMs, gapMs = gapMs)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_MEDIA wie der ClipPlayer, nicht ASSISTANCE_SONIFICATION:
                    // das landete auf STREAM_SYSTEM und war im Vibrations-/Stummmodus
                    // lautlos, während die Sprachclips weiterliefen. Der
                    // Blocked-Blip ist aber gerade die Zusage „ein Tipp ist nie ein
                    // stummes No-Op" — er muss auf derselben Spur liegen wie die
                    // Sprache, die das Kind ohnehin hört.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        // Ab hier hängt ein nativer Track dran: wirft write/play, verschluckt das
        // äußere runCatching die Exception und der Track bliebe für immer
        // alloziert — der Marker-Callback, der sonst freigibt, kommt dann nie.
        runCatching {
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    runCatching { track.release() }
                }
                override fun onPeriodicNotification(t: AudioTrack?) = Unit
            })
            track.play()
        }.onFailure { runCatching { track.release() } }
    }
}

private fun buildArpeggio(freqsHz: List<Double>, noteMs: Int = 90, gapMs: Int = 15): ShortArray {
    val noteSamples = SampleRate * noteMs / 1000
    val gapSamples = SampleRate * gapMs / 1000
    val out = ShortArray(freqsHz.size * (noteSamples + gapSamples))
    var idx = 0
    freqsHz.forEachIndexed { i, freq ->
        val amplitude = if (i == freqsHz.lastIndex) 0.55 else 0.4
        for (n in 0 until noteSamples) {
            val t = n.toDouble() / SampleRate
            val sample = sin(2 * PI * freq * t) * amplitude * envelopeAt(n, noteSamples)
            out[idx++] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        idx += gapSamples
    }
    return out
}

/** Soft attack/release so notes don't click. */
private fun envelopeAt(n: Int, total: Int): Double {
    val attack = (total * 0.08).toInt().coerceAtLeast(1)
    val release = (total * 0.3).toInt().coerceAtLeast(1)
    return when {
        n < attack -> n.toDouble() / attack
        n > total - release -> (total - n).toDouble() / release
        else -> 1.0
    }
}
