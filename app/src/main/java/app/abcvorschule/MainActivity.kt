package app.abcvorschule

import android.graphics.Color as AndroidColor
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.abcvorschule.speech.observeBackgroundSpeechStop
import androidx.lifecycle.viewmodel.compose.viewModel
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.speech.ClipIndex
import app.abcvorschule.speech.SpeechChannel
import app.abcvorschule.speech.SpeechController
import app.abcvorschule.ui.debug.TtsDebugScreen
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.rememberAbcHaptics
import app.abcvorschule.ui.shell.TaskShell
import app.abcvorschule.ui.theme.AbcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent {
            AbcTheme {
                CompositionLocalProvider(LocalAbcHaptics provides rememberAbcHaptics()) {
                    AbcApp(onFinish = { finish() })
                }
            }
        }
    }
}

@Composable
fun AbcApp(onFinish: () -> Unit = {}) {
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
    var showTtsDebug by remember { mutableStateOf(false) }

    BackHandler {
        if (viewModel.onBackPressed()) {
            onFinish()
        }
    }
    BackHandler(enabled = showTtsDebug) {
        showTtsDebug = false
    }

    val pack = viewModel.contentPack()
    if (BuildConfig.DEBUG && showTtsDebug && pack != null) {
        TtsDebugScreen(
            pack = pack,
            repository = app.ttsDebugRepository,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeak = speech::speak,
            onClose = { showTtsDebug = false },
        )
    } else {
        TaskShell(
            state = state,
            pack = pack,
            viewModel = viewModel,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeak = speech::speak,
            onSpeakFeedback = { text -> speech.speak(text, channel = SpeechChannel.Feedback) },
            onSpeakAndAwait = speech::speakAndAwait,
            onSpeakPromptSequence = speech::speakAndAwaitSequence,
            onSpeakIntroSequence = { texts, onPartComplete ->
                speech.speakAndAwaitSequence(texts, onPartComplete = onPartComplete)
            },
            onStopSpeak = speech::stop,
            onOpenTtsDebug = { showTtsDebug = true },
        )
    }
}
