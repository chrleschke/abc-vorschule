package app.abcvorschule.speech

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Stops speech when the hosting activity is no longer visible (home, task switch,
 * another app on top). Resumes the output gate on [Lifecycle.Event.ON_START] so
 * foreground prompts work normally again.
 */
fun LifecycleOwner.observeBackgroundSpeechStop(speech: SpeechController): LifecycleEventObserver {
    val observer = LifecycleEventObserver { _, event ->
        handleBackgroundSpeechLifecycleEvent(
            event = event,
            onBackground = speech::onBackground,
            onForeground = speech::onForeground,
        )
    }
    lifecycle.addObserver(observer)
    return observer
}

internal fun handleBackgroundSpeechLifecycleEvent(
    event: Lifecycle.Event,
    onBackground: () -> Unit,
    onForeground: () -> Unit,
) {
    when (event) {
        Lifecycle.Event.ON_STOP -> onBackground()
        Lifecycle.Event.ON_START -> onForeground()
        else -> Unit
    }
}
