package app.abcvorschule.ui.shell

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val ParentGateMs = 1500L

@Composable
fun ParentGateButton(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .size(48.dp)
            .testTag("parent_gate")
            .pointerInput(onUnlocked) {
                detectTapGestures(
                    onPress = {
                        try {
                            withTimeout(ParentGateMs) {
                                tryAwaitRelease()
                            }
                        } catch (_: TimeoutCancellationException) {
                            onUnlocked()
                            tryAwaitRelease()
                        }
                    },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "⋯",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
