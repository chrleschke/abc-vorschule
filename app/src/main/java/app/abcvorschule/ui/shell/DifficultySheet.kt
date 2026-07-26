package app.abcvorschule.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.progress.ParentMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultySheet(
    current: ParentMode,
    onSelect: (ParentMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.difficulty_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            ModeRow(
                glyph = "~",
                label = stringResource(R.string.mode_auto),
                selected = current == ParentMode.Auto,
                onClick = { onSelect(ParentMode.Auto) },
            )
            ModeRow(
                glyph = "+",
                label = stringResource(R.string.mode_beginner),
                selected = current == ParentMode.Beginner,
                onClick = { onSelect(ParentMode.Beginner) },
            )
            ModeRow(
                glyph = "=",
                label = stringResource(R.string.mode_advanced),
                selected = current == ParentMode.Advanced,
                onClick = { onSelect(ParentMode.Advanced) },
            )
        }
    }
}

@Composable
private fun ModeRow(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (selected) "[$glyph] $label" else " $glyph  $label",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
