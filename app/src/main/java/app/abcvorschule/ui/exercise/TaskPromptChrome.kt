package app.abcvorschule.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Speaker centered above the task title inside the exercise prompt area.
 */
@Composable
fun TaskPromptChrome(
    title: String?,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    mutedTitle: Boolean = false,
    onTitleSpeak: ((String) -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AbcSpeakerButton(
            enabled = ttsAvailable,
            speaking = speaking,
            onClick = onSpeakPrompt,
        )
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = if (mutedTitle) 28.sp else 34.sp,
                color = if (mutedTitle) WarmMuted else WarmInk,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clickable {
                    if (onTitleSpeak != null) onTitleSpeak(title) else onSpeakPrompt()
                },
            )
        }
    }
}
