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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.speech.SpeechController
import app.abcvorschule.ui.debug.TtsDebugScreen
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.rememberAbcHaptics
import app.abcvorschule.ui.shell.TaskShell
import app.abcvorschule.ui.theme.AbcTheme

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
    DisposableEffect(speech) {
        onDispose { speech.shutdown() }
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
            onSpeakAndAwait = speech::speakAndAwait,
            onStopSpeak = speech::stop,
            onOpenTtsDebug = { showTtsDebug = true },
        )
    }
}
