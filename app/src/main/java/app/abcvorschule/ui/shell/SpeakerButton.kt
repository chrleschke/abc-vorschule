package app.abcvorschule.ui.shell

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.abcvorschule.R

@Composable
fun SpeakerButton(
    enabled: Boolean,
    speaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.speaker)
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text = if (speaking) "🔊" else "🔈",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
