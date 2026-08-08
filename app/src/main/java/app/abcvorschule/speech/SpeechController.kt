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

/**
 * Sprechen ist verfügbar, sobald irgendein Ausgabeweg existiert: kuratierte Clips
 * (assets/audio, decken fast alle Sprech-Texte ab und brauchen keine TTS-Engine)
 * oder eine deutsche TTS-Stimme. Ohne deutsche Stimme, aber mit Clip-Index, bleibt
 * die App also NICHT stumm — die visuellen No-Speech-Fallbacks greifen nur, wenn
 * wirklich nichts sprechen kann.
 */
internal fun speechAvailable(languageOk: Boolean, clipCount: Int): Boolean =
    languageOk || clipCount > 0

class SpeechController(
    context: Context,
    initialClips: ClipIndex = ClipIndex.empty(),
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private val clipPlayers: Map<SpeechChannel, ClipPlayer> = mapOf(
        SpeechChannel.Primary to ClipPlayer(appContext),
        SpeechChannel.Feedback to ClipPlayer(appContext),
    )

    /** Text→Clip-Index. Startet typischerweise leer und wird asynchron nachgereicht
     * ([updateClipIndex]), damit das ~110-KB-JSON nicht im ersten Frame auf dem
     * Main-Thread geparst wird. @Volatile: Schreiber ist der IO-Dispatcher,
     * Leser der Main-Thread. */
    @Volatile
    private var clips: ClipIndex = initialClips

    /** Deutsche TTS-Stimme einsatzbereit? Nur dieser Pfad darf `engine.speak`
     * erreichen — [available] ist bewusst weiter gefasst (Clips zählen mit) und
     * taugt deshalb nicht mehr als Gate für die Engine. */
    @Volatile
    private var languageOk = false

    private val _available = MutableStateFlow(speechAvailable(languageOk = false, clipCount = initialClips.size))
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val utteranceWaiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /** ID der aktuell laufenden Primary-TTS-Utterance. Nur sie darf `speaking`
     * schalten — Feedback-Utterances und stale Callbacks längst geflushter
     * Utterances würden sonst den Primary-Kanal-Zustand verfälschen. */
    @Volatile
    private var primaryUtteranceId: String? = null

    /** While true, speak calls are ignored — set when the activity stops (background). */
    @Volatile
    private var blockedForBackground = false

    /** Reicht den asynchron geladenen Clip-Index nach (MainActivity lädt ihn auf
     * Dispatchers.IO). Thread-sicher über @Volatile-Feld + StateFlow. Nach
     * [shutdown] wird Verfügbarkeit nicht wieder gemeldet. */
    fun updateClipIndex(index: ClipIndex) {
        clips = index
        if (tts != null) refreshAvailable()
    }

    private fun refreshAvailable() {
        _available.value = speechAvailable(languageOk, clips.size)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            languageOk = false
            refreshAvailable()
            return
        }
        val german = Locale.GERMANY
        val result = engine.setLanguage(german)
        languageOk = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (languageOk) {
            // `engine.voices` wirft auf manchen OEM-Engines (z. B. Samsung) real
            // Exceptions und darf den App-Start nicht crashen — dann bleibt
            // einfach die Default-Stimme der Engine aktiv.
            runCatching { engine.voices }.getOrNull()?.firstOrNull { voice ->
                voice.locale.language == german.language &&
                voice.locale.country == german.country &&
                !voice.isNetworkConnectionRequired
            }?.let { engine.voice = it }
        }
        refreshAvailable()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null && utteranceId == primaryUtteranceId) {
                    _speaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                finishUtterance(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishUtterance(utteranceId)
            }

            // Ohne onStop completed eine geflushte/gestoppte Utterance ihren
            // Waiter nie: `speakAndAwait` hinge bis zum Timeout und `speaking`
            // bliebe true. Callback existiert seit API 23, minSdk ist 26.
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finishUtterance(utteranceId)
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
        if (channel == SpeechChannel.Primary) clearWaiters()
        stopOutput(channel)
        if (playClip(text, channel, onComplete = {})) return
        enqueueTts(text, channel, UUID.randomUUID().toString())
    }

    /** Speaks [text] and suspends until the utterance finishes (or times out). */
    suspend fun speakAndAwait(
        text: String,
        channel: SpeechChannel = SpeechChannel.Primary,
        timeoutMs: Long = 10_000L,
    ) {
        if (text.isBlank() || blockedForBackground) return
        if (channel == SpeechChannel.Primary) clearWaiters()
        stopOutput(channel)
        val deferred = CompletableDeferred<Unit>()
        if (playClip(text, channel, onComplete = { deferred.complete(Unit) })) {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
            return
        }
        val id = UUID.randomUUID().toString()
        utteranceWaiters[id] = deferred
        if (!enqueueTts(text, channel, id)) {
            utteranceWaiters.remove(id)
            return
        }
        withTimeoutOrNull(timeoutMs) { deferred.await() }
        utteranceWaiters.remove(id)
    }

    /**
     * TTS-Engine-Fallback, wenn kein Clip existiert. Läuft nur mit deutscher
     * Stimme ([languageOk]) — [available] wäre hier das falsche Gate, weil es
     * seit dem Clip-Fallback auch ohne TTS-Stimme true sein kann; ohne diesen
     * Check spräche die Engine dann in der falschen Sprache.
     *
     * Primary flusht (neue Rundenansage ersetzt die alte), Feedback reiht mit
     * QUEUE_ADD ein: die TTS-Engine ist geteilt, ein FLUSH auf dem
     * Feedback-Kanal würde eine gerade laufende Primary-Ansage abwürgen —
     * genau das, was die getrennten Kanäle verhindern sollen (design doc).
     */
    private fun enqueueTts(text: String, channel: SpeechChannel, id: String): Boolean {
        val engine = tts ?: return false
        if (!languageOk) return false
        if (channel == SpeechChannel.Primary) {
            primaryUtteranceId = id
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        } else {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        }
        return true
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
        languageOk = false
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
            primaryUtteranceId = null
            tts?.stop()
            _speaking.value = false
        }
    }

    /** Utterance ist zu Ende (fertig, Fehler oder gestoppt): Waiter immer
     * completen; `speaking` nur zurücksetzen, wenn es die aktuelle
     * Primary-Utterance war (Feedback/stale IDs siehe [primaryUtteranceId]). */
    private fun finishUtterance(utteranceId: String?) {
        if (utteranceId != null && utteranceId == primaryUtteranceId) {
            primaryUtteranceId = null
            _speaking.value = false
        }
        completeWaiter(utteranceId)
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
