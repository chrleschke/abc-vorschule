package app.abcvorschule

import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.abcvorschule.speech.observeBackgroundSpeechStop
import androidx.lifecycle.viewmodel.compose.viewModel
import app.abcvorschule.session.hasShowableContent
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.speech.ClipIndex
import app.abcvorschule.speech.SpeechChannel
import app.abcvorschule.speech.SpeechController
import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.rememberAbcHaptics
import app.abcvorschule.ui.shell.TaskShell
import app.abcvorschule.ui.theme.AbcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /**
     * Vom Compose-Baum gesetzt, sobald etwas Zeigbares steht — bis dahin bleibt
     * der Splash über der App. Ein einfaches `var` genügt: gelesen wird es im
     * Pre-Draw-Listener des Main-Threads, geschrieben aus der Composition, die
     * ebenfalls dort läuft.
     */
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Nicht „bis geladen", sondern „bis es etwas zu zeigen gibt": bei einem
        // Ladefehler bleibt `ready` dauerhaft false, und ein reiner ready-Test
        // ließe den Splash für immer stehen. hasShowableContent nimmt den
        // Fehlerzweig deshalb mit.
        splashScreen.setKeepOnScreenCondition { !contentReady }
        // Im Hellen ist dieser Crossfade unsichtbar — darunter liegt dieselbe
        // Farbe. Er ist für den Dark Mode da: dort blendet der Nachthimmel auf
        // den Papiergrund über, statt hart umzuschlagen.
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MILLIS)
                .withEndAction { splashProvider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        // Alles, was die App hörbar macht — Sprachclips, TTS und die synthetisierten
        // Blips — liegt auf der Medienspur. Ohne das regeln die Lautstärketasten die
        // Klingeltonspur, und der Erwachsene dreht am falschen Regler.
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge(
            // Status bar: API 26 has windowLightStatusBar, so a fully transparent
            // darkScrim fallback never actually gets used — safe to leave transparent.
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            // Nav bar: the second argument is the darkScrim shown on API 26 devices,
            // which lack windowLightNavigationBar and so can't tint the (white) nav
            // icons dark themselves. A transparent scrim there left white icons
            // invisible on the Cream background; a translucent dark scrim keeps them
            // legible without visibly darkening the bar on the newer devices that
            // don't need the fallback.
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, 0x66000000),
        )
        hideSystemBars()
        setContent {
            AbcTheme {
                CompositionLocalProvider(LocalAbcHaptics provides rememberAbcHaptics()) {
                    AbcApp(
                        onFinish = { finish() },
                        onContentReady = { contentReady = true },
                    )
                }
            }
        }
    }

    // Zurück in den Vollbildmodus, sobald das Fenster den Fokus zurückbekommt —
    // etwa nachdem die System-Zahlentastatur zu war oder das Eltern-Sheet den
    // Fokus hatte. Ohne das blieben die Leisten nach solchen Zwischenspielen
    // stehen, und mit ihnen die Rutschbahn für eine versehentliche Zurück-Geste.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Kinder-App im Vollbild: Status- und Navigationsleiste sind versteckt, ein
     * Randwisch holt sie nur kurz zurück (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
     * statt sofort Zurück oder Home auszulösen.
     *
     * Das ist außerdem die Voraussetzung für den Gesten-Ausschluss über die ganze
     * Übungsfläche: Android deckelt `systemGestureExclusion` sonst bei 200 dp je
     * Bildschirmkante — der Deckel entfällt nur, solange die Navigationsleiste
     * dauerhaft versteckt ist.
     */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Dauer des Übergangs vom Splash in die App. Lang genug, dass der Sprung vom
 * Nachthimmel auf den Papiergrund im Dark Mode als Blende gelesen wird und nicht
 * als Umschalten; kurz genug, dass niemand darauf wartet.
 */
private const val SPLASH_FADE_MILLIS = 300L

@Composable
fun AbcApp(onFinish: () -> Unit = {}, onContentReady: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as AbcApplication
    val speech = remember { SpeechController(context) }
    // Clip-Index (~110 KB JSON) abseits des Main-Threads laden: synchron im ersten
    // Frame geparst hat er den App-Start blockiert. SpeechController startet leer
    // und zieht Verfügbarkeit reaktiv nach, sobald der Index da ist.
    LaunchedEffect(speech) {
        val index = withContext(Dispatchers.IO) {
            ClipIndex.load { path -> context.assets.open(path) }
        }
        speech.updateClipIndex(index)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, speech) {
        val observer = lifecycleOwner.observeBackgroundSpeechStop(speech)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speech.shutdown()
        }
    }

    val viewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(app.contentRepository, app.progressRepository),
    )
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val ttsAvailable by speech.available.collectAsStateWithLifecycle()
    val speaking by speech.speaking.collectAsStateWithLifecycle()

    BackHandler {
        if (viewModel.onBackPressed()) {
            onFinish()
        }
    }
    val pack = viewModel.contentPack()
    // Freigabe für den Splash, sobald TaskShell etwas anderes als den leeren
    // Grund zeichnen würde. Die Bedingung spiegelt dessen Verzweigung — sie
    // liegt in SessionModels.kt, damit sie ohne Gerät prüfbar ist.
    val showable = state.hasShowableContent(packLoaded = pack != null)
    LaunchedEffect(showable) {
        if (showable) onContentReady()
    }
    TaskShell(
        state = state,
        pack = pack,
        viewModel = viewModel,
        ttsAvailable = ttsAvailable,
        speaking = speaking,
        onSpeak = speech::speak,
        onSpeakFeedback = { text -> speech.speak(text, channel = SpeechChannel.Feedback) },
        onSpeakCounting = { text -> speech.speak(text, channel = SpeechChannel.Counting) },
        onSpeakAndAwait = speech::speakAndAwait,
        onSpeakPromptSequence = { texts -> speech.speakAndAwaitSequence(texts) },
        onSpeakIntroSequence = { texts, onPartComplete ->
            speech.speakAndAwaitSequence(texts, onPartComplete = onPartComplete)
        },
        onSpeakParts = { parts -> speech.speakAndAwaitSequence(parts) },
        onSpeakPartsSequenced = { parts, onPartComplete ->
            speech.speakAndAwaitSequence(parts, onPartComplete = onPartComplete)
        },
        onSpeakFeedbackVoiced = { part -> speech.speak(part.text, channel = SpeechChannel.Feedback, voice = part.voice) },
        onStopSpeak = speech::stop,
    )
}
