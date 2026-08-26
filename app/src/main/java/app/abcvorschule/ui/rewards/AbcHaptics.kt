package app.abcvorschule.ui.rewards

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Haptik-Vokabular der App. Vier Verben, damit jede Stelle dieselbe Sprache
 * spricht: tick = kleiner Sammel-Erfolg, success = Aufgabe richtig,
 * celebrate = Lektions-/Batterie-Feier, nudge = sanfte Korrektur.
 * Haptik ergänzt den Ton (Blips/Chime), sie ersetzt ihn nie.
 */
interface AbcHaptics {
    fun tick()
    fun success()
    fun celebrate()
    fun nudge()
}

enum class HapticVerb { Tick, Success, Celebrate, Nudge }

/**
 * Reine Muster-Definition, getrennt vom Vibrator, damit sie unit-testbar ist.
 * timings/amplitudes sind createWaveform-kompatibel (erste Zelle ist ein Puls,
 * kein Delay — daher ungerade Länge: puls[,pause,puls...]).
 */
object HapticPatterns {
    fun timingsFor(verb: HapticVerb): LongArray = when (verb) {
        HapticVerb.Tick -> longArrayOf(25)
        HapticVerb.Success -> longArrayOf(45)
        HapticVerb.Celebrate -> longArrayOf(45, 90, 45, 90, 90)
        HapticVerb.Nudge -> longArrayOf(20)
    }

    fun amplitudesFor(verb: HapticVerb): IntArray = when (verb) {
        HapticVerb.Tick -> intArrayOf(180)
        HapticVerb.Success -> intArrayOf(220)
        HapticVerb.Celebrate -> intArrayOf(150, 0, 190, 0, 255)
        HapticVerb.Nudge -> intArrayOf(90)
    }

    /**
     * Delay-first-Form für `createWaveform`: führende 0, danach unsere
     * Puls/Pause-Paare. Auch der Fallback ohne Amplitudensteuerung läuft
     * hierüber — `createOneShot(timings.sum(), …)` hätte dort die Pausen als
     * Vibrationszeit mitgezählt und aus dem Dreifachpuls von `celebrate` ein
     * durchgehendes 360-ms-Brummen gemacht.
     */
    fun waveformTimingsFor(verb: HapticVerb): LongArray =
        longArrayOf(0, *timingsFor(verb))
}

private class VibratorAbcHaptics(private val vibrator: Vibrator) : AbcHaptics {
    override fun tick() = play(HapticVerb.Tick)
    override fun success() = play(HapticVerb.Success)
    override fun celebrate() = play(HapticVerb.Celebrate)
    override fun nudge() = play(HapticVerb.Nudge)

    private fun play(verb: HapticVerb) {
        runCatching {
            val timings = HapticPatterns.timingsFor(verb)
            val amplitudes = HapticPatterns.amplitudesFor(verb)
            val effect = if (vibrator.hasAmplitudeControl()) {
                if (timings.size == 1) {
                    VibrationEffect.createOneShot(timings[0], amplitudes[0])
                } else {
                    // createWaveform(timings, amplitudes, -1) erwartet Delay-first;
                    // unsere Muster sind Puls-first, daher führende 0 einfügen.
                    VibrationEffect.createWaveform(
                        longArrayOf(0, *timings),
                        intArrayOf(0, *amplitudes),
                        -1,
                    )
                }
            } else {
                // Ohne Amplitudensteuerung bleibt wenigstens der Rhythmus: die
                // Waveform-Form trennt Puls und Pause. Summiert man sie zu einem
                // OneShot, wird aus `celebrate` ein Dauerbrummen — und damit ein
                // anderes Haptik-Vokabel als das, das die Stelle meint.
                VibrationEffect.createWaveform(
                    HapticPatterns.waveformTimingsFor(verb),
                    -1,
                )
            }
            vibrator.vibrate(effect)
        }
    }
}

private object NoOpAbcHaptics : AbcHaptics {
    override fun tick() = Unit
    override fun success() = Unit
    override fun celebrate() = Unit
    override fun nudge() = Unit
}

val LocalAbcHaptics = compositionLocalOf<AbcHaptics> { NoOpAbcHaptics }

@Composable
fun rememberAbcHaptics(): AbcHaptics {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) VibratorAbcHaptics(vibrator) else NoOpAbcHaptics
    }
}
