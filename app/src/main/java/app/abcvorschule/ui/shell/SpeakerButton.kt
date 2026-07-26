package app.abcvorschule.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.abcvorschule.ui.components.AbcSpeakerButton

@Composable
fun SpeakerButton(
    enabled: Boolean,
    speaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AbcSpeakerButton(
        enabled = enabled,
        speaking = speaking,
        onClick = onClick,
        modifier = modifier,
    )
}
