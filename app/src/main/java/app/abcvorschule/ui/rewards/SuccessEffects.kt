package app.abcvorschule.ui.rewards

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.IconStar
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SuccessBurst(
    trigger: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!trigger) return
    val scale = remember(trigger) { Animatable(0.4f) }
    val alpha = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        playSuccessChime()
        coroutineScope {
            launch {
                scale.animateTo(
                    targetValue = 1.3f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            launch { alpha.animateTo(1f, tween(180)) }
        }
        delay(550)
        // Wait for the exit animation to fully finish before the caller advances —
        // otherwise the composable is torn down mid-shrink and the star just vanishes.
        coroutineScope {
            launch { scale.animateTo(0.7f, tween(320)) }
            launch { alpha.animateTo(0f, tween(320)) }
        }
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.34f)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center,
        ) {
            IconStar(
                tint = MaterialTheme.colorScheme.primary,
                size = 84.dp,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value),
            )
        }
    }
}

private const val SampleRate = 44100

/** Short ascending major arpeggio (C-E-G-C) — a cheerful "ta-da" chime, synthesized on-device. */
fun playSuccessChime() {
    val notes = listOf(523.25, 659.25, 783.99, 1046.50)
    playTone(notes, noteMs = 90)
}

/** One rising scale step per collected trace star (C major, wrapping after an octave). */
fun playStarBlip(step: Int) {
    val scale = listOf(523.25, 587.33, 659.25, 698.46, 783.99, 880.0, 987.77, 1046.50)
    val freq = scale[step.coerceAtLeast(0) % scale.size]
    playTone(listOf(freq), noteMs = 70)
}

private fun playTone(freqsHz: List<Double>, noteMs: Int) {
    runCatching {
        val samples = buildArpeggio(freqsHz, noteMs = noteMs, gapMs = 0)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
        track.write(samples, 0, samples.size)
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                runCatching { track.release() }
            }
            override fun onPeriodicNotification(t: AudioTrack?) = Unit
        })
        track.play()
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
