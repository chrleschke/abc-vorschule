package app.abcvorschule.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Primary trägt die Rundenansage; Feedback trägt Tap-Echos, die die Ansage nicht
 * abwürgen dürfen (Wort-Detektiv, siehe design doc). Getrennte ClipPlayer-Instanzen,
 * damit ein Aufruf auf dem einen Kanal den anderen nicht flusht — die TTS-Engine
 * bleibt geteilt, siehe design doc "Nicht im Scope". */
enum class SpeechChannel { Primary, Feedback }

class SpeechController(
    context: Context,
    private val clips: ClipIndex = ClipIndex.empty(),
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private val clipPlayers: Map<SpeechChannel, ClipPlayer> = mapOf(
        SpeechChannel.Primary to ClipPlayer(appContext),
        SpeechChannel.Feedback to ClipPlayer(appContext),
    )

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val utteranceWaiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /** While true, speak calls are ignored — set when the activity stops (background). */
    @Volatile
    private var blockedForBackground = false

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            _available.value = false
            return
        }
        val german = Locale.GERMANY
        val result = engine.setLanguage(german)
        val languageOk = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (languageOk) {
            engine.voices?.firstOrNull { voice ->
                voice.locale.language == german.language &&
                voice.locale.country == german.country &&
                !voice.isNetworkConnectionRequired
            }?.let { engine.voice = it }
        }
        _available.value = languageOk
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
                completeWaiter(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speaking.value = false
                completeWaiter(utteranceId)
            }
        })
    }

    /** Activity no longer visible — stop output and block new speech until foreground. */
    fun onBackground() {
        blockedForBackground = true
        stop()
    }

    /** Activity visible again — allow new speech (does not replay interrupted prompts). */
    fun onForeground() {
        blockedForBackground = false
    }

    fun speak(text: String, channel: SpeechChannel = SpeechChannel.Primary) {
        if (text.isBlank() || blockedForBackground) return
        clearWaiters()
        stopOutput(channel)
        if (playClip(text, channel, onComplete = {})) return
        val engine = tts ?: return
        if (!_available.value) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /** Speaks [text] and suspends until the utterance finishes (or times out). */
    suspend fun speakAndAwait(
        text: String,
        channel: SpeechChannel = SpeechChannel.Primary,
        timeoutMs: Long = 10_000L,
    ) {
        if (text.isBlank() || blockedForBackground) return
        clearWaiters()
        stopOutput(channel)
        val deferred = CompletableDeferred<Unit>()
        if (playClip(text, channel, onComplete = { deferred.complete(Unit) })) {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
            return
        }
        val engine = tts ?: return
        if (!_available.value) return
        val id = UUID.randomUUID().toString()
        utteranceWaiters[id] = deferred
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        withTimeoutOrNull(timeoutMs) { deferred.await() }
        utteranceWaiters.remove(id)
    }

    /**
     * Speaks each part in order — prompt clip then atom clip, etc. [onPartComplete]
     * fires with each part's ORIGINAL index in [texts] (blank parts are skipped but
     * don't shift later indices) right after that part finishes — callers use this to
     * unlock interaction before the whole sequence is done (design doc: Wort-Detektiv).
     */
    suspend fun speakAndAwaitSequence(
        texts: List<String>,
        timeoutMs: Long = 10_000L,
        onPartComplete: ((index: Int) -> Unit)? = null,
    ) {
        texts.withIndex().filter { it.value.isNotBlank() }.forEach { (index, text) ->
            speakAndAwait(text, timeoutMs = timeoutMs)
            onPartComplete?.invoke(index)
        }
    }

    fun stop() {
        SpeechChannel.entries.forEach { stopOutput(it) }
        clearWaiters()
    }

    fun shutdown() {
        SpeechChannel.entries.forEach { stopOutput(it) }
        tts?.shutdown()
        tts = null
        _available.value = false
        clearWaiters()
    }

    /** Clip gefunden und gestartet? `speaking` bildet nur den Primary-Kanal ab — die
     * Rundenansage, nicht ein gleichzeitig laufendes Feedback-Echo (design doc). */
    private fun playClip(text: String, channel: SpeechChannel, onComplete: () -> Unit): Boolean {
        val entry = clips.lookup(text) ?: return false
        val started = clipPlayers.getValue(channel).play(entry.file) {
            if (channel == SpeechChannel.Primary) _speaking.value = false
            onComplete()
        }
        if (started && channel == SpeechChannel.Primary) _speaking.value = true
        return started
    }

    /**
     * Beendet den Ausgabeweg des gegebenen Kanals. Die TTS-Engine bleibt geteilt und
     * wird nur gestoppt, wenn der Primary-Kanal gestoppt wird — ein Feedback-Stop darf
     * eine noch laufende Primary-Ansage nicht abwürgen (design doc).
     */
    private fun stopOutput(channel: SpeechChannel) {
        clipPlayers.getValue(channel).stop()
        if (channel == SpeechChannel.Primary) {
            tts?.stop()
            _speaking.value = false
        }
    }

    private fun completeWaiter(utteranceId: String?) {
        if (utteranceId == null) return
        utteranceWaiters.remove(utteranceId)?.complete(Unit)
    }

    private fun clearWaiters() {
        utteranceWaiters.keys.toList().forEach { key ->
            utteranceWaiters.remove(key)?.complete(Unit)
        }
    }
}
