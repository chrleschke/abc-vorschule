package app.abcvorschule.ui.rewards

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun SuccessBurst(
    trigger: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!trigger) return
    var visible by remember(trigger) { mutableStateOf(true) }
    val emoji = remember(trigger) { listOf("⭐", "🎉", "✨", "🌟").random() }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1.4f else 0.6f,
        animationSpec = tween(450),
        label = "burst",
    )

    LaunchedEffect(trigger) {
        playSuccessTone()
        delay(500)
        visible = false
        delay(120)
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = emoji, fontSize = 64.sp, modifier = Modifier.scale(scale))
    }
}

fun playSuccessTone() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        val type = listOf(
            ToneGenerator.TONE_PROP_ACK,
            ToneGenerator.TONE_PROP_BEEP,
            ToneGenerator.TONE_PROP_PROMPT,
        )[Random.nextInt(3)]
        tone.startTone(type, 180)
        // Release after tone window
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { tone.release() }
        }, 250)
    }
}

@Composable
fun RememberToneCleanup() {
    DisposableEffect(Unit) {
        onDispose { }
    }
}
