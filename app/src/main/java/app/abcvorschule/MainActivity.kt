package app.abcvorschule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.speech.SpeechController
import app.abcvorschule.ui.shell.TaskShell
import app.abcvorschule.ui.theme.AbcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbcTheme {
                AbcApp(onFinish = { finish() })
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

    BackHandler {
        if (viewModel.onBackPressed()) {
            onFinish()
        }
    }

    TaskShell(
        state = state,
        pack = viewModel.contentPack(),
        viewModel = viewModel,
        ttsAvailable = ttsAvailable,
        speaking = speaking,
        onSpeak = speech::speak,
        onSpeakAndAwait = speech::speakAndAwait,
        onStopSpeak = speech::stop,
    )
}
